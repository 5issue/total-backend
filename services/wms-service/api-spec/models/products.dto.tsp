import "../common/response.tsp";

namespace WmsService.Products;

@doc("상품 서비스로부터 전달받는 WMS용 상품 스냅샷(WmsProduct) 동기화 항목.")
model WmsProductSyncItem {
  @doc("상품 서비스 Product UNIT ID. WmsProduct.id로 그대로 사용된다 (docs/erd-spec.md 1번).")
  productId: int64;

  @doc("고유 상품 코드.")
  skuCode: string;

  name: string;
}

model WmsProductSyncRequest {
  items: WmsProductSyncItem[];
}

model WmsProductSyncResult {
  @doc("이번 요청으로 생성 또는 갱신(upsert)된 상품 수.")
  syncedCount: int32;

  @doc("productId 형식은 맞지만 처리에 실패한 항목. 배열이 비어 있으면 전량 성공.")
  failedItems: WmsService.Common.BatchItemError[];
}
