package com.kurly.product.domain.enums;

import org.springframework.data.domain.Sort;

public enum ProductSortType {
    RECOMMENDED,
    LATEST,
    POPULAR,
    BENEFIT,
    PRICE_DESC,
    PRICE_ASC;

    public Sort toSort() {
        Sort primary = switch (this) {
            case RECOMMENDED -> Sort.by(Sort.Direction.DESC, "likeCount");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case POPULAR -> Sort.by(Sort.Direction.DESC, "totalSalesCount");
            case BENEFIT -> Sort.by(Sort.Direction.DESC, "discountRate");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "salePrice");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "salePrice");
        };
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
