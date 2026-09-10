package com.kurly.payment.presentation.dto;

import com.kurly.payment.domain.entity.PaymentCancel;

import java.time.Instant;

/**
 * 결제 취소 결과.
 *
 * <p>{@code paymentId}는 숫자로 내보낸다. 명세 예시에는 문자열(`"12345"`)로 적혀 있으나 영수증
 * 조회 응답은 숫자라, 같은 값이 엔드포인트마다 다른 타입이면 클라이언트가 분기해야 한다.
 */
public record CancelResponse(
        Long paymentId,
        String status,
        Instant canceledAt
) {

    public static CancelResponse from(PaymentCancel cancel) {
        return new CancelResponse(
                cancel.getPayment().getId(),
                cancel.getPayment().getStatus().name(),
                Timestamps.toInstant(cancel.getPayment().getCanceledAt()));
    }
}
