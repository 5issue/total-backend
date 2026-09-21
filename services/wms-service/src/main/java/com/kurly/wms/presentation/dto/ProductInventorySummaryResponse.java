package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.jpa.ProductInventorySummaryProjection;

public record ProductInventorySummaryResponse(
        Long productId,

        /** 조회 시 warehouseId를 지정하지 않았으면(전국 통합 집계) null이다. */
        Long warehouseId,

        Integer totalQuantity,
        Integer totalReservedQuantity,
        Integer totalAvailableQuantity
) {
    public static ProductInventorySummaryResponse of(ProductInventorySummaryProjection projection, Long warehouseId) {
        int totalQuantity = projection.totalQuantity().intValue();
        int totalReservedQuantity = projection.totalReservedQuantity().intValue();
        return new ProductInventorySummaryResponse(
                projection.productId(),
                warehouseId,
                totalQuantity,
                totalReservedQuantity,
                totalQuantity - totalReservedQuantity
        );
    }
}
