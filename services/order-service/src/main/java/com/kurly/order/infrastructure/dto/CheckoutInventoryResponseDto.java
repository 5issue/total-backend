package com.kurly.order.infrastructure.dto;

import com.kurly.order.domain.common.StorageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CheckoutInventoryResponseDto(
        UUID reservationToken,
        LocalDateTime expiresAt,
        Long shippingFee,
        List<Item> items
) {

    public record Item(
            Long productId,
            Long dealProductId,
            Long skuId,
            String productName,
            String optionName,
            StorageType storageType,
            Integer quantity,
            Long unitPrice
    ) {
    }
}
