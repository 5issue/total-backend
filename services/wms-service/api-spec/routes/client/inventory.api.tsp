import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inventory.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inventory;

@tag("Inventory")
@route("/api/v1/wms/inventories")
interface InventoryClient {
  @summary("상품별 재고 요약 조회 (창고 통합/개별)")
  @doc("""
    여러 로케이션에 흩어진 동일 상품의 재고를 상품 단위로 합산해 보여준다. 본사 SCM
    관리자·상품 기획자·타 서비스 연동처럼 "이 상품이 총 몇 개 있는지"가 궁금한 경우에 쓴다.
    로케이션/LOT 단위 상세는 상세 조회(`GET /api/v1/wms/inventories`)를 쓴다.

    warehouseId를 생략하면 전국 전체 창고를 통합 집계한다(그 경우 응답의 warehouseId는 null).
    productId를 생략하면 창고(또는 전국) 안의 모든 상품을 페이지 단위로 나열한다.
    """)
  @get
  @route("/summary")
  getInventorySummary(
    @query warehouseId?: int64,
    @query productId?: int64,
    @query page?: int32,
    @query size?: int32,
    @query sort?: string
  ): WmsService.Common.ApiResponse<WmsService.Common.PageResponse<ProductInventorySummaryResponse>> | WmsService.Common.ErrorResponse;

  @summary("로케이션/LOT별 상세 재고 조회")
  @doc("""
    특정 상품이 어느 로케이션·구역(zone)·LOT/유통기한으로 나뉘어 보관 중인지 실물 단위로
    나열한다. 현장 작업자(PDA)·창고 관리자·재고 실사처럼 "실제로 어디에 얼마나 있는지"가
    궁금한 경우에 쓴다. 상품 단위 합계는 요약 조회(`GET /api/v1/wms/inventories/summary`)를 쓴다.
    """)
  @get
  listInventoryDetails(
    @query warehouseId?: int64,
    @query productId?: int64,
    @query zone?: WmsService.Common.Zone,
    @query locationId?: int64,
    @query lotNo?: string,
    @query page?: int32,
    @query size?: int32,
    @query sort?: string
  ): WmsService.Common.ApiResponse<WmsService.Common.PageResponse<InventoryDetailResponse>> | WmsService.Common.ErrorResponse;

  @summary("주문 접수 시 재고 선점 (Soft-alloc)")
  @doc("주문 접수 시점에 가용 재고를 잠정 선점한다. OutboundOrder 생성 전이라 referenceId로 선점 건을 식별한다.")
  @post
  @route("/allocation")
  allocate(@body body: StockAllocationRequest): WmsService.Common.ApiResponse<StockAllocationResponse> | WmsService.Common.ErrorResponse;
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
