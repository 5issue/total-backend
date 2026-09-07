package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.OrderItem;

public record OrderItemResponseDto(Long orderItemId, Long productId, Long skuId, String title,
                                   Integer quantity, Long unitPrice, Long totalPrice) {
    public static OrderItemResponseDto from(OrderItem item) {
        return new OrderItemResponseDto(item.getId(), item.getProductId(), item.getSkuId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineAmount());
    }
}
