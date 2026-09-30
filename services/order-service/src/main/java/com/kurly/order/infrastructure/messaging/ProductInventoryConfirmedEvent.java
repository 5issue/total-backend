package com.kurly.order.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductInventoryConfirmedEvent(
        UUID eventId,
        Long orderId,
        Status status,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FailedItemInfo> failedItems,
        Instant confirmedAt
) {
    public enum Status {
        CONFIRMED,
        INSUFFICIENT_STOCK
    }

    public record FailedItemInfo(
            Long productId,
            Integer requestedQuantity
    ) {
    }
}
