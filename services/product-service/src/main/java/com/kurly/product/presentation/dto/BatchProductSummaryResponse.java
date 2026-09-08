package com.kurly.product.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BatchProductSummaryResponse(
        List<ProductSummaryItem> products
) {
    public record ProductSummaryItem(
            Long productId,
            String name,
            Long salePrice,
            String thumbnailUrl,
            String storageType,
            String status,
            String seller,
            InventoryInfo inventory
    ) {}

    public record InventoryInfo(
            int availableQuantity,
            @JsonProperty("isSoldOut") boolean isSoldOut,
            int maxQuantityPerOrder
    ) {}
}
