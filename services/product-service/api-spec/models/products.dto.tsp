namespace ProductService.Products;

// ── 열거형 ──────────────────────────────────────────────────────────────

@doc("`com.kurly.product.presentation.dto.ProductSortType`와 1:1로 대응.")
enum ProductSortType {
  RECOMMENDED,
  LATEST,
  POPULAR,
  BENEFIT,
  PRICE_DESC,
  PRICE_ASC,
}

@doc("""
  가격대 필터. `com.kurly.product.domain.enums.PriceBand`와 1:1로 대응하며, 서버가
  `PriceBand.from(value)`로 이 문자열 값을 파싱한다.

  ⚠️ 알 수 없는 값이 오면 `IllegalArgumentException`이 그대로 던져지는데
  `GlobalExceptionHandler`가 이를 매핑하지 않아 400이 아니라 500(INTERNAL_SERVER_ERROR)으로
  응답한다 — 알려진 버그이며, 이 문서 작업 범위에서 코드는 고치지 않았다.
  """)
enum PriceBand {
  UNDER_5000: "0-5000",
  BETWEEN_5000_10000: "5000-10000",
  BETWEEN_10000_20000: "10000-20000",
  BETWEEN_20000_30000: "20000-30000",
  OVER_30000: "30000-",
}

@doc("`com.kurly.product.domain.enums.StorageType`와 1:1로 대응.")
enum StorageType {
  REFRIGERATED,
  FROZEN,
  ROOM_TEMPERATURE,
}

@doc("`com.kurly.product.domain.enums.PackagingType`와 1:1로 대응.")
enum PackagingType {
  PAPER,
  PLASTIC,
  FOAM,
  CARDBOARD,
}

enum MediaType {
  IMAGE,
  VIDEO,
}

enum MediaRole {
  THUMBNAIL,
  DETAIL,
}

enum ProductStatus {
  SALE,
  SOLDOUT,
  HIDDEN,
}

enum ProductType {
  GROUP,
  UNIT,
}

// ── 홈 ─────────────────────────────────────────────────────────────────

model QuickMenuItemDto {
  title: string;
  imageUrl: string;
  linkUrl: string;
}

model ProductSummaryDto {
  id: int64;
  name: string;
  brand: string;
  price: int64;
  salePrice: int64;
  discountRate: int32;
  likeCount: int32;
  thumbnailUrl: string;
}

@doc("`type`이 QUICK_MENU 섹션이면 quickMenus만 채워지고 products는 null, 그 외 섹션은 반대다 — 둘 다 채워지는 경우는 없다.")
model HomeSection {
  type: string;
  title: string;
  quickMenus: QuickMenuItemDto[] | null;
  products: ProductSummaryDto[] | null;
}

model HomeResponse {
  sections: HomeSection[];
}

// ── 목록/검색 ───────────────────────────────────────────────────────────

model ProductSummaryResponse {
  id: int64;
  name: string;
  brand: string;
  price: int64;
  salePrice: int64;
  discountRate: int32;
  likeCount: int32;
  thumbnailUrl: string;
}

@doc("""
  Spring Data `Slice<T>`를 커스텀 직렬화 없이 그대로 반환한 응답이다. 무한 스크롤 전제라
  `Page`와 달리 totalElements/totalPages가 없다.

  ⚠️ 이 필드 구성은 Spring Boot 기본 Jackson 직렬화 규칙을 근거로 추정한 것이고 실제 실행
  중인 서버로 확인하지는 않았다(미검증) — 실제 응답과 다르면 이 모델을 갱신할 것. 특히
  `pageable`/`sort` 중첩 객체는 세부 필드를 생략하고 빠져 있다.
  """)
model SliceResponse<T> {
  content: T[];
  last: boolean;
  first: boolean;
  size: int32;
  number: int32;
  numberOfElements: int32;
  empty: boolean;
}

model ProductFilterResponse {
  totalCount: int64;
  filterGroups: FilterGroupDto[];
}

model FilterGroupDto {
  filterId: string;
  title: string;
  items: FilterItemDto[];
}

model FilterItemDto {
  label: string;
  value: string;
  count: int64;
}

// ── 상세 ───────────────────────────────────────────────────────────────

model ProductSpecResponse {
  storageType: StorageType;
  packagingType: PackagingType;
  attributes: string;
}

model ProductMediaResponse {
  id: int64;
  mediaUrl: string;
  mediaType: MediaType;
  mediaRole: MediaRole;
  sequence: int32;
}

model ProductUnitResponse {
  id: int64;
  skuCode: string;
  name: string;
  price: int64;
  salePrice: int64;
  status: ProductStatus;
}

model ProductDetailResponse {
  id: int64;
  skuCode: string;
  name: string;
  shortDescription: string;
  brand: string;
  price: int64;
  discountRate: int32;
  salePrice: int64;
  type: ProductType;
  status: ProductStatus;
  likeCount: int32;
  totalSalesCount: int64;

  @doc("생성 일시 (서버 로컬 타임존 기준 LocalDateTime, Format: YYYY-MM-DDTHH:mm:ss)")
  createdAt: string;

  spec: ProductSpecResponse | null;
  media: ProductMediaResponse[];
  units: ProductUnitResponse[];
}
