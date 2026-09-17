package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.InboundItem;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundItemStatus;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundUnit;

public record InboundItemResponse(
        Long id,
        Long productId,
        InboundUnit inboundUnit,
        Integer orderedQuantity,
        Integer inspectQuantity,
        Integer totalBaseQuantity,
        InboundItemStatus status,
        Long stockMovementId
) {
    public static InboundItemResponse from(InboundItem item) {
        return from(item, null);
    }

    public static InboundItemResponse from(InboundItem item, Long stockMovementId) {
        return new InboundItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getInboundUnit(),
                item.getOrderedQuantity(),
                item.getInspectQuantity(),
                item.getTotalBaseQuantity(),
                item.getStatus(),
                stockMovementId
        );
    }
}
