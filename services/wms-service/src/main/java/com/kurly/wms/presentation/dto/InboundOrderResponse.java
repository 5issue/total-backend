package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.InboundOrder;
import com.kurly.wms.infrastructure.entity.InboundOrder.InboundOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record InboundOrderResponse(
        Long id,
        Long warehouseId,
        String poNumber,
        String supplierName,
        InboundOrderStatus status,
        LocalDateTime expectedDate,
        LocalDateTime completedDate,
        List<InboundItemResponse> items
) {
    public static InboundOrderResponse of(InboundOrder order, List<InboundItemResponse> items) {
        return new InboundOrderResponse(
                order.getId(),
                order.getWarehouse().getId(),
                order.getPoNumber(),
                order.getSupplierName(),
                order.getStatus(),
                order.getExpectedDate(),
                order.getCompletedDate(),
                items
        );
    }
}
