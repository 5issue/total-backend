package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartQuantityUpdateRequest(
        @NotNull(message = "수량은 필수값입니다.")
        @Min(value = 1, message = "수량은 최소 1개 이상이어야 합니다.")
        Integer quantity
) {
}