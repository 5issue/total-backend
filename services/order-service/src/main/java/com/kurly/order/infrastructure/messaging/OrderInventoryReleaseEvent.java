package com.kurly.order.infrastructure.messaging;

import com.kurly.order.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderInventoryReleaseEvent(UUID eventId, UUID reservationToken, Long orderId,
                                         Long memberId, List<Item> items, LocalDateTime occurredAt) {
    public static OrderInventoryReleaseEvent of(Order order) {
        return new OrderInventoryReleaseEvent(UUID.randomUUID(), order.getInventoryReservationToken(),
                order.getId(), order.getMemberId(), order.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getQuantity())).toList(), LocalDateTime.now());
    }

    public record Item(Long productId, Integer quantity) {
    }
}
