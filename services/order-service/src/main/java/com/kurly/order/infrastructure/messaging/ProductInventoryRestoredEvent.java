package com.kurly.order.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record ProductInventoryRestoredEvent(
        UUID eventId,
        String routingKey,
        String reservationToken,
        Long orderId,
        String status,
        Instant restoredAt
) {
}
