package com.kurly.oms.presentation.dto;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderStatus;

public record CancelEligibilityResponseDto(
        Long orderId,
        boolean cancelable,
        OmsOrderStatus omsStatus,
        boolean releaseRequired
) {
    public static CancelEligibilityResponseDto from(OmsOrder omsOrder) {
        return new CancelEligibilityResponseDto(
                omsOrder.getOrderId(),
                omsOrder.isCancelableOrder(),
                omsOrder.getStatus(),
                omsOrder.isReleaseRequired()
        );
    }
}
