package com.kurly.order.infrastructure.dto;

import com.kurly.order.domain.cart.DeliveryType;

import java.time.Instant;

public record DeliveryAddressResponseDto(AddressResponse selectedAddress, boolean deliverable,
                                         DeliveryType deliveryType, Instant cutoffAt,
                                         Instant expectedDeliveryAt) {
    public record Promise(boolean deliverable, Long regionId, DeliveryType deliveryType,
                          Instant cutoffAt, Instant expectedDeliveryAt) {
    }
}
