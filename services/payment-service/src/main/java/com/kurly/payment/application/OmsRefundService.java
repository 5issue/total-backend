package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.PaymentNotFoundException;
import com.kurly.payment.infrastructure.messaging.OmsRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OMS 반품 환불 요청 처리(payment 추가 통신 명세).
 *
 * <p>흐름은 <b>PG 취소 → 기록 → 완료 통보 적재</b>다. 통보는 아웃박스에 넣고 워커가 발행한다.
 *
 * <p><b>{@code PaymentCancelService}와 나누어 둔다.</b> 그쪽은 결제 전액을 취소하는 경로이고
 * 소유자 대조를 전제로 한다. 반품 환불은 일부 상품만 돌아오는 부분 환불이며, 행위자가 관리자라
 * 사용자 토큰이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmsRefundService {

    /** 취소 사유에 이벤트 식별자를 담아 중복 배달을 걸러낸다. */
    static final String REASON_PREFIX = "OMS_RETURN_REFUND:";

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentRecordService paymentRecordService;
    private final PgClient pgClient;

    /**
     * 환불을 수행하고 완료 통보를 적재한다.
     *
     * <p>이미 처리한 이벤트면 아무 일도 하지 않는다. 브로커가 최소 1회 배달을 보장하므로
     * 같은 요청이 두 번 오는 것은 정상이며, 그때 두 번 환불하면 고객에게 과다 지급된다.
     *
     * @return 환불을 수행했으면 {@code true}, 중복 배달로 건너뛰었으면 {@code false}
     */
    public boolean refund(OmsRefundRequestedEvent event) {
        String reason = REASON_PREFIX + event.eventId();
        if (paymentCancelRepository.existsByCancelReason(reason)) {
            log.info("이미 처리한 환불 요청. 중복 배달로 보고 넘어간다: eventId={}", event.eventId());
            return false;
        }

        Payment payment = paymentRepository
                .findByOrderIdAndStatus(event.orderId(), PaymentStatus.SUCCESS)
                .orElseThrow(PaymentNotFoundException::new);

        long refundAmount = event.refundAmount();
        long alreadyRefunded = paymentCancelRepository.sumSucceededAmountByPaymentId(payment.getId());
        if (alreadyRefunded + refundAmount > payment.getTotalAmount()) {
            // 결제 금액보다 많이 환불하려는 요청이다. 발행자 버그이므로 재시도해도 같다.
            throw new RefundAmountExceededException(
                    "환불 요청이 결제 금액을 넘는다: orderId=" + event.orderId()
                            + ", 결제=" + payment.getTotalAmount()
                            + ", 기환불=" + alreadyRefunded + ", 요청=" + refundAmount);
        }

        PaymentCancel cancel = paymentRecordService.beginCancel(payment.getId(), reason, refundAmount);
        try {
            PgClient.Cancellation cancellation = pgClient.cancel(
                    payment.getPaymentKey(), refundAmount, reason, cancel.getId());
            paymentRecordService.completeRefund(cancel.getId(), cancellation.pgCancelKey(), event.omsReturnId());
            log.info("반품 환불 완료: orderId={}, omsReturnId={}, amount={}",
                    event.orderId(), event.omsReturnId(), refundAmount);
            return true;
        } catch (RuntimeException e) {
            // 환불 실패를 그대로 던지면 고객 돈이 묶인 채 잊힌다. 이력에 남기고 배치가 이어받는다.
            log.error("PG 환불 실패. 재시도 큐로 넘긴다: orderId={}, cancelId={}",
                    event.orderId(), cancel.getId(), e);
            paymentRecordService.failCancel(cancel.getId(), e.getMessage());
            throw e;
        }
    }

    /** 결제 금액을 넘는 환불 요청. 재시도로 해결되지 않으므로 호출부가 DLQ로 보낸다. */
    public static class RefundAmountExceededException extends RuntimeException {
        public RefundAmountExceededException(String message) {
            super(message);
        }
    }
}
