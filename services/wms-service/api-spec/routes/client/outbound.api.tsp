import "@typespec/http";
import "../../common/response.tsp";
import "../../models/outbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Outbound;

@tag("Outbound")
@route("/api/v1/wms/outbounds")
interface OutboundClient {
  @summary("작업자별 피킹 Task 조회")
  @doc("워커/창고 기준으로 피킹 대기 중이거나 할당된 Task 목록을 조회한다.")
  @get
  @route("/picking-tasks")
  getPickingTasks(@query workerId?: int64, @query warehouseId?: int64): WmsService.Common.ApiResponse<PickingTaskResponse[]> | WmsService.Common.ErrorResponse;

  @summary("작업자에게 피킹 Task 할당")
  @doc("대기 중인 피킹 Task 중 하나를 작업자에게 배정한다.")
  @post
  @route("/picking-tasks")
  assignPickingTask(@body body: PickingTaskAssignRequest): WmsService.Common.ApiResponse<PickingTaskResponse> | WmsService.Common.ErrorResponse;

  @summary("피킹 결과 반영")
  @doc("품목 1건의 실제 피킹 수량을 기록한다. orderedQuantity보다 적으면 SHORTAGE로 처리된다.")
  @post
  @route("/{outboundOrderId}/items/{outboundItemId}/pick")
  pickItem(@path outboundOrderId: int64, @path outboundItemId: int64, @body body: PickItemRequest): WmsService.Common.ApiResponse<OutboundItemResponse> | WmsService.Common.ErrorResponse;

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
