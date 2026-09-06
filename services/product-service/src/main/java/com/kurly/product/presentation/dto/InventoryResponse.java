package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.ProductInventory;

public record InventoryResponse(
        Long productId,
        Integer baseQuantity,
        Integer reservedQuantity,
        Integer availableQuantity
) {
    public static InventoryResponse from(ProductInventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getBaseQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity()
        );
    }
}
