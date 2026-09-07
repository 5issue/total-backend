package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaceOrderRequestDto(@NotNull @Positive Long orderId) {
}
