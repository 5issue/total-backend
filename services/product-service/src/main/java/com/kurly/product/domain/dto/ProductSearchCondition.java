package com.kurly.product.domain.dto;

import com.kurly.product.domain.enums.PriceBand;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;

/**
 * 상품 목록 조회 필터 조건. categoryId 를 제외한 나머지는 nullable(미적용).
 */
public record ProductSearchCondition(
        Long categoryId,
        String keyword,
        String brand,
        PriceBand priceBand,
        StorageType storageType
) {
    public String normalizedKeyword() {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    public Long minPrice() {
        return priceBand == null ? null : priceBand.getMinInclusive();
    }

    public Long maxPrice() {
        return priceBand == null ? null : priceBand.getMaxExclusive();
    }
}
