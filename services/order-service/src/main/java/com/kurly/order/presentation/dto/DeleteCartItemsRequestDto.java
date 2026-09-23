package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeleteCartItemsRequestDto(
        @NotEmpty(message = "삭제할 상품 ID 목록은 필수입니다.")
        List<Long> productIds
) {
}
