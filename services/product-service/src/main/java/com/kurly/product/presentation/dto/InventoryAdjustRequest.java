package com.kurly.product.presentation.dto;

import com.kurly.product.domain.dto.ReserveItem;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
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

    @AssertTrue(message = "items에 같은 productId가 중복될 수 없습니다.")
    public boolean isItemsProductIdUnique() {
        if (items == null) {
            return true;
        }
        long distinctProductIdCount = items.stream().map(ReserveItemRequest::productId).distinct().count();
        return distinctProductIdCount == items.size();
    }

    public List<ReserveItem> toReserveItems() {
        assert items != null;
        return items.stream().map(ReserveItemRequest::toReserveItem).toList();
    }
}
