package com.kurly.product.infrastructure.messaging;

import com.kurly.product.domain.dto.ReserveItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 출고 완료(피킹 완료 후 실물 재고 차감) 시점에 WMS가 발행하는 이벤트. WMS → product.
 *
 * <p>WMS의 {@code com.kurly.wms.infrastructure.messaging.dto.OutboundCompletedEvent}와
 * 페이로드 필드가 1:1 대응해야 한다. 역직렬화는 {@code __TypeId__} 헤더가 아니라 리스너
 * 메서드 파라미터 타입으로 추론되므로(INFERRED), 클래스명·패키지는 WMS와 달라도 된다.
 */
public record OutboundCompletedEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        Long warehouseId,
        List<ReserveItem> items,
        LocalDateTime occurredAt
) {
}
