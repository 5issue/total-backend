package com.kurly.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddCartItemsRequestDto(
        @NotEmpty
        List<@NotNull @Valid CartItemRequest> items
) {
    public record CartItemRequest(
            @NotNull(message = "상품 ID는 필수입니다.")
            Long productId,

            @NotNull(message = "수량은 필수입니다.")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            Integer quantity
    ) {
    }
}