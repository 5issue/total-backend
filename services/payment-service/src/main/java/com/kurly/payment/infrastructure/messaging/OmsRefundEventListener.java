package com.kurly.payment.infrastructure.messaging;

import com.kurly.payment.application.OmsRefundService;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * OMS 반품 환불 요청 소비자(payment 추가 통신 명세).
 *
 * <p><b>행위자가 관리자라 사용자 토큰이 없다.</b> 메시지가 실어 보낸 식별자로 결제를 찾는데,
 * 서명이 없으므로 이는 인증이 아니라 <b>정합성 검증</b>이다. 신뢰 경계는 브로커다.
 *
 * <p>재시도해도 결과가 같은 오류는 즉시 DLQ로 보낸다. 환불은 돈이 걸린 흐름이라 큐가 막히면
 * 다른 고객의 환불까지 지연된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OmsRefundEventListener {

    private final OmsRefundService omsRefundService;

    @RabbitListener(queues = "${payment.oms-refund.queue}")
    public void onRefundRequested(OmsRefundRequestedEvent event) {
        validate(event);

        try {
            omsRefundService.refund(event);
        } catch (PaymentNotFoundException e) {
            // 주문에 성공한 결제가 없다. 정상 상황에서는 일어나지 않는 발행자 버그다.
            log.error("환불 대상 결제를 찾지 못함. DLQ로 보낸다: orderId={}", event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException("환불 대상 결제 없음: orderId=" + event.orderId(), e);
        } catch (OmsRefundService.RefundAmountExceededException e) {
            log.error("환불 금액이 결제 금액을 넘는다. DLQ로 보낸다: eventId={}", event.eventId(), e);
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (InvalidPaymentStatusException e) {
            // 이미 취소된 결제다. 중복 배달이거나 다른 경로에서 먼저 취소된 경우다.
            log.info("취소할 수 없는 상태의 결제. 넘어간다: orderId={}", event.orderId());
        }
        // 그 밖의 예외(PG 오류 등)는 그대로 올려보낸다. 브로커가 재배달해 다시 시도하게 한다.
    }

    /**
     * 다시 시도해도 같은 결과인 형식 오류를 걸러낸다.
     *
     * <p><b>{@code omsReturnId}가 필수다.</b> OMS가 완료 통보를 받아 반품 건을 찾는 조회 키라,
     * 없으면 환불을 해도 OMS의 반품 상태가 영원히 {@code REFUND_PENDING}에 남는다.
     * 돈은 나가고 상태는 어긋나는 쪽이 더 나쁘므로, 환불을 시작하기 전에 막는다.
     */
    private void validate(OmsRefundRequestedEvent event) {
        if (event == null) {
            throw new AmqpRejectAndDontRequeueException("환불 요청 이벤트가 비어 있습니다.");
        }
        reject(event.eventId() == null, "eventId가 없습니다", event);
        reject(event.orderId() == null, "orderId가 없습니다", event);
        reject(event.omsReturnId() == null, "omsReturnId가 없습니다(OMS 발행 필드 확인 필요)", event);
        reject(event.refundAmount() == null || event.refundAmount() <= 0,
                "refundAmount가 유효하지 않습니다", event);
    }

    private void reject(boolean invalid, String reason, OmsRefundRequestedEvent event) {
        if (invalid) {
            log.error("환불 요청 이벤트 형식 오류. DLQ로 보낸다: {} / event={}", reason, event);
            throw new AmqpRejectAndDontRequeueException("환불 요청 이벤트 형식 오류: " + reason);
        }
    }
}
