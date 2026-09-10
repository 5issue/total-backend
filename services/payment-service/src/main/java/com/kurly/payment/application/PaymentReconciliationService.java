package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
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
 * <p>대사 대상은 두 가지다.
 * <ul>
 *   <li>{@code REQUESTED} — 승인 결과를 기록하지 못하고 멈춘 건(프로세스 종료 등)</li>
 *   <li>{@code FAILED} — 실패로 기록했지만 그 판단이 타임아웃에서 나왔을 수 있는 건</li>
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
                paymentRecordService.claimReconcilable(limit, LocalDateTime.now().minus(staleAfter));
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
        PgClient.Inquiry inquiry = pgClient.findByOrderId(target.orderId()).orElse(null);

        if (inquiry != null && inquiry.pending()) {
            // PG에서 아직 진행 중이다. 여기서 실패로 못박으면 결제될 수 있었던 건을 죽인다.
            // 선점을 풀어 다음 주기가 다시 보게 한다.
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
            return;
        }

        // 여기부터가 대사의 존재 이유다. PG는 승인했는데 우리 기록은 아니었다.
        log.warn("기록과 PG 불일치 발견. 승인으로 정정한다: paymentId={}, orderId={}, 기록={}",
                target.paymentId(), target.orderId(), target.status());
        paymentRecordService.recordApproval(target.paymentId(), new PgClient.Approval(
                inquiry.paymentKey(), inquiry.method(), inquiry.receiptUrl()));

        handOverOrRefund(target, inquiry.paymentKey());
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
            log.info("대사로 정정한 결제를 주문에 넘겼다: paymentId={}", target.paymentId());
        } catch (RuntimeException e) {
            log.warn("주문 인계 실패. 환불한다: paymentId={}, orderId={}",
                    target.paymentId(), target.orderId(), e);
            paymentCompensationService.compensate(
                    target.paymentId(), paymentKey, target.totalAmount(), "RECONCILE_ORDER_UNAVAILABLE");
        }
    }
}
