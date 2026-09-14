import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inventory.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inventory;

// 참고: 재고 선점(Soft-alloc, /api/v1/wms/inventories/allocation)은 client 쪽 계약이고,
// 여기서는 그 선점을 취소/복구하는 케이스만 다룬다. FEFO 하드 할당은
// routes/internal/outbound.internal.tsp의 주문 수신 시점에 이뤄진다.

@tag("Inventory")
@route("/internal/v1/wms/inventories")
interface InventoryInternal {
  @summary("주문 취소로 인한 재고 복구")
  @doc("결제 실패, 주문 취소 등으로 이전에 선점(allocate)했던 재고를 복구한다.")
  @post
  @route("/restore")
  restore(@body body: StockRestoreRequest): WmsService.Common.ApiResponse<StockRestoreResponse> | WmsService.Common.ErrorResponse;
}
