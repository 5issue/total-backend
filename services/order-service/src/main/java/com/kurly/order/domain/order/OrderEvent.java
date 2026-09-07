package com.kurly.order.domain.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        Long memberId,
        List<Item> items,
        LocalDateTime occurredAt
) {
    public static OrderEvent of(String routingKey, Order order) {
        return new OrderEvent(
                UUID.randomUUID(),
                routingKey,
                order.getId(),
                order.getMemberId(),
                order.getItems().stream().map(item -> new Item(item.getProductId(), item.getQuantity())).toList(),
                LocalDateTime.now()
        );
    }

    public record Item(Long productId, Integer quantity) {
    }
}
