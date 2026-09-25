package com.kurly.oms.presentation.dto;

import java.util.List;

public record OmsOrderDetailResponse(
        Long omsOrderId,
        Long orderId,
        String orderNo,
        String status,
        RegionInfo region,
        List<ShipmentDetail> shipments
) {
    public record RegionInfo(
            Long regionId,
            String regionName,
            String deliveryType
    ) {
    }

    public record ShipmentDetail(
            Long shipmentId,
            String shipmentNo,
            String storageType,
            String status,
            String centerName,
            String slotName,
            List<ShipmentItemDetail> items
    ) {
    }

    public record ShipmentItemDetail(
            Long omsOrderItemId,
            Long productId,
            Long skuId,
            Integer quantity
    ) {
    }
}