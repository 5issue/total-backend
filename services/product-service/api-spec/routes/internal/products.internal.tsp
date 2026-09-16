import "@typespec/http";
import "../../common/response.tsp";
import "../../models/internal.dto.tsp";

using TypeSpec.Http;

namespace ProductService.Internal;

@tag("Products")
@route("/internal/v1/products")
interface ProductsInternal {
  @summary("상품 배치 요약 조회")
  @doc("""
    주문/장바구니 등 다른 서비스가 여러 상품을 한 번에 조회할 때 쓴다. 최대 100개까지
    요청 가능(초과 시 400). UNIT 타입이 아닌 productId는 결과에서 조용히 제외된다.
    `@RequireRole(ADMIN)` — 관리자 권한이 있는 서비스 계정 토큰이 필요하다(단순 `@Authenticated`
    가 아님).
    """)
  @post
  @route("/batch-summary")
  batchSummary(@body body: BatchProductSummaryRequest): ProductService.Common.ApiResponse<BatchProductSummaryResponse> | ProductService.Common.ErrorResponse;
}
