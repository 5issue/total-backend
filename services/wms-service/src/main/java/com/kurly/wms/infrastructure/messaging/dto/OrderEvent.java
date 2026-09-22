package com.kurly.wms.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderEvent(
        UUID eventId,
        String routingKey,
        String reservationToken,
        Long orderId,
        Long memberId,
        Long warehouseId,
        RecipientInfo recipientInfo,
        List<OrderEventItem> items,
        LocalDateTime occurredAt
) {
    public record RecipientInfo(
            String name,
            String address,
            String zipCode) {
    }

    public record OrderEventItem(
            Long orderItemId,
            Long productId,
            Integer quantity,
            String productName) {
    }
}
