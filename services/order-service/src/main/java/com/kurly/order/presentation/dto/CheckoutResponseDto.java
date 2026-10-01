package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record CheckoutResponseDto(Long orderId, String orderNo, UUID reservationToken,
                                  Instant expiresAt, Long paymentAmount, List<OrderItemResponseDto> items) {
    public static CheckoutResponseDto from(Order order) {
        // expiresAt(LocalDateTime)을 타임존 표기 없이 그대로 내려보내면 브라우저(JS
        // `new Date()`)가 그 문자열을 KST 등 로컬 시간대로 잘못 해석해 실제보다 9시간
        // 이른 시각으로 읽는다 — 주문서 진입 직후 "주문시간이 초과되었어요" 모달이 바로
        // 뜨는 원인이었다. `ZoneId.systemDefault()`로 바꾼다 — `LocalDateTime.now()`가
        // 이 값을 만들 때 쓴 바로 그 시간대라, 배포(JVM 기본값 UTC)·로컬(개발 머신 시간대)
        // 어느 쪽이든 원래 의도한 순간으로 정확히 복원된다.
        Instant expiresAt = order.getInventoryReservedUntil() == null
                ? null
                : order.getInventoryReservedUntil().atZone(ZoneId.systemDefault()).toInstant();
        return new CheckoutResponseDto(order.getId(), order.getOrderNo(), order.getInventoryReservationToken(),
                expiresAt, order.getPaymentAmount(),
                order.getItems().stream().map(OrderItemResponseDto::from).toList());
    }
}
