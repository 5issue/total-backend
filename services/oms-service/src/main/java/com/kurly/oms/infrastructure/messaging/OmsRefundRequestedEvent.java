package com.kurly.oms.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OmsRefundRequestedEvent(
        UUID eventId,
        Long omsOrderId,
        Long orderId,
        Long refundAmount,
        Long deductedFee,
        List<Long> omsOrderItemIds,
        LocalDateTime occurredAt
) {
    public static OmsRefundRequestedEvent of(
            Long omsOrderId,
            Long orderId,
            Long refundAmount,
            Long deductedFee,
            List<Long> itemIds
    ) {
        return new OmsRefundRequestedEvent(
                UUID.randomUUID(),
                omsOrderId,
                orderId,
                refundAmount,
                deductedFee,
                itemIds,
                LocalDateTime.now()
        );
    }
}
