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

  @summary("AI 상품 ID로 상품 조회")
  @doc("""
    AI 서버의 `product_id`(BE `product.ai_product_id`)로 BE 상품을 조회한다. AI 추천/레시피
    응답의 상품을 BE 상품으로 바꿀 때 쓴다. `ai_product_ids`는 쉼표로 구분한 값
    (예: `?ai_product_ids=753,754`)이며 최대 100개, 중복은 한 번만 처리한다.

    - `ai_product_id`는 GROUP과 UNIT이 같은 값을 공유할 수 있다(원본에 UNIT 행이 없어 BE가
      합성한 UNIT은 부모 GROUP과 같은 값). 그래서 한 ai_product_id에 대해 GROUP·UNIT이 모두
      응답에 나올 수 있고, 구분은 각 항목의 `type`으로 한다.
    - 일치하는 상품이 없거나 HIDDEN 상품뿐인 ai_product_id는 오류가 아니라 결과에서 조용히 제외된다 —
      응답 개수가 요청보다 적을 수 있다. 어떤 값이 빠졌는지는 `notFoundAiProductIds`로 알려 준다.
    - `items`는 요청한 ai_product_ids 순서를 따르고, 같은 ai_product_id 안에서는 GROUP → UNIT
      순이다.
    - 공개 API다(인증 불필요). `HIDDEN` 상태 상품은 조회되지 않는다 — SALE/SOLDOUT만 나온다.
    - 값이 비었거나 숫자가 아닌 항목이 있거나 100개를 넘으면 400 INVALID_INPUT_VALUE.
    """)
  @get
  @route("/by-ai")
  getByAiProductIds(
    @query(#{ name: "ai_product_ids", explode: false }) aiProductIds: int64[]
  ): ProductService.Common.ApiResponse<ProductsByAiResponse> | ProductService.Common.ErrorResponse;

  @summary("상품 상세 조회")
  @doc("단일 상품(UNIT)의 상세 정보를 조회한다.")
  @get
  @route("/{productId}")
  getDetail(@path productId: int64): ProductService.Common.ApiResponse<ProductDetailResponse> | ProductService.Common.ErrorResponse;
}
