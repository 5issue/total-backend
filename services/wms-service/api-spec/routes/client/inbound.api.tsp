import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inbound;

@tag("Inbound")
@route("/api/v1/wms/inbounds")
interface InboundClient {
  @summary("입고 상세 검수 처리")
  @doc("현장(PDA)에서 입고 상세 1건의 검수 결과를 기록한다. totalBaseQuantity는 서버가 상품 환산 계수로 계산한다.")
  @post
  @route("/{inboundItemId}/inspect")
  inspect(@path inboundItemId: int64, @body body: InspectItemRequest): WmsService.Common.ApiResponse<InboundItemResponse> | WmsService.Common.ErrorResponse;

  @summary("적치(Put-away) 가이드 추천 조회")
  @doc("검수 완료된 입고 상세에 대해 적치할 로케이션을 추천한다.")
  @get
  @route("/put-away/recommendation")
  getPutAwayRecommendation(@query inboundItemId: int64): WmsService.Common.ApiResponse<PutAwayRecommendationResult> | WmsService.Common.ErrorResponse;

  @summary("적치 확정 및 재고 증가 트랜잭션")
  @doc("작업자가 실제로 적치를 완료한 로케이션을 확정하고, 해당 로케이션의 재고를 증가시킨다.")
  @post
  @route("/put-away/confirm")
  confirmPutAway(@body body: PutAwayConfirmRequest): WmsService.Common.ApiResponse<InboundItemResponse> | WmsService.Common.ErrorResponse;
}
