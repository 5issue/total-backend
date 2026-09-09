package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckoutRequestDto(@NotNull @Size(min = 1) List<@NotNull @Positive Long> cartItemIds) {
}
