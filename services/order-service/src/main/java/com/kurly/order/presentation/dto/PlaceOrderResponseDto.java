package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;
import java.time.Instant;
import java.time.ZoneId;

public record PlaceOrderResponseDto(Long orderId, String orderNo, OrderStatus status, Instant expiresAt) {
    public static PlaceOrderResponseDto from(Order order) {
        // CheckoutResponseDto.from() 참고 — LocalDateTime을 타임존 표기 없이 그대로
        // 내려보내면 브라우저가 로컬 시간대로 잘못 해석한다. ZoneId.systemDefault()는
        // LocalDateTime.now()가 이 값을 만들 때 쓴 바로 그 시간대라 배포/로컬 어느
        // 쪽이든 정확하다.
        Instant expiresAt = order.getInventoryReservedUntil() == null
                ? null
                : order.getInventoryReservedUntil().atZone(ZoneId.systemDefault()).toInstant();
        return new PlaceOrderResponseDto(order.getId(), order.getOrderNo(), order.getStatus(), expiresAt);
    }
}
