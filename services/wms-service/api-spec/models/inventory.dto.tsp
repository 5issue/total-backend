import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.Inventory;

/** `StockMovement.MovementUnit`과 1:1로 대응한다. */
enum MovementUnit {
  PALLET,
  BOX,
  EA,
}

/** `StockMovement.MovementType`과 1:1로 대응한다. */
enum MovementType {
  PUT_AWAY,
  REPLENISHMENT,
  RELOCATION,
}

/** `StockMovement.MovementStatus`와 1:1로 대응한다. */
enum MovementStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELED,
}

/** `Location.LocationType`과 1:1로 대응한다. */
enum LocationType {
  PALLET_RACK,
  SHELF_BIN,
  BUFFER,
}

/** `Location.LocationStatus`와 1:1로 대응한다. */
enum LocationStatus {
  ACTIVE,
  LOCKED,
}

// ── client: 창고 구역/로케이션 마스터 ──────────────────────────────────────
// 참고: 로케이션 배정 전략(고정/동적)이 docs/erd-spec.md 미결 사항이라, 등록/수정 계약은
// 현재 Location 엔티티 컬럼을 그대로 노출하는 최소 형태로 정의한다.

model LocationCreateItem {
  locationType: LocationType;
  storageType: WmsService.Common.StorageType;
  zone: string | null;
  aisle: string;
  rack: string;
  level: int32;
  bin: string;
}

@doc("창고 하나에 구역(zone)별 로케이션을 여러 건 한 번에 등록한다.")
model ZoneLocationsCreateRequest {
  @minItems(1)
  locations: LocationCreateItem[];
}

model LocationResponse {
  id: int64;
  warehouseId: int64;
  locationType: LocationType;
  storageType: WmsService.Common.StorageType;
  zone: string | null;
  aisle: string;
  rack: string;
  level: int32;
  bin: string;
  status: LocationStatus;
}

@doc("전달된 필드만 갱신한다. 상태 변경(ACTIVE/LOCKED)도 이 요청으로 처리한다.")
model LocationUpdateRequest {
  status?: LocationStatus;
  zone?: string;
  aisle?: string;
  rack?: string;
  level?: int32;
  bin?: string;
}

// ── client: 가용 재고 조회 ──────────────────────────────────────────
// 참고: docs/erd-spec.md "논의 필요 사항"에 재고 집계 관련 미결 사항이 남아 있다.
// 아래 조회 계약은 현재 Inventory 엔티티 구조를 반영한 예시이며, 정책 확정 후 조정될 수 있다.

model InventoryLotResponse {
  id: int64;
  warehouseId: int64;
  locationId: int64;
  lpnCode: string | null;
  lotNo: string | null;
  expiredDate: plainDate | null;
  quantity: int32;
  reservedQuantity: int32;
  availableQuantity: int32;
}

@doc("상품 1개에 대한 창고 전체 합산 가용 재고. 로케이션/LOT별 상세는 lots에 담는다.")
model ProductInventorySummaryResponse {
  productId: int64;
  totalQuantity: int32;
  totalReservedQuantity: int32;
  totalAvailableQuantity: int32;
  lots: InventoryLotResponse[];
}

// ── client: 재고 선점(Soft-alloc) ──────────────────────────────────────
// 참고: 이 시점에는 아직 WMS에 OutboundOrder가 없을 수 있어(주문 접수 시점),
// outboundOrderId 대신 호출자가 넘기는 referenceId로 선점 건을 식별한다.
// 이후 order.inventory.confirm 이벤트(events/inventory.events.tsp)에서 FEFO 하드 할당으로
// 전환되고, 주문이 취소되면 order.canceled.inventory-restore 이벤트로 복구한다.

model StockAllocationItemRequest {
  productId: int64;

  @minValue(1)
  quantity: int32;
}

model StockAllocationRequest {
  warehouseId: int64;

  @doc("주문/장바구니 등 이 선점 건을 식별하는 호출측 참조 키. restore 호출 시 그대로 사용한다.")
  referenceId: string;

  @minItems(1)
  items: StockAllocationItemRequest[];
}

model StockAllocationItemResult {
  productId: int64;
  requestedQuantity: int32;
  allocatedQuantity: int32;
}

model StockAllocationResponse {
  referenceId: string;

  @doc("요청한 품목 전부에 대해 가용 재고를 확보했는지 여부. false면 일부/전부 결품.")
  fullyAllocated: boolean;

  items: StockAllocationItemResult[];
}

// ── client: 내부 이동 지시 생성(보충) ──────────────────────────────────────

model StockMovementCreateRequest {
  warehouseId: int64;
  productId: int64;
  fromLocationId: int64;
  toLocationId: int64;
  movementUnit: MovementUnit;
  movementType: MovementType;

  @minValue(1)
  unitQuantity: int32;
}

model StockMovementResponse {
  id: int64;
  warehouseId: int64;
  productId: int64;
  fromLocationId: int64;
  toLocationId: int64;
  movementUnit: MovementUnit;
  movementType: MovementType;
  status: MovementStatus;
}

// 참고: "주문 취소로 인한 재고 복구"는 예전에 REST(POST /internal/v1/wms/inventories/restore)였지만,
// 비동기 이벤트(order.canceled.inventory-restore)로 전환했다 — events/inventory.events.tsp 참고.
