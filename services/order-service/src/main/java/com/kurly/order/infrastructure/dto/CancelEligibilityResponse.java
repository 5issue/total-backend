package com.kurly.order.infrastructure.dto;

public record CancelEligibilityResponse(
        Long orderId,
        boolean cancelable,
        String omsStatus,
        boolean releaseRequired
) {
}