package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;

public record ProductUnitResponse(
        Long id,
        String skuCode,
        String name,
        Long price,
        Long salePrice,
        ProductStatus status
) {
    public static ProductUnitResponse from(Product product) {
        return new ProductUnitResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getPrice(),
                product.getSalePrice(),
                product.getStatus()
        );
    }
}
