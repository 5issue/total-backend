namespace ProductService.Categories;

@doc("`com.kurly.product.domain.category.Category.CategoryType`(가칭)와 1:1로 대응.")
enum CategoryType {
  STANDARD,
  DISPLAY,
}

model CategoryResponse {
  id: int64;
  name: string;
  type: CategoryType;
  sequence: int32;
  children: CategoryResponse[];
}

@doc("""
  실제 Java 응답 타입은 `Map<CategoryType, List<CategoryResponse>>`이지만, CategoryType이
  STANDARD/DISPLAY 두 값뿐이라 항상 이 두 키만 갖는 고정 객체로 더 정확하게 표현했다.
  """)
model CategoryTreeResponse {
  STANDARD: CategoryResponse[];
  DISPLAY: CategoryResponse[];
}
