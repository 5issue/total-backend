import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.Outbound;

/** `OutboundItem.OutboundItemStatus`와 1:1로 대응한다. */
enum OutboundItemStatus {
  @doc("피킹존은 물론 보관존에서도 예약조차 못한 상태. StockMovement가 전혀 없다. 입고 적치(PUT_AWAY)로 보관존 재고가 늘면 재시도 대상이며, 장시간 지속되면 FAILED로 전환된다.")
  UNALLOCATED,

  @doc("보관존 재고 예약 + 보충 지시(StockMovement, REPLENISHMENT)까지는 생성됨. 물리 이동 완료만 기다리면 된다. location/lotNo/expiredDate가 아직 null이다.")
  PENDING_REPLENISHMENT,

  @doc("피킹존 재고(location, lotNo)에 매핑되고 reserved_quantity 증가까지 끝난 상태.")
  ALLOCATED,
  PICKED,
  SHORTAGE,

  @doc("장시간 UNALLOCATED으로 남아 있어 최종 실패 처리됨(배치/스케줄러가 전환).")
  FAILED,
}

/** `OutboundOrder.OutboundOrderStatus`와 1:1로 대응한다. */
enum OutboundOrderStatus {
  PENDING_REPLENISHMENT,
  ALLOCATED,
  PICKING,
  PACKING,
  COMPLETED,
  CANCELED,

  @doc("품목 하나라도 장시간 재고를 확보하지 못해 최종 실패 처리됨(배치/스케줄러가 전환).")
  FAILED,
}

// 참고: 예전에는 여기 "OMS 주문 수신 + FEFO 재고 할당"을 REST(POST /internal/v1/wms/outbounds/orders)로
// 받았지만, 비동기 이벤트(order.inventory.confirm)로 전환했다 — events/inventory.events.tsp 참고.
// 출고 지시(OutboundOrder) 생성과 FEFO 하드 할당은 이제 그 이벤트 컨슈머 안에서 일어난다.

// 참고: 피킹은 이제 별도 Task 엔티티/API로 다룬다 — models/tasks.dto.tsp, routes/client/tasks.api.tsp 참고.

// ── client: 출고 완료 ──────────────────────────────────────────

model OutboundOrderResponse {
  id: int64;
  orderId: int64;
  warehouseId: int64;
  status: OutboundOrderStatus;
  createdAt: utcDateTime;
}

@doc("""
  포장 대기(PACKING) 중인 전표의 출고를 최종 확정한다. PACKING 상태가 아니면
  409(WMS4098)로 실패한다.
  """)
model OutboundCompleteRequest {
  outboundOrderId: int64;
}

// ── client: 패킹/송장 ──────────────────────────────────────────
// TODO: 포장재/보냉재 추천 알고리즘, TMS 연동 방식(송장 발급 API 계약 등)이 아직 정해지지 않았다.
// 아래 모델은 화면/계약 형태를 미리 잡아두기 위한 최소 구조이며, 요구사항 확정 후 필드를 채운다.

model PackingRecommendationRequest {
  outboundOrderId: int64;
}

@doc("[placeholder] 출고 지시 품목의 부피/무게, 보관 유형(냉장/냉동)을 기준으로 포장재·보냉재를 추천한다.")
model PackingRecommendationResponse {
  outboundOrderId: int64;

  @doc("추천 박스 규격 코드. 포장재 마스터/알고리즘 확정 전이라 자유 문자열로 둔다.")
  recommendedBoxCode: string;

  @doc("냉장/냉동 품목이 포함되어 보냉재가 필요한지 여부.")
  requiresCoolant: boolean;

  @doc("보냉재 종류 (예: 아이스팩, 드라이아이스). 불필요하면 null.")
  coolantType: string | null;
}

model ShippingConfirmRequest {
  outboundOrderId: int64;
}

@doc("[placeholder] 패킹 검수 완료 후 TMS(운송관리시스템) 연동으로 발급되는 배송 송장 정보.")
model ShippingInvoiceResponse {
  outboundOrderId: int64;
  carrier: string;
  trackingNumber: string;
}
