package com.kurly.order.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductInventoryRestoredEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        LocalDateTime restoredAt
) {
}