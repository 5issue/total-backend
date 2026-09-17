package com.kurly.wms.infrastructure.messaging.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 검수 완료(입고 확정 플로우 3단계 직후) 시점에 발행하는 "재고 입고" 이벤트. WMS → product.
 *
 * <p>클래스의 완전 정규화 이름이 그대로 발행 시 {@code __TypeId__} 헤더 값이 되므로
 * 패키지/클래스명을 임의로 바꾸면 안 된다(api-spec의 {@code InboundCompletedEvent.payloadType}과
 * 1:1 대응).
 */
public record InboundCompletedEvent(
        UUID eventId,
        String routingKey,
        Long warehouseId,
        Long inboundOrderId,
        Long inboundItemId,
        Long productId,
        String lotNo,
        LocalDate expiredDate,
        Integer quantity,
        LocalDateTime occurredAt
) {
}
