namespace WmsService.Events;

/**
 * order-service가 실제로 발행하는 이벤트. `com.kurly.order.domain.order.OrderEvent`(record)와 1:1로 대응한다.
 * product-service가 이미 같은 클래스를 구독하고 있다(`InventoryEvent` — 필드는 동일, 로컬 이름만 다르다).
 * WMS도 자체 로컬 DTO로 역직렬화할 뿐 이 클래스에 직접 의존하지 않는다.
 *
 * ⚠️ `warehouseId`/`recipientInfo`는 아직 order-service의 실제 `OrderEvent.java`에는 없는
 * **제안(propose) 단계 확장**이다 — `order.inventory.confirm`을 "재고 확정만" 하는 이벤트에서
 * "재고 확정 + 출고 지시(OutboundOrder) 생성"까지 커버하는 이벤트로 넓히기 위해 필요하다.
 * `OrderEventItem`의 `orderItemId`/`productName`도 같은 이유로 추가된 신규 필드다. 전부
 * 필드 추가(제거/개명 없음)라 이미 이 클래스를 구독 중인 product-service(`InventoryEvent`,
 * HOLD/RELEASE/CONFIRM 소비)에는 영향이 없다 — Jackson 기본 설정은 알 수 없는 필드를 무시한다.
 * order-service 쪽에 실제로 반영되기 전까지는 계약 제안일 뿐이니, 실 연동 전 order-service
 * 팀과 재확인이 필요하다.
 */
model OrderEvent {
  eventId: string;
  routingKey: string;

  @doc("재고 HOLD/CONFIRM/RESTORE를 하나로 묶는 토큰. order.getInventoryReservationToken().")
  reservationToken: string | null;

  orderId: int64;
  memberId: int64;

  @doc("이 주문을 처리할 물류센터. 출고 지시(OutboundOrder) 생성에 필요해 새로 제안하는 필드다 — HOLD/RELEASE/RESTORE 시점에도 항상 채워져 있는지는 order-service 확인이 필요하다.")
  warehouseId: int64;

  @doc("배송지 정보. 출고 지시 생성 및 송장 발행에 필요해 새로 제안하는 필드다.")
  recipientInfo: RecipientInfo;

  items: OrderEventItem[];

  @doc("Java LocalDateTime(occurredAt) — UTC가 아니라 서버 로컬 타임존의 naive datetime이다. 예시는 utcDateTime으로 근사한다.")
  occurredAt: utcDateTime;
}

@doc("배송지 정보. order.inventory.confirm 확장 제안으로 새로 추가.")
model RecipientInfo {
  name: string;
  address: string;
  zipCode: string;
}

model OrderEventItem {
  @doc("""
    OMS 주문 상세(라인 아이템) id. 출고 상세(OutboundItem)와 매핑해 부분 출고/숏 처리 결과를
    다시 주문 쪽에 연결하는 데 쓴다 — 새로 제안하는 필드라 WMS의 OutboundItem에도 아직
    대응 컬럼이 없다(구현 시 추가 필요).
    """)
  orderItemId: int64;

  productId: int64;
  quantity: int32;

  @doc("""
    피킹/패킹 화면·송장 표시용 상품명 스냅샷. WMS가 이미 자체 WmsProduct.name을 갖고 있어
    필수는 아니지만, 이벤트 수신 시점에 해당 상품이 아직 동기화되지 않았을 가능성에
    대비해 실어둔다.
    """)
  productName: string;
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

/**
 * WMS가 출고를 최종 완료(피킹 완료 후 실물 재고 차감)했을 때 발행하는 이벤트의 페이로드.
 * `com.kurly.wms.infrastructure.messaging.dto.OutboundCompletedEvent`와 대응한다.
 *
 * 주문 상세(orderItemId) 단위 매칭은 아직 지원하지 않는다 — `OutboundItem`에 대응 컬럼이
 * 없다(`WmsService.Events.OrderEvent.OrderEventItem.orderItemId`가 propose 단계 필드인 것과
 * 같은 이유). 이번 버전은 부분 출고/숏도 없어 상품별 수량만으로 충분하다고 보고, 주문(orderId)
 * 단위 완료 신호로 우선 정의한다.
 */
model OutboundCompletedEventPayload {
  eventId: string;
  routingKey: string;

  orderId: int64;
  warehouseId: int64;
  items: OutboundCompletedItem[];

  occurredAt: utcDateTime;
}

model OutboundCompletedItem {
  productId: int64;

  @doc("실제로 피킹·차감된 수량. 이번 버전은 부분 피킹을 지원하지 않아 항상 주문 수량과 같다.")
  quantity: int32;
}

/**
 * WMS가 반품 검수를 완료했을 때 발행하는 이벤트의 페이로드. 아직 구현되지 않은
 * `com.kurly.wms.infrastructure.messaging.dto.ReturnInspectionCompletedEvent`(예정)와
 * 대응한다.
 *
 * WMS에는 아직 반품(Return) 도메인 엔티티가 없다 — `InboundCompletedEventPayload`가
 * `InboundOrder`/`InboundItem`에 대응하는 것과 달리, 이 페이로드는 대응하는 WMS 쪽 엔티티가
 * 아직 없어서 `returnOrderId`를 반품을 접수한 상위 도메인(OMS/주문)의 식별자를 그대로
 * 참조하는 외부 참조 키로 취급한다(`StockAllocationRequest.referenceId`와 같은 성격).
 */
model ReturnInspectionCompletedEventPayload {
  eventId: string;
  routingKey: string;

  @doc("반품을 접수한 상위 도메인(OMS/주문)의 식별자. WMS에 아직 자체 Return 엔티티가 없어 외부 참조 키로 취급한다.")
  returnOrderId: int64;

  warehouseId: int64;
  items: ReturnInspectionItem[];

  occurredAt: utcDateTime;
}

@doc("반품 검수 품목 1건의 결과. inspectedQuantity = restockedQuantity + defectiveQuantity가 항상 성립한다.")
model ReturnInspectionItem {
  productId: int64;
  lotNo: string | null;

  @doc("검수 대상 수량(반품 접수 시점 수량).")
  inspectedQuantity: int32;

  @doc("검수 합격 후 판매 가능 재고로 재입고한 수량.")
  restockedQuantity: int32;

  @doc("검수 불합격(파손·오염·유통기한 등)으로 폐기 처리한 수량.")
  defectiveQuantity: int32;
}
