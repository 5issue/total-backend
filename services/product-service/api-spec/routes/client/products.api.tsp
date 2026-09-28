import "@typespec/http";
import "../../common/response.tsp";
import "../../models/products.dto.tsp";

using TypeSpec.Http;

namespace ProductService.Products;

@tag("Products")
@route("/api/v1/products")
@useAuth(NoAuth)
interface ProductsClient {
  @summary("홈 화면 추천 섹션 조회")
  @doc("퀵메뉴/추천 상품 등 홈 화면에 노출할 섹션 목록을 조회한다.")
  @get
  @route("/home-recommendations")
  getHomeRecommendations(): ProductService.Common.ApiResponse<HomeResponse> | ProductService.Common.ErrorResponse;

  @summary("상품 목록 조회")
  @doc("""
    카테고리 또는 키워드 기준으로 상품을 무한 스크롤(Slice) 조회한다. keyword는 상품명 또는
    브랜드명 어디에 있든(substring) 매칭한다. categoryId/keyword 중 최소 하나는 필요하다
    (둘 다 없으면 400 INVALID_INPUT_VALUE). sort 기본값은 RECOMMENDED, page 기본값 0,
    size 기본값 20이다.
    """)
  @get
  list(
    @query categoryId?: int64,
    @query keyword?: string,
    @query sort?: ProductSortType,
    @query brand?: string,
    @query price?: PriceBand,
    @query storageType?: StorageType,
    @query page?: int32,
    @query size?: int32
  ): ProductService.Common.ApiResponse<SliceResponse<ProductSummaryResponse>> | ProductService.Common.ErrorResponse;

  @summary("검색어 자동완성")
  @doc("""
    상품명 원문이 아니라 `search_keyword` 테이블에 큐레이션된 정제 키워드(품목/속성/브랜드명)를
    대상으로 한다 — "전용목장우유 900mL"/"전용목장우유 1.8L"처럼 용량만 다른 상품이 중복
    제안되지 않는다. Redis ZSET(ZRANGEBYLEX) 기반 substring 매칭 — 키워드 맨 앞뿐 아니라
    "무항생제 우유"의 "우유"처럼 중간에 붙은 부분도 매칭된다. Redis 캐시 미스 시 DB(ILIKE
    substring) 조회로 폴백한다. keyword는 필수이며 비어있으면 빈 배열을 반환한다. size 기본값은
    10이다.
    """)
  @get
  @route("/autocomplete")
  autocomplete(@query keyword: string, @query size?: int32): ProductService.Common.ApiResponse<ProductAutocompleteResponse> | ProductService.Common.ErrorResponse;

  @summary("검색/목록 필터 옵션 조회")
  @doc("""
    categoryId 또는 keyword 중 정확히 하나만 있어야 한다(둘 다 없거나 둘 다 있으면 400
    INVALID_INPUT_VALUE). keyword는 `list`와 동일하게 상품명 또는 브랜드명을 substring으로
    매칭한다.
    """)
  @get
  @route("/filters")
  getFilters(@query categoryId?: int64, @query keyword?: string): ProductService.Common.ApiResponse<ProductFilterResponse> | ProductService.Common.ErrorResponse;

  @summary("상품 상세 조회")
  @doc("단일 상품(UNIT)의 상세 정보를 조회한다.")
  @get
  @route("/{productId}")
  getDetail(@path productId: int64): ProductService.Common.ApiResponse<ProductDetailResponse> | ProductService.Common.ErrorResponse;
}
