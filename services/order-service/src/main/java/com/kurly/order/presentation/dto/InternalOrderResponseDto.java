package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;

public record InternalOrderResponseDto(Long orderId, Long userId, Long amount, OrderStatus status,
                                       String reservationToken, long remainingTimeoutSeconds) {
    public static InternalOrderResponseDto from(Order order, long remainingSeconds) {
        return new InternalOrderResponseDto(order.getId(), order.getMemberId(), order.getPaymentAmount(),
                order.getStatus(), order.getInventoryReservationToken(), remainingSeconds);
    }
}
