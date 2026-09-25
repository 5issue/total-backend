package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.OutboundOrder;
import com.kurly.wms.infrastructure.entity.OutboundOrder.OutboundOrderStatus;
import java.time.LocalDateTime;

public record OutboundOrderResponse(
        Long id,
        Long orderId,
        Long warehouseId,
        OutboundOrderStatus status,
        LocalDateTime createdAt
) {
    public static OutboundOrderResponse from(OutboundOrder outboundOrder) {
        return new OutboundOrderResponse(
                outboundOrder.getId(),
                outboundOrder.getOrderId(),
                outboundOrder.getWarehouse().getId(),
                outboundOrder.getStatus(),
                outboundOrder.getCreatedAt()
        );
    }
}
