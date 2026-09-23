package com.kurly.order.infrastructure.dto;

import com.kurly.order.domain.cart.DeliveryType;

import java.time.LocalDateTime;

public record DeliveryAddressResponseDto(AddressResponse selectedAddress, boolean deliverable,
                                         DeliveryType deliveryType, LocalDateTime cutoffAt,
                                         LocalDateTime expectedDeliveryAt) {
    public record Promise(boolean deliverable, Long regionId, DeliveryType deliveryType,
                          LocalDateTime cutoffAt, LocalDateTime expectedDeliveryAt) {
    }
}
