import "../../models/events.dto.tsp";

namespace WmsService.Events;

// inbound.events.tsp와 같은 방향(wms가 발행, 다른 서비스가 소비)이지만 "출고" 도메인이라 별도
// 파일로 분리했다.

@doc("""
  출고 완료(피킹 완료 후 실물 재고 차감) 시점에 WMS가 발행하는 이벤트. order-service가 주문의
  배송 준비 완료(출고 확정)를 인지하는 데 쓸 것을 염두에 두고 설계했다
  (`OutboundOrderService.completeShipment()`).

  주문 상세(orderItemId) 단위 매칭은 아직 지원하지 않는다 — `OutboundCompletedEventPayload`
  참고. order-service가 라인별 매칭이 필요하면 `OrderEvent.OrderEventItem.orderItemId`
  제안(inventory.events.tsp 참고)이 먼저 확정돼야 한다.

  ⚠️ 계약만 우선 정의한 상태다 — order-service 쪽 소비자가 아직 구현되지 않았다. 실제 연동 전에
  컨슈머 팀과 페이로드를 다시 확인해야 한다.
  """)
model OutboundCompletedEvent {
  title: "출고 완료(실물 재고 차감)";
  producer: "wms";
  consumer: "order";
  topic: "wms.outbound.completed";
  exchange: "wms.topic.exchange";
  queue: "order.outbound.completed.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.wms.infrastructure.messaging.dto.OutboundCompletedEvent";
  payload: OutboundCompletedEventPayload;
}
