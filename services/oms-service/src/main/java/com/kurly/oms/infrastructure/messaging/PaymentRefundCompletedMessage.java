package com.kurly.oms.infrastructure.messaging;

import java.util.UUID;

public record PaymentRefundCompletedMessage(
        UUID eventId,
        Long omsReturnId,
        Long refundAmount,
        Long refundAt
) {
}
