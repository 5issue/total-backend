package com.kurly.payment.infrastructure.messaging;

import java.util.UUID;

/**
 * 환불 완료 통보(payment 추가 통신 명세). OMS가 소비해 반품 건을 완료 처리한다.
 *
 * <p>필드 구성은 OMS의 {@code PaymentRefundCompletedMessage}를 그대로 따른다.
 *
 * @param omsReturnId  OMS가 반품 건을 찾는 <b>조회 키</b>다. 받은 값을 그대로 되돌려준다
 * @param refundAmount 검증용. OMS가 자기 기록({@code totalRefundAmount})과 대조해 다르면 거부한다
 * @param refundAt     환불 시각(epoch milli). OMS 정의가 {@code Long}이라 그 형식을 따른다
 */
public record PaymentRefundCompletedMessage(
        UUID eventId,
        Long omsReturnId,
        Long refundAmount,
        Long refundAt
) {
}
