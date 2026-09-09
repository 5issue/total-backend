package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import java.time.LocalDateTime;
import java.util.List;

public record CheckoutResponseDto(Long orderId, String orderNo, String reservationToken,
                                  LocalDateTime expiresAt, Long paymentAmount, List<OrderItemResponseDto> items) {
    public static CheckoutResponseDto from(Order order) {
        return new CheckoutResponseDto(order.getId(), order.getOrderNo(), order.getInventoryReservationToken(),
                order.getInventoryReservedUntil(), order.getPaymentAmount(),
                order.getItems().stream().map(OrderItemResponseDto::from).toList());
    }
}
