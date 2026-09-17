import "./products.dto.tsp";

namespace ProductService.Internal;

@doc("한 번에 조회할 수 있는 최대 개수는 100개. 초과 시 400(INVALID_INPUT_VALUE, \"한 번에 조회할 수 있는 상품은 최대 100개입니다.\").")
model BatchProductSummaryRequest {
  productIds: int64[];
}

model InventoryInfo {
  availableQuantity: int32;

  @doc("JSON 키는 `isSoldOut`(서버가 `@JsonProperty`로 명시).")
  isSoldOut: boolean;

  @doc("1회 주문 최대 수량. 현재 서버 코드에 10으로 하드코딩되어 있다.")
  maxQuantityPerOrder: int32;
}

@doc("""
  `seller` 필드는 이름과 달리 실제로는 상품의 brand 값을 그대로 담는다(서버 코드가 brand를
  seller 파라미터 자리에 그대로 전달한다) — 혼동하지 않도록 실제 동작 그대로 문서화했다.
  """)
model ProductSummaryItem {
  productId: int64;
  name: string;
  salePrice: int64;
  thumbnailUrl: string | null;
  storageType: ProductService.Products.StorageType | null;
  status: ProductService.Products.ProductStatus | null;
  seller: string;
  inventory: InventoryInfo;
}

@doc("요청한 productIds 중 UNIT 타입 상품으로 확인되지 않은 항목은 결과에서 조용히 제외된다 — 응답 개수가 요청보다 적을 수 있다.")
model BatchProductSummaryResponse {
  products: ProductSummaryItem[];
}
