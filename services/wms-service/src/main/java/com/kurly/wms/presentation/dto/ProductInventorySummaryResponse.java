package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.jpa.ProductInventorySummaryProjection;

public record ProductInventorySummaryResponse(
        Long productId,

        /** 조회 시 warehouseId를 지정하지 않았으면(전국 통합 집계) null이다. */
        Long warehouseId,

        Long totalQuantity,
        Long totalReservedQuantity,
        Long totalAvailableQuantity
) {
    public static ProductInventorySummaryResponse of(ProductInventorySummaryProjection projection, Long warehouseId) {
        long totalQuantity = projection.totalQuantity();
        long totalReservedQuantity = projection.totalReservedQuantity();
        return new ProductInventorySummaryResponse(
                projection.productId(),
                warehouseId,
                totalQuantity,
                totalReservedQuantity,
                totalQuantity - totalReservedQuantity
        );
    }
}
