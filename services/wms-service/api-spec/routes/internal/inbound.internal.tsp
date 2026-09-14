import "@typespec/http";
import "../../common/response.tsp";
import "../../models/inbound.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Inbound;

@tag("Inbound")
@route("/internal/v1/wms/inbounds")
interface InboundInternal {
  @summary("입고 예정(ASN) 생성")
  @doc("SCM의 발주(PO) 기준으로 입고 예정(ASN)을 생성한다. 생성 시 상태는 EXPECTED로 시작한다.")
  @post
  @route("/asn")
  createAsn(@body body: InboundAsnCreateRequest): WmsService.Common.ApiResponse<InboundOrderResponse> | WmsService.Common.ErrorResponse;
}
