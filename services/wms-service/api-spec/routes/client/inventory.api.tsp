import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inventory.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inventory;

@tag("Inventory")
@route("/api/v1/wms/inventories")
interface InventoryClient {
  @summary("실시간 가용 재고 조회")
  @doc("상품 ID 기준으로 로케이션·LOT별 재고와, 창고 전체 합산 가용 재고를 함께 조회한다.")
  @get
  getByProduct(@query productId: int64, @query warehouseId?: int64): WmsService.Common.ApiResponse<ProductInventorySummaryResponse> | WmsService.Common.ErrorResponse;

  @summary("주문 접수 시 재고 선점 (Soft-alloc)")
  @doc("주문 접수 시점에 가용 재고를 잠정 선점한다. OutboundOrder 생성 전이라 referenceId로 선점 건을 식별한다.")
  @post
  @route("/allocation")
  allocate(@body body: StockAllocationRequest): WmsService.Common.ApiResponse<StockAllocationResponse> | WmsService.Common.ErrorResponse;

  @summary("내부 이동 지시 생성 (보충 작업)")
  @doc("피킹존 보충(REPLENISHMENT)이나 단순 재배치(RELOCATION) 등 로케이션 간 이동 작업 지시를 생성한다.")
  @post
  @route("/movement")
  createMovement(@body body: StockMovementCreateRequest): WmsService.Common.ApiResponse<StockMovementResponse> | WmsService.Common.ErrorResponse;
}

// 창고 구역/로케이션 마스터 관리. base path가 /api/v1/wms/inventories가 아니라
// /api/v1/wms/warehouses, /api/v1/wms/locations라 별도 interface로 둔다.
@tag("Warehouse")
interface Locations {
  @summary("창고 구역 및 로케이션 등록")
  @doc("창고 하나에 구역(zone)별 로케이션을 여러 건 한 번에 등록한다.")
  @post
  @route("/api/v1/wms/warehouses/{warehouseId}/zones")
  registerZoneLocations(@path warehouseId: int64, @body body: ZoneLocationsCreateRequest): WmsService.Common.ApiResponse<LocationResponse[]> | WmsService.Common.ErrorResponse;

  @summary("로케이션 단건 수정 및 상태 변경")
  @doc("전달된 필드만 갱신한다. status를 LOCKED/ACTIVE로 바꿔 점검·실사 상태를 표현할 수 있다.")
  @patch
  @route("/api/v1/wms/locations/{locationId}")
  updateLocation(@path locationId: int64, @body body: LocationUpdateRequest): WmsService.Common.ApiResponse<LocationResponse> | WmsService.Common.ErrorResponse;
}
