package com.kurly.oms.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OmsReturnInspectionRequestedEvent(
        UUID eventId,
        Long omsOrderId,
        Long orderId,
        List<Long> omsOrderItemIds,
        LocalDateTime occurredAt
) {
    public static OmsReturnInspectionRequestedEvent of(Long omsOrderId, Long orderId, List<Long> itemIds) {
        return new OmsReturnInspectionRequestedEvent(
                UUID.randomUUID(),
                omsOrderId,
                orderId,
                itemIds,
                LocalDateTime.now()
        );
    }
}