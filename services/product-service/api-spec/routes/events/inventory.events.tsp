import "../../models/events.dto.tsp";

namespace ProductService.Events;

// HTTP 라우트가 없는 비동기(RabbitMQ) 이벤트 정의라 @route/@get/@post는 안 쓴다. wms-service와
// 동일한 컨벤션: producer/consumer/topic/exchange/queue/dataFormat/payloadType을 리터럴
// 타입으로 박아서 "타입이자 값"으로 쓴다. producer/consumer가 둘 이상이면 콤마로 구분한다.
//
// 이 파일은 product-service가 "발행"하는 이벤트만 다룬다. product-service는 실제로
// order.inventory.{release,confirm,restore} 3개도 소비하지만(InventoryEventListener.java),
// 소비 이벤트는 이 스펙의 문서화 범위가 아니다 — 발행자(order-service)가 자체 api-spec을
// 갖추면 그쪽 스펙에서 문서화해야 할 계약이다.

@doc("""
  재고 확정(HOLD→CONFIRM) 처리 결과. `order.inventory.confirm` 이벤트를 소비한 직후 발행한다.

  ⚠️ 현재 이 토픽을 구독하는 큐가 리포지토리 어디에도 없다 — order-service 쪽에
  `product.topic.exchange`+`product.inventory.confirmed` 바인딩이 없어서, 코드상으로는
  정상 발행되지만 받는 소비자가 없는 상태다(발행은 하지만 아무도 못 받음).
  """)
model ProductInventoryConfirmedEvent {
  title: "재고 확정 결과";
  producer: "product";
  consumer: "order";
  topic: "product.inventory.confirmed";
  exchange: "product.topic.exchange";
  queue: "(아직 없음 — 구독 큐 미생성)";
  dataFormat: "JSON";
  payloadType: "com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent";
  payload: ProductInventoryConfirmedEventPayload;
}

@doc("""
  재고 복구 처리 결과. order-service의 취소성 재고 복구 요청을 소비한 뒤 발행하며,
  order-service의 `OrderInventoryRestoredHandler`가 실제로 구독해 주문 취소 완료 처리에 쓴다.
  """)
model ProductInventoryRestoredEvent {
  title: "재고 복구 결과";
  producer: "product";
  consumer: "order";
  topic: "product.inventory.restored";
  exchange: "product.topic.exchange";
  queue: "order.inventory-restored.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.product.infrastructure.messaging.dto.ProductInventoryRestoredEvent";
  payload: ProductInventoryRestoredEventPayload;
}
