package com.kurly.order.infrastructure.dto;

import java.util.List;
import java.util.UUID;

public record InventoryHoldRequest(
        UUID reservationToken,
        List<InventoryHoldItem> items
) {

    public record InventoryHoldItem(
            Long productId,
            Integer quantity
    ) {
    }
}
