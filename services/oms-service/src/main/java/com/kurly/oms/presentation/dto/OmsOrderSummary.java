package com.kurly.oms.presentation.dto;

public record OmsOrderSummary(
        Long omsOrderId,
        Long orderId,
        String orderNo,
        String status,
        long shipmentCount,
        String regionName,
        String centerName
) {
}
