package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.DeliveryStatus;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;

public record OrderDetailResponseDto(OrderResponseDto order, Long paymentId, String fulfillmentStatus,
                                     DeliveryStatus deliveryStatus, boolean selfCancelable) {
    public static OrderDetailResponseDto from(Order order) {
        boolean cancellable = order.getStatus() == OrderStatus.PAID
                && !"RELEASE_INSTRUCTED".equals(order.getFulfillmentStatus());
        return new OrderDetailResponseDto(OrderResponseDto.from(order), order.getPaymentId(),
                order.getFulfillmentStatus(), order.getDeliveryStatus(), cancellable);
    }
}
