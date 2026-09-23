package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddCartItemsRequestDto(
        @NotEmpty List<CartItemRequest> items
) {
    public record CartItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
