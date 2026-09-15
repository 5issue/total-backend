import "@typespec/http";
import "../../common/response.tsp";
import "../../models/products.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Products;

@tag("Products")
@route("/internal/v1/wms/products")
interface Products {
  @summary("WMS 상품 스냅샷 동기화")
  @doc("""
    상품 서비스의 SKU 규격을 WmsProduct로 동기화한다.
    productId 기준 upsert이며, 이미 존재하면 skuCode/name을 갱신한다.
    """)
  @post
  @route("/sync")
  sync(@body body: WmsProductSyncRequest): WmsService.Common.ApiResponse<WmsProductSyncResult> | WmsService.Common.ErrorResponse;
}
