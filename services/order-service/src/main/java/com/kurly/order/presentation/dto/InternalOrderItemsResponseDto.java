package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import java.util.List;

public record InternalOrderItemsResponseDto(Long orderId, List<Item> items) {
    public static InternalOrderItemsResponseDto from(Order order) {
        return new InternalOrderItemsResponseDto(order.getId(), order.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getQuantity())).toList());
    }
    public record Item(Long productId, Integer quantity) {
    }
}
