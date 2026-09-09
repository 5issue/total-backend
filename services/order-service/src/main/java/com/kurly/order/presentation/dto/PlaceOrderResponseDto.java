package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;
import java.time.LocalDateTime;

public record PlaceOrderResponseDto(Long orderId, String orderNo, OrderStatus status, LocalDateTime expiresAt) {
    public static PlaceOrderResponseDto from(Order order) {
        return new PlaceOrderResponseDto(order.getId(), order.getOrderNo(), order.getStatus(),
                order.getInventoryReservedUntil());
    }
}
