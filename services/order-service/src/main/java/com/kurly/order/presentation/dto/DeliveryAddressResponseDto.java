package com.kurly.order.presentation.dto;

import com.kurly.order.domain.cart.DeliveryType;
import java.time.LocalDateTime;

public record DeliveryAddressResponseDto(CartResponseDto.Address selectedAddress, boolean deliverable,
                                         DeliveryType deliveryType, LocalDateTime cutoffAt, LocalDateTime expectedDeliveryAt) {
    public record Promise(boolean deliverable, Long regionId, DeliveryType deliveryType,
                          LocalDateTime cutoffAt, LocalDateTime expectedDeliveryAt) {}
}
