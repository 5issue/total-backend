package com.kurly.product.presentation.dto;

import com.kurly.product.domain.dto.ReserveItem;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InventoryAdjustRequest(
        @NotNull UUID reservationToken,
        @Nullable List<ReserveItemRequest> items
) {
    public record ReserveItemRequest(
            Long productId,
            Integer quantity
    ) {
        public ReserveItem toReserveItem() {
            return new ReserveItem(productId, quantity);
        }
    }

    public List<ReserveItem> toReserveItems() {
        return items.stream().map(ReserveItemRequest::toReserveItem).toList();
    }
}
