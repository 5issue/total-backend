package com.kurly.product.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record InventoryAdjustRequest(
        @NotNull Long orderId,
        @NotEmpty @Valid List<ReserveItem> items
) {
    public record ReserveItem(
            @NotNull Long orderItemId,
            @NotNull Long productId,
            @NotNull @Positive Integer quantity
    ) {}
}
