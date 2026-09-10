package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Product;
import java.util.List;

public record HomeResponse(
        List<HomeSection> sections

) {
    public record HomeSection(
            String type,
            String title,
            List<QuickMenuItemDto> quickMenus,
            List<ProductSummaryDto> products
    ) {
        public static HomeSection of(String type, String title, List<QuickMenuItemDto> quickMenus) {
            return new HomeSection(
                    type,
                    title,
                    quickMenus,
                    null
            );
        }
    }

    public record QuickMenuItemDto(
            String title,
            String imageUrl,
            String linkUrl
    ) {}

    public record ProductSummaryDto(
            Long id,
            String name,
            String brand,
            Long price,
            Long salePrice,
            Integer discountRate,
            Integer likeCount,
            String thumbnailUrl
    ) {
        public static ProductSummaryDto of(Product product, String thumbnailUrl) {
            return new ProductSummaryDto(
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
}
