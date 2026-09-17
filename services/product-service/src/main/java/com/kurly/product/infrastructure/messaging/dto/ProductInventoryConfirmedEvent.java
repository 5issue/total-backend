package com.kurly.product.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 재고 확정 결과 이벤트. Product → Order, {@code product.inventory.confirmed}.
 *
 * <p>클래스의 완전 정규화 이름이 그대로 발행 시 {@code __TypeId__} 헤더 값이 되므로
 * 패키지/클래스명을 임의로 바꾸면 안 된다.
 */
public record ProductInventoryConfirmedEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        Status status,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FailedItemInfo> failedItems,
        Instant confirmedAt
) {
    public record FailedItemInfo(
            Long productId,
            Integer requestedQuantity
    ) {
    }

    public enum Status {
        CONFIRMED,
        INSUFFICIENT_STOCK
    }
}
