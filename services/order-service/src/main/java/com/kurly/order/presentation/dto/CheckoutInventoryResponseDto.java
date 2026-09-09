package com.kurly.order.presentation.dto;

import com.kurly.order.domain.common.StorageType;
import java.time.LocalDateTime;
import java.util.List;

public record CheckoutInventoryResponseDto(String reservationToken, LocalDateTime expiresAt, Long shippingFee,
                                           List<Item> items) {
    public record Item(Long productId, Long dealProductId, Long skuId, String productName,
                       String optionName, StorageType storageType, Integer quantity, Long unitPrice) {
    }
}
