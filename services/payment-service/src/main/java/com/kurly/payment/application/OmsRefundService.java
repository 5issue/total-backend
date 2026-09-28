package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.infrastructure.messaging.OmsRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * OMS 반품 환불 요청 처리(payment 추가 통신 명세).
 *
 * <p>흐름은 <b>개시(트랜잭션) → PG 취소(트랜잭션 밖) → 완료 기록(트랜잭션)</b> 셋으로 나뉜다.
 * PG 호출을 트랜잭션 안에서 하면 외부 응답을 기다리는 동안 결제 행 잠금을 붙잡는다.
 *
 * <p><b>{@code PaymentCancelService}와 나누어 둔다.</b> 그쪽은 결제 전액을 취소하는 경로이고
 * 소유자 대조를 전제로 한다. 반품 환불은 일부 상품만 돌아오는 부분 환불이며, 행위자가 관리자라
 * 사용자 토큰이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmsRefundService {

    /** 취소 사유에 남기는 표시. 사람이 이력을 볼 때 경로를 알 수 있게 한다. */
    static final String REASON_PREFIX = "OMS_RETURN_REFUND:";

    private final PaymentRecordService paymentRecordService;
    private final PgClient pgClient;

    /**
     * 환불을 수행하고 완료 통보를 적재한다.
     *
     * @return 환불을 수행했으면 {@code true}, 중복 배달로 건너뛰었으면 {@code false}
     */
    public boolean refund(OmsRefundRequestedEvent event) {
        String dedupKey = event.eventId().toString();
        PaymentRecordService.RefundTicket ticket;
        try {
            ticket = paymentRecordService.beginRefund(
                    event.orderId(), REASON_PREFIX + event.eventId(),
                    event.refundAmount(), dedupKey, event.omsReturnId());
        } catch (DataIntegrityViolationException e) {
            // dedup_key 유니크 제약 위반. 조회로는 막을 수 없는 동시 재배달이 여기서 걸린다.
            // 최소 1회 배달이라 같은 요청이 두 번 오는 것은 정상이며, 두 번 환불하면 과다 지급이다.
            log.info("이미 처리 중이거나 처리된 환불 요청. 중복 배달로 보고 넘어간다: eventId={}",
                    event.eventId());
            return false;
        }

        PgClient.Cancellation cancellation;
        try {
            cancellation = pgClient.cancel(
                    ticket.paymentKey(), ticket.amount(), REASON_PREFIX + event.eventId(), ticket.cancelId());
        } catch (RuntimeException e) {
            // PG 호출이 실패했다. 이력에 남기고 재시도 배치가 이어받는다.
            // 여기서 조용히 끝내면 고객 돈이 묶인 채 잊힌다.
            log.error("PG 환불 실패. 재시도 큐로 넘긴다: orderId={}, cancelId={}",
                    event.orderId(), ticket.cancelId(), e);
            paymentRecordService.failCancel(ticket.cancelId(), e.getMessage());
            throw e;
        }

        try {
            paymentRecordService.completeRefund(ticket.cancelId(), cancellation.pgCancelKey());
        } catch (RuntimeException e) {
            // PG는 이미 환불했고 기록만 실패했다. 여기서 failCancel을 부르면 안 된다 —
            // 실패로 확정해 재시도를 걸면 PG를 다시 부르게 되고, 환불되지 않은 것처럼 이력이 남는다.
            // 취소는 REQUESTED로 남겨 회수 배치가 집어가게 한다. 그 경로는 같은 멱등키로
            // PG를 다시 불러 원래 결과를 돌려받고, 부분 환불로 완료 처리한다.
            log.error("환불은 됐으나 기록에 실패했다. REQUESTED로 남겨 회수에 맡긴다: "
                            + "orderId={}, cancelId={}, pgCancelKey={}",
                    event.orderId(), ticket.cancelId(), cancellation.pgCancelKey(), e);
            throw e;
        }

        log.info("반품 환불 완료: orderId={}, omsReturnId={}, amount={}",
                event.orderId(), event.omsReturnId(), ticket.amount());
        return true;
    }
}
