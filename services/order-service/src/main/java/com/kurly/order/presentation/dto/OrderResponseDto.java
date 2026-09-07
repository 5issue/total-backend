package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDto(Long orderId, String orderNo, LocalDateTime orderedAt, OrderStatus orderStatus,
                               Long paymentAmount, int totalQuantity, List<OrderItemResponseDto> items) {
    public static OrderResponseDto from(Order order) {
        return new OrderResponseDto(order.getId(), order.getOrderNo(), order.getCreatedAt(), order.getStatus(),
                order.getPaymentAmount(), order.getItems().stream().mapToInt(item -> item.getQuantity()).sum(),
                order.getItems().stream().map(OrderItemResponseDto::from).toList());
    }
}
