import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

import "./common/response.tsp";
import "./common/types.tsp";

import "./models/products.dto.tsp";
import "./models/inbound.dto.tsp";
import "./models/inventory.dto.tsp";
import "./models/outbound.dto.tsp";
import "./models/workers.dto.tsp";
import "./models/stock-movements.dto.tsp";
import "./models/events.dto.tsp";

import "./routes/internal/products.internal.tsp";
import "./routes/internal/inbound.internal.tsp";

import "./routes/client/inbound.api.tsp";
import "./routes/client/inventory.api.tsp";
import "./routes/client/outbound.api.tsp";
import "./routes/client/monitoring.api.tsp";
import "./routes/client/workers.api.tsp";
import "./routes/client/stock-movements.api.tsp";

import "./routes/events/inventory.events.tsp";
import "./routes/events/inbound.events.tsp";
import "./routes/events/return.events.tsp";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "WMS Service API" })
@info(#{ version: "0.1.0" })
@doc("창고 관리(입고 → 적치/보충 → 재고 현행화 → 출고) API. docs/erd-spec.md, docs/CLAUDE.md 기준.")
@server("http://localhost:8085", "로컬 개발 서버")
@useAuth(BearerAuth)
@tagMetadata("Products", #{ description: "상품 서비스 → WMS 상품 스냅샷(WmsProduct) 동기화" })
@tagMetadata("Warehouse", #{ description: "창고 구역/로케이션 마스터 관리" })
@tagMetadata("Inbound", #{ description: "입고 예정(ASN) 생성, 검수, 적치" })
@tagMetadata("Inventory", #{ description: "재고 조회, 재고 선점(Soft-alloc). 하드 할당/복구는 비동기 이벤트로 처리(routes/events/), 로케이션 간 이동 작업 지시는 StockMovement 태그 참고" })
@tagMetadata("StockMovement", #{ description: "재고 이동 작업 지시(적치/보충/재배치) 생성·목록 조회·완료 확정" })
@tagMetadata("Outbound", #{ description: "피킹, 패킹, 송장. 출고 지시 생성은 비동기 이벤트로 처리(routes/events/)" })
@tagMetadata("Workers", #{ description: "창고 작업자 등록 및 상태 관리" })
@tagMetadata("Monitoring", #{ description: "로케이션 히트맵, 작업자 생산성(UPH) 등 운영 모니터링" })
namespace WmsService;
