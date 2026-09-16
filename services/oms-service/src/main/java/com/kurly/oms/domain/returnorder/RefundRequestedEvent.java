package com.kurly.oms.domain.returnorder;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundRequestedEvent(UUID eventId, String routingKey, Long returnId,
                                   Long orderId, Long userId, Long paymentId,
                                   Long refundAmount, String reason, LocalDateTime occurredAt) {
    public static RefundRequestedEvent from(OmsReturnProcess process, String reason) {
        return new RefundRequestedEvent(UUID.randomUUID(), "order.refund.requested",
                process.getReturnId(), process.getOmsOrder().getOrderId(), process.getUserId(),
                process.getPaymentId(), process.getRefundAmount(), reason,
                LocalDateTime.now());
    }
}
