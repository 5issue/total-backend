import "@typespec/http";
import "../../common/response.tsp";
import "../../models/stock-movements.dto.tsp";

using TypeSpec.Http;

namespace WmsService.StockMovement;

@tag("StockMovement")
@route("/api/v1/wms/stock-movements")
interface StockMovement {
  @summary("이동 작업 지시 생성 (보충/재배치)")
  @doc("피킹존 보충(REPLENISHMENT)이나 단순 재배치(RELOCATION) 등 로케이션 간 이동 작업 지시를 PENDING 상태로 생성한다. 적치(PUT_AWAY) 작업 지시는 검수(inspect) 시점에 서버가 자동으로 생성하므로 이 API로 만들지 않는다.")
  @post
  createStockMovement(@body body: StockMovementCreateRequest): WmsService.Common.ApiResponse<StockMovementResponse> | WmsService.Common.ErrorResponse;

  @summary("적치/보충/이동 작업 지시 목록 조회")
  @doc("창고/이동 유형/상태 조건으로 StockMovement 목록을 조회한다. PDA 작업자가 자신이 처리해야 할 대기 중인 작업(예: warehouseId + movementType=PUT_AWAY + status=PENDING)을 확인하는 용도로 쓴다. createdAt 오름차순(오래된 순, FIFO)으로 정렬한다. limit 기본값은 50, 최대 200이다.")
  @get
  listStockMovements(
    @query warehouseId?: int64,
    @query movementType?: MovementType,
    @query status?: MovementStatus,
    @query limit?: int32
  ): WmsService.Common.ApiResponse<StockMovementResponse[]> | WmsService.Common.ErrorResponse;

  @summary("재고 이동 작업 완료 확정")
  @doc("적치(PUT_AWAY)·보충(REPLENISHMENT)·재배치(RELOCATION) 작업 지시의 실물 이동을 완료 처리한다. PENDING 상태가 아니면 409(WMS4092)로 실패한다.")
  @post
  @route("/confirm")
  confirmStockMovement(@body body: StockMovementConfirmRequest): WmsService.Common.ApiResponse<StockMovementResponse> | WmsService.Common.ErrorResponse;
}
