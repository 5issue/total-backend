package com.kurly.order.infrastructure.dto;

import com.kurly.order.domain.common.StorageType;

public record CartProductInfo(
        Long productId,
        String name,
        Long salePrice,
        String thumbnailUrl,
        StorageType storageType,
        String status,
        String seller,
        InventoryInfo inventory
) {
    public record InventoryInfo(
            int availableQuantity,
            boolean isSoldOut,
            int maxQuantityPerOrder
    ) {
    }
}
