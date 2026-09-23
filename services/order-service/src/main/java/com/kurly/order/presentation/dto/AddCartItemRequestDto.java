package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequestDto(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}
