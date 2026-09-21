import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.Inventory;

// MovementType/MovementUnit/MovementStatus/StockMovement*Request/Response는
// models/stock-movements.dto.tsp(WmsService.StockMovement)로 옮겼다 — 조회/확정 API가
// stock-movements.api.tsp로 독립하면서 여기 있던 사본과 중복되고 있었다.

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
// 참고: 자동 로케이션 배정(동적 우선순위 방식)은 [InspectItemRequest]에서 1차 구현했다.
// 상품별 지정 로케이션(고정 슬롯) 방식과의 하이브리드 여부는 여전히 docs/erd-spec.md
// 미결 사항 4번으로 남아 있다.

model LocationCreateItem {
  locationType: LocationType;
  storageType: WmsService.Common.StorageType;
  zone: WmsService.Common.Zone;
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
  zone: WmsService.Common.Zone;
  aisle: string;
  rack: string;
  level: int32;
  bin: string;
  status: LocationStatus;
}

@doc("전달된 필드만 갱신한다. 상태 변경(ACTIVE/LOCKED)도 이 요청으로 처리한다.")
model LocationUpdateRequest {
  status?: LocationStatus;
  zone?: WmsService.Common.Zone;
  aisle?: string;
  rack?: string;
  level?: int32;
  bin?: string;
}

// ── client: 재고 조회 (요약/상세 2종) ──────────────────────────────────────
// 참고: docs/erd-spec.md "논의 필요 사항"에 재고 집계 관련 미결 사항이 남아 있다.
// 아래 조회 계약은 현재 Inventory 엔티티 구조를 반영한 예시이며, 정책 확정 후 조정될 수 있다.
//
// 조회 관점이 서로 달라(상품 중심 합산 vs 로케이션/LOT 중심 나열) 응답 하나에 억지로
// 합치지 않고 엔드포인트를 둘로 나눴다:
//   - GET /api/v1/wms/inventories/summary — 상품별 총량 집계(SCM/상품 기획/타 서비스 연동용)
//   - GET /api/v1/wms/inventories          — 로케이션/LOT별 상세 나열(PDA/창고관리/실사용)
// 둘 다 페이지네이션 응답이라 WmsService.Common.PageResponse<T>로 감싼다(이 프로젝트 첫
// 페이지네이션 엔드포인트).

@doc("""
  상품 1건에 대한 합산 가용 재고. warehouseId를 지정하면 그 창고 안에서만 집계하고,
  생략하면 전국 전체 창고를 통합 집계한다 — 이때 응답의 warehouseId는 null이다.
  """)
model ProductInventorySummaryResponse {
  productId: int64;

  @doc("조회 시 warehouseId를 지정하지 않았으면(전국 통합 집계) null이다.")
  warehouseId: int64 | null;

  totalQuantity: int64;
  totalReservedQuantity: int64;
  totalAvailableQuantity: int64;
}

@doc("로케이션/LOT 1건의 실물 재고 상세.")
model InventoryDetailResponse {
  inventoryId: int64;
  warehouseId: int64;
  locationId: int64;

  @doc("""
    사람이 읽기 좋은 로케이션 표시 코드(예: `P-01-01`). Location 엔티티가 aisle/rack/level/bin을
    개별 컬럼으로 갖고 있을 뿐 이 형태의 코드 컬럼이 아직 없다 — 구현 시 그 값들을 조합해
    생성하거나, Location에 코드 컬럼을 추가할지 결정이 필요하다.
    """)
  locationCode: string;

  zone: WmsService.Common.Zone;
  productId: int64;
  lotNo: string | null;
  expiredDate: plainDate | null;
  lpnCode: string | null;
  quantity: int32;
  reservedQuantity: int32;
  availableQuantity: int32;
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

// 참고: "주문 취소로 인한 재고 복구"는 예전에 REST(POST /internal/v1/wms/inventories/restore)였지만,
// 비동기 이벤트(order.canceled.inventory-restore)로 전환했다 — events/inventory.events.tsp 참고.
