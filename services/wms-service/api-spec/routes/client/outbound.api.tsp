import "@typespec/http";
import "../../common/response.tsp";
import "../../models/outbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Outbound;

@tag("Outbound")
@route("/api/v1/wms/outbounds")
interface OutboundClient {
  // 참고: 피킹 Task 조회/시작/완료는 이제 별도 Task API로 다룬다 — WmsService.Task 참고(routes/client/tasks.api.tsp).

  // TODO: 포장재/보냉재 추천 알고리즘, TMS 연동 방식이 확정 전이라 아래 두 엔드포인트는 구조만 우선 정의한다.

  @summary("[placeholder] 지능형 포장재/보냉재 추천")
  @post
  @route("/packing/recommendation")
  getPackingRecommendation(@body body: PackingRecommendationRequest): WmsService.Common.ApiResponse<PackingRecommendationResponse> | WmsService.Common.ErrorResponse;

  @summary("[placeholder] 패킹 검수 완료 및 송장 발행")
  @doc("TMS(운송관리시스템) 연동으로 송장을 발급한다.")
  @post
  @route("/shipping/confirm")
  confirmShipping(@body body: ShippingConfirmRequest): WmsService.Common.ApiResponse<ShippingInvoiceResponse> | WmsService.Common.ErrorResponse;
}
