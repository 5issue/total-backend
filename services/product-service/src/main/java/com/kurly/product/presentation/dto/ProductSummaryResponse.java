package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Product;

public record ProductSummaryResponse(
        Long id,
        String name,
        String brand,
        Long price,
        Long salePrice,
        Integer discountRate,
        Integer likeCount,
        String thumbnailUrl
) {
    public static ProductSummaryResponse of(Product product, String thumbnailUrl) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getPrice(),
                product.getSalePrice(),
                product.getDiscountRate(),
                product.getLikeCount(),
                thumbnailUrl
        );
    }
}
