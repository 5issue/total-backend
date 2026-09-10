package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.ProductSpec;
import com.kurly.product.infrastructure.entity.ProductSpec.PackagingType;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;

public record ProductSpecResponse(
        StorageType storageType,
        PackagingType packagingType,
        String attributes
) {
    public static ProductSpecResponse from(ProductSpec spec) {
        return new ProductSpecResponse(spec.getStorageType(), spec.getPackagingType(), spec.getAttributes());
    }
}
