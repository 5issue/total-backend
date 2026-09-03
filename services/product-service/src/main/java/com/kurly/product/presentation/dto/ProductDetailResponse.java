package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Product;

import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String skuCode,
        String name,
        String shortDescription,
        String brand,
        Long price,
        Integer discountRate,
        Long salePrice,
        ProductType type,
        ProductStatus status,
        Integer likeCount,
        Long totalSalesCount,
        LocalDateTime createdAt,
        ProductSpecResponse spec,
        List<ProductMediaResponse> media,
        List<ProductUnitResponse> units
) {
    public static ProductDetailResponse of(Product product,
                                            ProductSpecResponse spec,
                                            List<ProductMediaResponse> media,
                                            List<ProductUnitResponse> units) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getShortDescription(),
                product.getBrand(),
                product.getPrice(),
                product.getDiscountRate(),
                product.getSalePrice(),
                product.getType(),
                product.getStatus(),
                product.getLikeCount(),
                product.getTotalSalesCount(),
                product.getCreatedAt(),
                spec,
                media,
                units
        );
    }

}
