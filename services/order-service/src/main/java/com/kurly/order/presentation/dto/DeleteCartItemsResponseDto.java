package com.kurly.order.presentation.dto;

import java.util.List;

public record DeleteCartItemsResponseDto(
        List<Long> deletedProductIds
) {
    public static DeleteCartItemsResponseDto from(List<Long> deletedProductIds) {
        return new DeleteCartItemsResponseDto(deletedProductIds);
    }
}