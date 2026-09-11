package com.kurly.product.presentation.dto;

import com.kurly.product.domain.dto.ReserveItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record InventoryAdjustRequest(
        @NotNull UUID eventId,
        String routingKey,
        @NotNull Long orderId,
        Long memberId,
        @NotEmpty @Valid List<ReserveItemRequest> items
) {
    public record ReserveItemRequest(
            @NotNull Long productId,
            @NotNull @Positive Integer quantity
    ) {
        public ReserveItem toReserveItem() {
            return new ReserveItem(productId, quantity);
        }
    }

    public List<ReserveItem> toReserveItems() {
        return items.stream().map(ReserveItemRequest::toReserveItem).toList();
    }
}
