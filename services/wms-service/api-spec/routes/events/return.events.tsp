import "../../models/events.dto.tsp";

namespace WmsService.Events;

// inbound.events.tsp(입고 도메인)와 나란한 "반품(return)" 도메인 이벤트 파일 — WMS에는
// 아직 반품 REST 리소스/엔티티가 전혀 없어(routes/client/return.api.tsp 같은 게 없음)
// 이벤트 계약이 이 도메인의 첫 결과물이다.

@doc("""
  반품 검수가 완료됐을 때 WMS가 발행하는 이벤트. 품목별로 검수 대상 수량과, 그중
  재입고(판매 가능 재고 복귀)한 수량/폐기한 수량을 나눠 담는다.
  """)
model ReturnInspectionCompletedEvent {
  title: "반품 검수 완료";
  producer: "wms";
  consumer: "order";
  topic: "wms.return.inspected";
  exchange: "wms.topic.exchange";
  queue: "order.return.inspected.queue";
  dataFormat: "JSON";
  payloadType: "com.kurly.wms.infrastructure.messaging.dto.ReturnInspectionCompletedEvent";
  payload: ReturnInspectionCompletedEventPayload;
}
