package com.kurly.payment.presentation.dto;

import com.kurly.payment.domain.entity.Payment;

import java.time.Instant;

/**
 * 결제 승인 결과.
 *
 * <p>{@code paymentCompletedAt}은 UTC ISO-8601로 내보낸다. 엔티티는 {@code LocalDateTime}으로
 * 저장하므로 시스템 시간대 기준으로 {@code Instant}로 되돌린다.
 */
public record CheckoutResponse(
        Instant paymentCompletedAt,
        String receiptUrl,
        String paymentStatus
) {

    public static CheckoutResponse from(Payment payment) {
        return new CheckoutResponse(
                Timestamps.toInstant(payment.getApprovedAt()),
                payment.getReceiptUrl(),
                payment.getStatus().name());
    }
}
