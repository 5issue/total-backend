import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inventory.dto.tsp";

using TypeSpec.Http;

namespace ProductService.Inventory;

@doc("주문 접수/실패 시 order-service가 호출하는 내부 API. wms-service와 달리 내부용이라도 `@Authenticated`라 유효한 JWT가 필요하다(무인증 아님).")
@tag("Inventory")
@route("/internal/v1/products/inventory")
interface InventoryInternal {
  @summary("재고 HOLD")
  @doc("주문 접수 시점에 상품별 재고를 예약(HOLD)한다.")
  @post
  @route("/hold")
  hold(@body body: InventoryAdjustRequest): ProductService.Common.NoDataResponse | ProductService.Common.ErrorResponse;

  @summary("재고 HOLD 해제")
  @doc("""
    HOLD했던 예약을 취소한다. CONFIRM/RESTORE는 이 REST API가 아니라 비동기 이벤트로
    처리된다 — routes/events/inventory.events.tsp의 `order.inventory.release` 이벤트를 봐도
    같은 처리 경로다(order-service가 REST 대신 이벤트로도 이 흐름을 태울 수 있음).
    """)
  @post
  @route("/release")
  release(@body body: InventoryAdjustRequest): ProductService.Common.NoDataResponse | ProductService.Common.ErrorResponse;
}
