import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.Inbound;

/** `InboundItem.InboundUnit`과 1:1로 대응한다. */
enum InboundUnit {
  PALLET,
  CARTON,
  BOX,
}

/** `InboundOrder.InboundOrderStatus`와 1:1로 대응한다. */
enum InboundOrderStatus {
  EXPECTED,
  INSPECTING,
  COMPLETED,
  CANCELED,
}

/** `InboundItem.InboundItemStatus`와 1:1로 대응한다. */
enum InboundItemStatus {
  PENDING,
  INSPECTED,
  PUT_AWAY,
}

// ── internal: SCM -> WMS ASN 생성 ──────────────────────────────────────────

model InboundAsnItemRequest {
  productId: int64;
  inboundUnit: InboundUnit;

  @doc("발주/예정 수량 (inboundUnit 기준, 낱개 아님).")
  @minValue(1)
  orderedQuantity: int32;
}

model InboundAsnCreateRequest {
  warehouseId: int64;

  @doc("SCM 발주 번호. InboundOrder.po_number(V2 마이그레이션, 2026-09-15)로 반영되어 있다.")
  poNumber: string;

  supplierName: string;
  expectedDate: utcDateTime;

  @minItems(1)
  items: InboundAsnItemRequest[];
}

model InboundItemResponse {
  id: int64;
  productId: int64;
  inboundUnit: InboundUnit;
  orderedQuantity: int32;
  inspectQuantity: int32 | null;
  totalBaseQuantity: int32 | null;
  status: InboundItemStatus;
}

model InboundOrderResponse {
  id: int64;
  warehouseId: int64;
  poNumber: string;
  supplierName: string;
  status: InboundOrderStatus;
  expectedDate: utcDateTime;
  completedDate: utcDateTime | null;
  items: InboundItemResponse[];
}

// ── client: BO/PDA 검수 및 적치 ──────────────────────────────────────────
// 참고: 로케이션 배정 전략(고정/동적)이 docs/erd-spec.md 미결 사항이라, 추천 로직은 구조만 우선 정의한다.

model InspectItemRequest {
  @doc("실제 검수 합격 수량 (inboundUnit 기준). totalBaseQuantity(낱개 환산)는 서버가 계산한다.")
  @minValue(0)
  inspectQuantity: int32;
}

@doc("검수 완료된 입고 상세 1건에 대해 적치할 로케이션을 추천한다.")
model PutAwayRecommendationResult {
  inboundItemId: int64;
  recommendedLocationId: int64;
  storageType: WmsService.Common.StorageType;

  @doc("추천 사유 (예: '동일 상품 기존 보관 위치', '냉장존 빈 슬롯 중 최단 동선').")
  reason: string;

  status: WmsService.Common.TaskStatus;
}

@doc("재고 증가 트랜잭션을 함께 수행하므로 inboundItemId를 요청 본문에 명시한다.")
model PutAwayConfirmRequest {
  inboundItemId: int64;

  @doc("작업자가 실제로 적치를 확정한 로케이션. 추천과 다를 수 있다.")
  targetLocationId: int64;
}
