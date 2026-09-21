import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.StockMovement;

/** `StockMovement.MovementType`과 1:1로 대응한다. */
enum MovementType {
  PUT_AWAY,
  REPLENISHMENT,
  RELOCATION,
}

/** `StockMovement.MovementUnit`과 1:1로 대응한다. */
enum MovementUnit {
  PALLET,
  BOX,
  EA,
}

/** `StockMovement.MovementStatus`와 1:1로 대응한다. */
enum MovementStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELED,
}

@doc("피킹존 보충(REPLENISHMENT)이나 단순 재배치(RELOCATION) 등 로케이션 간 이동 작업 지시를 생성한다.")
model StockMovementCreateRequest {
  warehouseId: int64;
  inventoryId: int64;
  productId: int64;
  fromLocationId: int64;
  targetLocationId: int64;
  movementUnit: MovementUnit;
  movementType: MovementType;

  @minValue(1)
  unitQuantity: int32;
}

model StockMovementResponse {
  id: int64;
  warehouseId: int64;
  productId: int64;

  @doc("PUT_AWAY가 입고 검수 건에서 비롯된 경우에만 채워진다. REPLENISHMENT/RELOCATION은 null.")
  inboundItemId: int64 | null;

  lotNo: string | null;
  expiredDate: plainDate | null;

  fromLocationId: int64 | null;
  toLocationId: int64 | null;
  movementUnit: MovementUnit;
  unitQuantity: int32;
  quantity: int32;
  movementType: MovementType;
  status: MovementStatus;
  createdAt: utcDateTime;
  completedAt: utcDateTime | null;
}

@doc("""
  재고 이동 작업 지시(적치/보충/재배치)의 실물 이동을 완료 처리한다. movementType이
  PUT_AWAY면 대응하는 InboundItem도 PUT_AWAY 상태로 전환한다.

  stockMovementId로 작업 지시를 직접 특정한다 — inboundItemId 등으로 받으면 "이 건의
  PENDING 작업 지시"를 다시 조회해야 하는 간접 조회가 생기므로, 작업 지시 자체의 식별자를
  그대로 넘기는 편이 조회 조건도 단순해지고 애매함도 없다(예: 검수(inspect) 응답의
  stockMovementId). 이미 COMPLETED/CANCELED인 작업 지시를 다시 확정하려 하면
  409(WMS4092)로 실패한다.
  """)
model StockMovementConfirmRequest {
  @doc("확정할 작업 지시의 id. 아직 PENDING 상태여야 한다.")
  stockMovementId: int64;

  @doc("작업자가 실제로 이동을 완료한 로케이션. 원래 배정과 다를 수 있다.")
  targetLocationId: int64;
}
