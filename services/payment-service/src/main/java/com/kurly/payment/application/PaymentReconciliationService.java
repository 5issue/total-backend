package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PG 대사 — 우리 기록과 PG의 진실이 어긋난 결제를 찾아 맞춘다.
 *
 * <p>승인 요청이 타임아웃되면 우리는 결과를 모른다. 지금 코드는 그 경우를 실패로 기록하지만
 * <b>PG는 승인했을 수 있다.</b> 그러면 고객 돈은 빠져나갔는데 주문은 진행되지 않은 채로 남고,
 * 애플리케이션 기록만 보면 영원히 알 수 없다. 확인할 방법은 PG에 직접 묻는 것뿐이다.
 *
 * <p>대사 대상은 세 가지다.
 * <ul>
 *   <li>{@code REQUESTED} — 승인 결과를 기록하지 못하고 멈춘 건(프로세스 종료 등)</li>
 *   <li>{@code FAILED} — 실패로 기록했지만 그 판단이 타임아웃에서 나왔을 수 있는 건</li>
 *   <li><b>주문에 인계되지 못한 {@code SUCCESS}</b> — 승인 기록과 주문 통보는 다른 트랜잭션이라,
 *       그 사이에 죽으면 "결제는 성공했는데 주문은 모르는" 상태가 남는다</li>
 * </ul>
 *
 * <p>대사 시점에는 주문의 결제 유효시간(5분)이 이미 지났을 가능성이 높다. 주문이 받아주지 않으면
 * 결제를 살려둘 이유가 없으므로 <b>환불</b>한다. 돈을 묶어두는 쪽보다 돌려주는 쪽이 안전하다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> PG·주문 서비스 호출이 섞여 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    /**
     * 선점 임대 시간.
     *
     * <p>선점을 <b>완료 표시와 겸하지 않고</b> 만료되는 임대로 거는 것이 중요하다. 겸하게 두면
     * 선점 직후 프로세스가 죽었을 때 해제 코드가 실행되지 않아 그 결제가 영원히 대사 대상에서
     * 빠진다. 임대는 시간이 지나면 저절로 풀려 다른 워커가 회수한다.
     *
     * <p>외부 호출 타임아웃보다 넉넉하되, 죽은 워커의 몫을 너무 늦게 회수하지 않을 만큼 짧게 둔다.
     */
    static final Duration LEASE = Duration.ofMinutes(5);

    private final PgClient pgClient;
    private final OrderClient orderClient;
    private final PaymentRecordService paymentRecordService;
    private final PaymentCompensationService paymentCompensationService;

    /**
     * 대사가 필요한 결제를 훑는다.
     *
     * @param staleAfter 이 시간이 지나도록 결론이 안 난 건만 본다. 진행 중인 정상 결제를 건드리면
     *                   승인과 대사가 같은 결제를 두고 경합한다
     */
    public void reconcileStalePayments(int limit, Duration staleAfter) {
        List<PaymentRecordService.ReconcileTarget> targets =
                paymentRecordService.claimReconcilable(limit, LocalDateTime.now().minus(staleAfter), LEASE);
        if (targets.isEmpty()) {
            return;
        }

        log.info("PG 대사 시작: {}건", targets.size());
        for (PaymentRecordService.ReconcileTarget target : targets) {
            try {
                reconcile(target);
            } catch (RuntimeException e) {
                // 결론을 내지 못했다. 선점을 풀어 다음 주기가 다시 보게 한다.
                log.error("결제 대사 실패: paymentId={}, orderId={}",
                        target.paymentId(), target.orderId(), e);
                paymentRecordService.releaseReconciliationClaim(target.paymentId());
            }
        }
    }

    private void reconcile(PaymentRecordService.ReconcileTarget target) {
        if (target.approvedButNotHandedOver()) {
            // 승인은 이미 기록돼 있다. PG에 다시 물을 것 없이 주문 인계만 마치면 된다.
            log.warn("승인됐으나 주문에 인계되지 않은 결제 발견: paymentId={}, orderId={}",
                    target.paymentId(), target.orderId());
            handOverOrRefund(target, target.paymentKey());
            paymentRecordService.completeReconciliation(target.paymentId());
            return;
        }

        PgClient.Inquiry inquiry = pgClient.findByOrderId(target.orderId()).orElse(null);

        if (inquiry != null && inquiry.pending()) {
            // PG에서 아직 진행 중이다. 여기서 실패로 못박으면 결제될 수 있었던 건을 죽인다.
            // 선점만 풀고 완료로 표시하지 않아 다음 주기가 다시 본다.
            log.info("PG에서 아직 진행 중. 대사를 미룬다: paymentId={}, pg상태={}",
                    target.paymentId(), inquiry.status());
            paymentRecordService.releaseReconciliationClaim(target.paymentId());
            return;
        }

        if (inquiry == null || !inquiry.approved()) {
            // PG도 승인한 적이 없다. 돈이 나가지 않았음이 확인됐으므로 실패로 확정한다.
            if (!target.alreadyFailed()) {
                log.info("대사 결과 미승인으로 확정: paymentId={}, pg상태={}",
                        target.paymentId(), inquiry == null ? "없음" : inquiry.status());
                paymentRecordService.recordFailure(target.paymentId());
            }
            paymentRecordService.completeReconciliation(target.paymentId());
            return;
        }

        // 여기부터가 대사의 존재 이유다. PG는 승인했는데 우리 기록은 아니었다.
        log.warn("기록과 PG 불일치 발견. 승인으로 정정한다: paymentId={}, orderId={}, 기록={}",
                target.paymentId(), target.orderId(), target.status());
        paymentRecordService.recordApproval(target.paymentId(), new PgClient.Approval(
                inquiry.paymentKey(), inquiry.method(), inquiry.receiptUrl()));

        handOverOrRefund(target, inquiry.paymentKey());
        paymentRecordService.completeReconciliation(target.paymentId());
    }

    /**
     * 정정한 결제를 주문에 넘기거나, 넘길 수 없으면 환불한다.
     *
     * <p>주문 통보가 어떤 이유로든 실패하면 환불로 간다. 대사 시점이면 주문의 결제 유효시간은
     * 이미 지났을 가능성이 높고, 넘기지도 못한 돈을 계속 들고 있는 것이 더 나쁘다.
     */
    private void handOverOrRefund(PaymentRecordService.ReconcileTarget target, String paymentKey) {
        try {
            orderClient.completePayment(
                    target.orderId(), target.paymentId(), target.totalAmount(), LocalDateTime.now());
            // 남기지 않으면 다음 주기가 이 결제를 미인계로 보고 계속 집는다.
            paymentRecordService.markOrderNotified(target.paymentId());
            log.info("대사로 정정한 결제를 주문에 넘겼다: paymentId={}", target.paymentId());
            return;
        } catch (RuntimeException e) {
            log.warn("주문 인계 실패: paymentId={}, orderId={}", target.paymentId(), target.orderId(), e);
        }

        // 통보 실패를 곧바로 만료로 읽으면 안 된다. 재통보는 이미 결제로 확정된 주문에도 갈 수 있고,
        // 그때 오는 409를 만료로 오인하면 <b>멀쩡한 결제를 환불</b>하게 된다. 주문에 직접 되묻는다.
        if (isAlreadyPaid(target.orderId())) {
            log.info("주문이 이미 결제로 확정돼 있다. 인계 완료로 본다: paymentId={}", target.paymentId());
            paymentRecordService.markOrderNotified(target.paymentId());
            return;
        }

        paymentCompensationService.compensate(
                target.paymentId(), paymentKey, target.totalAmount(), "RECONCILE_ORDER_UNAVAILABLE");
    }

    /**
     * 주문이 결제로 확정됐는지 되묻는다.
     *
     * <p>조회 자체가 실패하면 <b>판단하지 않고 예외를 올린다.</b> 호출부가 선점을 풀어 다음 주기가
     * 다시 보게 된다. 모르는 채로 환불하면 멀쩡한 결제를 되돌릴 수 있다.
     */
    private boolean isAlreadyPaid(Long orderId) {
        try {
            return orderClient.fetch(orderId).alreadyPaid();
        } catch (OrderNotFoundException e) {
            // 없거나 만료된 주문이다(주문 명세상 404가 둘을 구분하지 않는다). 살려둘 이유가 없다.
            return false;
        }
    }

    /**
     * 결과를 모른 채 남은 취소를 회수한다.
     *
     * <p>{@code beginCancel}은 {@code REQUESTED}를 커밋한 뒤 PG를 부른다. 그 사이에 프로세스가 죽으면
     * 실패 기록({@code failCancel})이 실행되지 않아 <b>재시도 큐에 들어가지도 못한다.</b> 재시도 워커는
     * 큐만 보므로 그 취소는 아무도 이어받지 못하고, 고객 돈이 환불되지 않은 채 남는다.
     *
     * <p>여기서 하는 일은 그 취소를 실패로 확정해 재시도 큐에 넣는 것뿐이다. 실제 재시도는 기존
     * 워커가 맡는다. <b>PG에 이미 취소가 반영됐더라도 안전하다</b> — 재시도는 같은 취소 이력 id로
     * 만든 멱등키를 보내므로 PG가 같은 요청으로 알아본다.
     *
     * @param staleAfter PG 호출이 끝나기를 기다려 주는 유예. 짧으면 정상 진행 중인 취소를 건드린다
     */
    public void recoverStaleCancels(int limit, Duration staleAfter) {
        List<Long> recovered = paymentRecordService.recoverStaleRequestedCancels(
                limit, LocalDateTime.now().minus(staleAfter));
        if (!recovered.isEmpty()) {
            log.warn("결과를 모른 채 남은 취소를 재시도 큐로 회수: {}건, cancelIds={}",
                    recovered.size(), recovered);
        }
    }
}
