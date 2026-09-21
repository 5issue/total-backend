package com.kurly.order.infrastructure.messaging;

import com.kurly.order.domain.order.Order;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderReturnRequestedEvent(
        UUID eventId,
        Long orderId,
        Long memberId,
        LocalDateTime occurredAt
) {
    public static OrderReturnRequestedEvent of(Order order) {
        return new OrderReturnRequestedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getMemberId(),
                LocalDateTime.now()
        );
    }
}