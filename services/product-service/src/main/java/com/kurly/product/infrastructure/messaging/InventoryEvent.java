package com.kurly.product.infrastructure.messaging;

import com.kurly.product.domain.dto.ReserveItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryEvent(
        UUID eventId,
        String routingKey,
        String reservationToken,
        Long orderId,
        Long memberId,
        List<ReserveItem> items,
        LocalDateTime occurredAt
) {

}