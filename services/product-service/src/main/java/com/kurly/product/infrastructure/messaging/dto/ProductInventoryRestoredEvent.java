package com.kurly.product.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 복구 결과 이벤트. Product → Order, {@code product.inventory.restored}.
 *
 * <p>클래스의 완전 정규화 이름이 그대로 발행 시 {@code __TypeId__} 헤더 값이 되므로
 * 패키지/클래스명을 임의로 바꾸면 안 된다.
 */
public record ProductInventoryRestoredEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        Status status,
        LocalDateTime restoredAt
) {
    public enum Status {
        RESTORED,
        ALREADY_RESTORED
    }
}
