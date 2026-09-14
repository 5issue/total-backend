import "@typespec/http";
import "../../common/response.tsp";
import "../../models/outbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Outbound;

// 참고: 원래 논의된 routes/internal 목록에는 없었지만, OMS가 출고 지시를 생성하는 흐름은
// 서비스 간 호출이라 internal로 분류했다.

@tag("Outbound")
@route("/internal/v1/wms/outbounds")
interface OutboundInternal {
  @summary("주문 수신 및 FEFO 재고 할당")
  @doc("OMS의 주문 확정 결과를 받아 출고 지시(OutboundOrder)를 생성하고, FEFO 기준으로 LOT/로케이션을 하드 할당한다.")
  @post
  @route("/orders")
  receiveOrder(@body body: OutboundOrderCreateRequest): WmsService.Common.ApiResponse<OutboundOrderResponse> | WmsService.Common.ErrorResponse;
}
