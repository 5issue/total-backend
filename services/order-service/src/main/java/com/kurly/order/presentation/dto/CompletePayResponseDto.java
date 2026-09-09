package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;

public record CompletePayResponseDto(Long orderId, OrderStatus orderStatus) {
    public static CompletePayResponseDto from(Order order) {
        return new CompletePayResponseDto(order.getId(), order.getStatus());
    }
}
