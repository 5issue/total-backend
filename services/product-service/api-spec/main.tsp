import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

import "./common/response.tsp";

import "./models/products.dto.tsp";
import "./models/categories.dto.tsp";
import "./models/inventory.dto.tsp";
import "./models/internal.dto.tsp";
import "./models/events.dto.tsp";

import "./routes/internal/inventory.internal.tsp";
import "./routes/internal/products.internal.tsp";

import "./routes/client/products.api.tsp";
import "./routes/client/categories.api.tsp";

import "./routes/events/inventory.events.tsp";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "Product Service API" })
@info(#{ version: "0.1.0" })
@doc("상품 카탈로그 조회, 주문 연동 재고 HOLD/RELEASE, 재고 확정/복구 이벤트 발행 API.")
@server("http://localhost:8084", "로컬 개발 서버")
@useAuth(BearerAuth)
@tagMetadata("Products", #{ description: "상품 홈/목록/상세 조회(공개), 배치 요약 조회(내부)" })
@tagMetadata("Categories", #{ description: "카테고리 트리 조회(공개)" })
@tagMetadata("Inventory", #{ description: "주문으로부터의 재고 HOLD/RELEASE(내부, 인증 필요). CONFIRM/RESTORE 결과는 REST가 아니라 비동기 이벤트로 발행(routes/events/)" })
namespace ProductService;
