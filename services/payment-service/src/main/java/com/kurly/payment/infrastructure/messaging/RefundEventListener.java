package com.kurly.payment.infrastructure.messaging;

import com.kurly.payment.application.PaymentCancelService;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 반품 승인 환불 소비자(주문-결제 시퀀스 3절).
 *
 * <p>OMS가 반품을 승인하면 이벤트가 발행되고, 이 소비자가 결제를 취소한다.
 * <b>행위자가 관리자라 사용자 토큰이 없다.</b> 메시지가 실어 보낸 식별자로 소유자를 대조하는데,
 * 서명이 없으므로 이는 인증이 아니라 <b>정합성 검증</b>이다. 신뢰 경계는 브로커다.
 *
 * <p>불일치는 사용자 오류가 아니라 발행자 버그다. 동기 경로처럼 404로 조용히 넘기지 않고
 * DLQ로 보내 사람이 보게 한다.
 *
 * <p>본문은 {@code Map}으로 받는다. common이 등록한 JSON {@code MessageConverter}가 변환을 맡으며,
 * 해석할 수 없는 메시지는 이 메서드에 닿기 전에 컨테이너가 거부해 DLQ로 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private static final String DEFAULT_REASON = "RETURN_APPROVED";

    private final PaymentCancelService paymentCancelService;

    @RabbitListener(queues = "${payment.refund.queue}")
    public void onRefundApproved(Map<String, Object> event) {
        Long paymentId = requirePaymentId(event);
        Long userId = optionalLong(event.get("userId"));
        Object reason = event.get("reason");

        try {
            paymentCancelService.cancel(paymentId, userId,
                    reason == null ? DEFAULT_REASON : String.valueOf(reason));
            log.info("반품 환불 처리 완료: paymentId={}", paymentId);
        } catch (PaymentNotFoundException e) {
            // 결제가 없거나 메시지가 주장한 소유자와 다르다. 정상 상황에서는 일어나지 않는다.
            log.error("반품 환불 대상이 맞지 않음. DLQ로 보낸다: paymentId={}, userId={}", paymentId, userId, e);
            throw new AmqpRejectAndDontRequeueException("환불 대상 불일치: paymentId=" + paymentId, e);
        } catch (InvalidPaymentStatusException e) {
            // 이미 취소된 결제다. 발행이 최소 1회를 보장하므로 같은 이벤트가 두 번 오는 것은 정상이다.
            log.info("이미 취소된 결제. 중복 배달로 보고 넘어간다: paymentId={}", paymentId);
        }
        // 그 밖의 예외는 그대로 올려보낸다. 브로커가 재배달해 다시 시도하게 한다.
    }

    private Long requirePaymentId(Map<String, Object> event) {
        Long paymentId = optionalLong(event.get("paymentId"));
        if (paymentId == null) {
            // 다시 시도해도 같은 결과다. 큐를 막지 않도록 즉시 DLQ로 보낸다.
            log.error("환불 이벤트에 paymentId가 없음. DLQ로 보낸다: event={}", event);
            throw new AmqpRejectAndDontRequeueException("환불 이벤트에 paymentId가 없습니다.");
        }
        return paymentId;
    }

    private Long optionalLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }
}
