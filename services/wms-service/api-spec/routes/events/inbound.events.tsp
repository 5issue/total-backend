import "../../models/events.dto.tsp";

namespace WmsService.Events;

// inventory.events.tsp(order-service → wms, 소비 이벤트)와 반대 방향 — 여기는 wms가
// 발행하고 다른 서비스가 소비하는 이벤트다. REST가 도메인별로 inbound/inventory/outbound
// 파일을 나누는 것과 같은 이유로, "입고" 도메인 이벤트를 별도 파일로 분리했다.

@doc("""
  검수 완료(입고 확정 플로우 3단계: StockMovement 생성) 직후 WMS가 발행하는 "재고 입고" 이벤트.
  product-service가 상품 도메인의 총 재고 수치를 갱신하는 데 쓸 것을 염두에 두고 설계했다
  (`InboundOrderService.inspect()` 코드 주석의 "4단계: 상품 도메인 이벤트 발행"에 해당).

  ⚠️ 계약만 우선 정의한 상태다 — WMS 쪽 발행자(Outbox 엔티티를 이미 갖고 있으니 그 패턴을
  재사용할 예정)와 product-service 쪽 소비자 둘 다 아직 구현되지 않았다. product-service에
  현재 이 토픽을 구독하는 코드가 없으므로, 실제 연동 전에 컨슈머 팀과 페이로드를 다시 확인해야
  한다.
  """)
model InboundCompletedEvent {
  title: "입고 검수 완료(재고 증가)";
  producer: "wms";
  consumer: "product";
  topic: "wms.inbound.completed";
  exchange: "wms.topic.exchange";
  queue: "product.inbound.completed.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.wms.infrastructure.messaging.dto.InboundCompletedEvent";
  payload: InboundCompletedEventPayload;
}
