package com.kurly.oms.presentation.dto;

import com.kurly.oms.domain.fulfillment.DeliveryType;

import java.time.Instant;

public record DeliveryPromiseResponse(
        boolean deliverable,
        Long regionId,
        DeliveryType deliveryType,
        Instant cutoffAt,
        Instant expectedDeliveryAt
) {
}
