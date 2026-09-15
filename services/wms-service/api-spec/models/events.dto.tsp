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
