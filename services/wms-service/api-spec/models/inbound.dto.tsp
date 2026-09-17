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

  @doc("""
    검수(inspect) 완료 시 함께 생성된 적치 작업 지시(StockMovement)의 id. inspect 이전이거나
    이미 put-away/confirm까지 끝난 뒤에도 계속 채워져 있다(작업 지시 자체는 COMPLETED 상태로
    남을 뿐 사라지지 않음) — inspect 이전에만 null이다. PDA는 이 값을 그대로
    put-away/confirm 요청의 stockMovementId로 넘긴다.
    """)
  stockMovementId: int64 | null;
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

@doc("""
  입고 상세 1건의 검수 결과를 기록한다.
  - inboundUnit을 생략(null)하면 ASN 생성 시 단위를 그대로 쓰고, 값을 주면 그 값으로
    totalBaseQuantity(낱개 환산)를 다시 계산한다 — 원래 PALLET로 발주했는데 실제로는
    낱개 박스로 들어온 경우 등.
  - targetLocationId를 생략(null)하면 시스템이 보관(STORAGE) 로케이션을 자동 추천한다:
    같은 창고·같은 storageType의 PALLET_RACK 중, 재고가 전혀 없고 진행 중인 이동
    지시(StockMovement PENDING/IN_PROGRESS)로 선점되지 않은 곳을 aisle/rack/level/bin
    순으로 가장 앞선 1건 배정한다. 후보가 없으면 409(WMS409)로 실패한다. 상품별 고정
    슬롯 방식과의 하이브리드 여부는 아직 docs/erd-spec.md 미결 사항 4번으로 남아 있다.
  """)
model InspectItemRequest {
  inboundItemId: int64;

  @doc("실제 검수 합격 수량 (inboundUnit 기준, 낱개 아님).")
  @minValue(0)
  inspectQuantity: int32;

  inboundUnit: InboundUnit | null;

  @doc("검수 시 확인한 LOT 번호.")
  lotNo: string;

  @doc("검수 시 확인한 유통기한.")
  expiredDate: plainDate;

  targetLocationId: int64 | null;
}

@doc("""
  대기 중(PENDING)인 적치 작업 지시 하나를 stockMovementId로 특정해 재추천을 요청한다.
  inspect 응답(InboundItemResponse.stockMovementId)을 그대로 사용한다 — inboundItemId로
  받으면 confirmPutAway와 마찬가지로 "이 건의 PENDING PUT_AWAY 작업 지시"를 다시 조회해야
  하는 간접 조회가 생기므로 동일한 이유로 stockMovementId를 직접 받는다.
  """)
model PutAwayRecommendationRequest {
  stockMovementId: int64;
}

@doc("""
  대기 중인 적치 작업 지시에 보관(STORAGE) 로케이션을 다시 추천하고, 그 결과로
  InboundItem.targetLocation과 StockMovement.toLocation을 함께 재배정한다(단순 조회가
  아니라 상태를 변경하는 작업이라 POST다).

  기존에 배정돼 있던 로케이션은 후보에서 자동으로 빠진다 — 재추천 대상 자체가 그 로케이션을
  점유 중인 PENDING 작업 지시이므로, InspectItemRequest의 자동 추천과 동일한 조건(같은
  창고·같은 storageType의 PALLET_RACK 중 재고 없고 선점 안 된 곳, aisle/rack/level/bin
  순 최전방)으로 다시 뽑으면 항상 다른 로케이션이 나온다. 이미 COMPLETED/CANCELED된 작업
  지시에 대해 호출하면 409(WMS4092)로 실패하고, 재추천할 후보가 전혀 없으면 409(WMS409)로
  실패한다.
  """)
model PutAwayRecommendationResult {
  inboundItemId: int64;
  stockMovementId: int64;
  recommendedLocationId: int64;
  storageType: WmsService.Common.StorageType;

  @doc("추천 사유. 현재는 동선 최적화(aisle/rack/level/bin 오름차순 최전방) 전략 하나뿐이라 고정 문구다.")
  reason: string;

  status: WmsService.Common.TaskStatus;
}

@doc("""
  버퍼(임시 도크)에 있던 재고를 목표 로케이션으로 물리 이동 완료 처리한다. 재고 증가는
  검수(inspect) 시점에 이미 반영됐고, 여기서는 StockMovement 완료 + 재고 위치 이전만 수행한다.

  inboundItemId가 아니라 stockMovementId로 작업 지시를 특정한다 — inboundItemId만으로는
  "이 검수 건의 PENDING 상태 PUT_AWAY 작업 지시"를 다시 조회해야 하는데, 작업 지시 자체의
  식별자를 이미 inspect 응답(InboundItemResponse.stockMovementId)으로 쥐고 있으므로 그걸
  그대로 넘기는 편이 조회 조건도 단순해지고 애매함도 없다. 이미 COMPLETED/CANCELED인
  작업 지시를 다시 확정하려 하면 409(WMS409)로 실패한다.
  """)
model PutAwayConfirmRequest {
  @doc("inspect 응답의 stockMovementId. 검수 시점에 생성된, 아직 PENDING인 PUT_AWAY 작업 지시여야 한다.")
  stockMovementId: int64;

  @doc("작업자가 실제로 적치를 확정한 로케이션. 추천/원래 배정과 다를 수 있다.")
  targetLocationId: int64;
}
