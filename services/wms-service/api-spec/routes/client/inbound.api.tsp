import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inbound;

@tag("Inbound")
@route("/api/v1/wms/inbounds")
interface InboundClient {
  @summary("입고 상세 검수 처리")
  @doc("현장(PDA)에서 입고 상세 1건의 검수 결과를 기록한다. totalBaseQuantity는 서버가 상품 환산 계수로 계산하고, 버퍼 로케이션 재고를 함께 증가시키며, 적치 작업 지시(StockMovement)를 PENDING으로 생성한다. 응답의 stockMovementId를 put-away/confirm 호출 시 그대로 사용한다.")
  @post
  @route("/inspect")
  inspect(@body body: InspectItemRequest): WmsService.Common.ApiResponse<InboundItemResponse> | WmsService.Common.ErrorResponse;

  @summary("적치(Put-away) 로케이션 재추천")
  @doc("대기 중인 적치 작업 지시의 목표 로케이션을 다시 추천하고 그 결과로 재배정한다(상태를 바꾸는 작업이라 POST). 원래 배정된 로케이션에 문제가 있어(파손·오염 등 시스템에 반영 안 된 사유) 다른 곳으로 바꾸고 싶을 때 쓴다.")
  @post
  @route("/put-away/recommendation")
  recommendPutAway(@body body: PutAwayRecommendationRequest): WmsService.Common.ApiResponse<PutAwayRecommendationResult> | WmsService.Common.ErrorResponse;
}
