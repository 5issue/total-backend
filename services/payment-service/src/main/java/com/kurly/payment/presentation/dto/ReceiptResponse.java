package com.kurly.payment.presentation.dto;

import com.kurly.payment.domain.entity.Payment;

import java.time.Instant;

/** 결제 완료 결과 및 승인 영수증. */
public record ReceiptResponse(
        Long paymentId,
        Long orderId,
        String paymentMethod,
        Long totalAmount,
        Instant paymentCompletedAt,
        String receiptUrl
) {

    public static ReceiptResponse from(Payment payment) {
        return new ReceiptResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getTotalAmount(),
                Timestamps.toInstant(payment.getApprovedAt()),
                payment.getReceiptUrl());
    }
}
