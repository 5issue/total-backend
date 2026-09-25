package com.kurly.wms.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 출고 완료(피킹 완료 후 실물 재고 차감) 시점에 발행하는 이벤트. WMS → order.
 *
 * <p>클래스의 완전 정규화 이름이 그대로 발행 시 {@code __TypeId__} 헤더 값이 되므로
 * 패키지/클래스명을 임의로 바꾸면 안 된다(api-spec의 {@code OutboundCompletedEvent.payloadType}과
 * 1:1 대응).
 *
 * <p>주문 상세(orderItemId) 단위 매칭은 아직 지원하지 않는다 — {@code OutboundItem}에
 * 대응 컬럼이 없다(propose 단계 필드, erd-spec 논의사항 참고). 이번 버전은 부분 출고/숏도
 * 없어 상품별 수량만으로 충분하다고 보고 주문(orderId) 단위 완료 신호로 우선 정의한다.
 */
public record OutboundCompletedEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        Long warehouseId,
        List<Item> items,
        LocalDateTime occurredAt
) {
    public record Item(Long productId, Integer quantity) {
    }
}
