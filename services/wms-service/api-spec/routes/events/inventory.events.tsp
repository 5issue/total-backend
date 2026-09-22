import "../../models/events.dto.tsp";

namespace WmsService.Events;

// HTTP 라우트가 없는 비동기(RabbitMQ) 이벤트 정의라 @route/@get/@post는 안 쓴다. 대신
// producer/consumer/topic/exchange/queue/dataFormat/payloadType을 리터럴 타입으로 박아서
// "타입이자 값"으로 쓴다 — OpenAPI로 컴파일되면 { type: string, enum: [값] }이 되고,
// scripts/sync-notion-events.js가 그 enum[0]을 읽어 메타데이터로 파싱한다.
// producer/consumer가 둘 이상이면 콤마로 구분한 문자열 하나로 둔다 (예: "product,wms").
// (REST 인터페이스의 @route와 같은 자리에 오는 것이 이 리터럴 필드들이라고 보면 된다.)

@doc("""
  결제가 완료된 주문(order.inventory.confirm)을 받아 WMS가 FEFO 기준으로 재고를 할당하고
  출고 지시(OutboundOrder)를 생성한다. 기존 REST `POST /internal/v1/wms/outbounds/orders`
  (OMS 주문 수신 및 FEFO 재고 할당)를 대체한다.

  이 토픽은 product-service도 이미 구독 중이다(재고 확정/차감). WMS는 새 구독자로 추가된다.

  ⚠️ payload(`OrderEvent`)의 `warehouseId`/`recipientInfo`, `OrderEventItem`의
  `orderItemId`/`productName`은 출고 지시 생성까지 커버하도록 새로 제안하는 필드다 —
  별도 이벤트(`order.event.paid` 등)를 새로 만들지 않고 이 계약 하나를 확장하는 쪽을 택했다
  (재고 확정과 출고 전표 생성이 서로 다른 이벤트에 걸치면 한쪽만 실패했을 때 정합성이
  깨지기 쉽다). `models/events.dto.tsp`의 `OrderEvent` 참고 — 아직 order-service의 실제
  클래스에는 반영 전이라 실 연동 전 확인이 필요하다.
  """)
model OrderInventoryConfirmEvent {
  title: "결제 완료 후 재고 확정(confirm)";
  producer: "order";
  consumer: "product,wms";
  topic: "order.inventory.confirm";
  exchange: "order.topic.exchange";
  queue: "wms.inventory.confirm.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.order.domain.order.OrderEvent";
  payload: OrderEvent;
}

@doc("""
  결제까지 끝난 주문이 이후 취소되어(order.canceled.inventory-restore) WMS가 이전에
  할당/적치했던 재고를 복구한다. 기존 REST `POST /internal/v1/wms/inventories/restore`
  (주문 취소로 인한 재고 복구)를 대체한다.

  주의: product-service의 `product.inventory.restore-routing-key` 기본값은
  `order.inventory.restore`인데, order-service가 실제로 발행하는 취소 이벤트의 routingKey는
  `order.canceled.inventory-restore`다(PaymentCancellationEvent). 이 스펙은 order-service가
  실제로 발행 중인 값을 기준으로 했다 — product-service 쪽이 이 토픽을 못 받고 있을 가능성이
  있으니 별도로 확인이 필요하다.
  """)
model OrderInventoryRestoreEvent {
  title: "주문 취소로 인한 재고 복구(restore)";
  producer: "order";
  consumer: "wms";
  topic: "order.canceled.inventory-restore";
  exchange: "order.topic.exchange";
  queue: "wms.inventory.restore.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.order.domain.order.OrderEvent";
  payload: OrderEvent;
}
