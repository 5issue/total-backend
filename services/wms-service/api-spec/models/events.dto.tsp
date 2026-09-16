namespace WmsService.Events;

/**
 * order-service가 실제로 발행하는 이벤트. `com.kurly.order.domain.order.OrderEvent`(record)와 1:1로 대응한다.
 * product-service가 이미 같은 클래스를 구독하고 있다(`InventoryEvent` — 필드는 동일, 로컬 이름만 다르다).
 * WMS도 자체 로컬 DTO로 역직렬화할 뿐 이 클래스에 직접 의존하지 않는다.
 */
model OrderEvent {
  eventId: string;
  routingKey: string;

  @doc("재고 HOLD/CONFIRM/RESTORE를 하나로 묶는 토큰. order.getInventoryReservationToken().")
  reservationToken: string | null;

  orderId: int64;
  memberId: int64;
  items: OrderEventItem[];

  @doc("Java LocalDateTime(occurredAt) — UTC가 아니라 서버 로컬 타임존의 naive datetime이다. 예시는 utcDateTime으로 근사한다.")
  occurredAt: utcDateTime;
}

model OrderEventItem {
  productId: int64;
  quantity: int32;
}

/**
 * WMS가 검수 완료(입고 확정 플로우 3단계 직후) 시점에 발행하는 이벤트의 페이로드. 아직 구현되지
 * 않은 `com.kurly.wms.infrastructure.messaging.dto.InboundCompletedEvent`(예정)와 대응한다 —
 * `product-service`의 `ProductInventoryConfirmedEvent`처럼 발행 클래스의 FQCN이 그대로
 * `__TypeId__` 헤더가 되므로, 실제 구현 시 이 패키지/클래스명을 그대로 따른다.
 *
 * `quantity`는 이번 검수로 새로 늘어난 낱개(EA) 수량이다(누적치 아님) — `InboundItem.totalBaseQuantity`
 * 그대로다. 아직 최종 보관 위치로의 물리 이동(put-away)이 끝나지 않은 시점이라 버퍼(BUFFER)
 * 재고 기준이며, `locationId`/`zone`은 담지 않는다 — 소비자 입장에서 "WMS 총 재고가 얼마나
 * 늘었는지"만 중요하고 창고 내부 위치는 관심사가 아니라고 보았다.
 */
model InboundCompletedEventPayload {
  eventId: string;
  routingKey: string;

  warehouseId: int64;
  inboundOrderId: int64;
  inboundItemId: int64;
  productId: int64;

  lotNo: string;
  expiredDate: plainDate;

  @doc("이번 검수로 새로 입고된 낱개(EA) 수량. InboundItem.totalBaseQuantity와 동일.")
  quantity: int32;

  occurredAt: utcDateTime;
}
