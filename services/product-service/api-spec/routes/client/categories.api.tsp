import "@typespec/http";
import "../../common/response.tsp";
import "../../models/categories.dto.tsp";

using TypeSpec.Http;

namespace ProductService.Categories;

@tag("Categories")
@route("/api/v1/products/categories")
@useAuth(NoAuth)
interface CategoriesClient {
  @summary("카테고리 트리 조회")
  @doc("STANDARD/DISPLAY 두 카테고리 트리를 함께 반환한다.")
  @get
  list(): ProductService.Common.ApiResponse<CategoryTreeResponse> | ProductService.Common.ErrorResponse;
}
