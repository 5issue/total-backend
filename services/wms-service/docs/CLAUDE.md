# wms-service 개발 하네스 (CLAUDE.md)

`product-service`의 실제 구현 스타일과 팀 컨벤션 문서(`/docs/팀컨벤션-코드스타일.md`, `/docs/Repository 아키텍처 계층 분리 및 구현 전략.md`)를 근거로 정리한 wms-service 전용 개발 가이드입니다. Claude(및 팀원)가 이 서비스에서 코드를 작성할 때 참고하는 단일 문서로 관리합니다.

> 이 문서는 프로젝트 루트가 아니라 `wms-service/docs/`에 있으므로 Claude Code가 자동으로 로드하지 않습니다. 세션 시작 시 필요하면 이 파일을 직접 읽어서 컨텍스트로 사용하세요.

---

## 1. 서비스 개요

- 모듈명: `wms-service` (`com.kurly.wms`)
- 역할: 창고 관리(입고 → 적치/보충 → 재고 현행화 → 출고), FEFO 기준 LOT 관리
- 로컬 포트: 앱 `8085`, PostgreSQL `5437` (근거: [service-port-convention.md](../../../docs/service-port-convention.md))
- 상세 데이터 모델: [erd-spec.md](./erd-spec.md)
- API 명세: TypeSpec 우선(API-first) — [6. API 명세](#6-api-명세-typespec--openapi--swaggerpostman) 참고
- 현재 상태: Flyway 베이스라인 스키마 + JPA 엔티티 10종 구현 완료, Repository/Service/Controller 및 API 미구현 (2026-09-14 기준)

## 2. 빌드 / 실행 / 테스트 명령어

루트(`total-backend/`)에서 멀티 모듈 Gradle로 관리되므로, wms-service만 지정할 때는 `:wms-service:` prefix를 사용합니다.

```bash
# 컴파일
./gradlew :wms-service:compileJava

# 로컬 인프라(PostgreSQL) 기동/종료 — services/wms-service 디렉터리에서
docker compose up -d
docker compose down

# 로컬 프로파일로 실행 (기본 프로파일도 local)
./gradlew :wms-service:bootRun --args='--spring.profiles.active=local'

# 테스트 (JUnit 5, jacoco 커버리지 리포트가 finalizedBy로 자동 실행됨)
./gradlew :wms-service:test

# 전체 서비스 빌드/테스트 (common 모듈 포함 전체 리액터)
./gradlew build
```

- Java 25 툴체인, Spring Boot 4.1.0, `io.spring.dependency-management`는 루트 `build.gradle`(`subprojects {}`)에서 전 서비스 공통 적용됩니다. wms-service의 `build.gradle`에는 이 서비스에만 필요한 의존성만 추가합니다.
- Lombok, `spring-boot-starter-test`, JUnit 5 launcher, jacoco는 루트에서 이미 전 서비스에 공통 적용되어 있으므로 서비스별 `build.gradle`에 다시 선언하지 않습니다.
- jacoco 커버리지 목표: Line 80% / Branch 80% 이상 (팀컨벤션 문서 기준). 임계값 강제(verification)는 서비스별로 개별 적용하며, 아직 테스트가 없는 서비스의 빌드를 깨뜨리지 않기 위해 루트에서는 강제하지 않습니다.

## 3. 환경 설정 (application.yml 구성)

product-service와 동일한 3분할 구조 + 동일 환경변수 이름을 사용합니다.

| 파일 | 역할 |
| --- | --- |
| `application.yml` | 프로파일 비의존 공통 설정 (`spring.application.name`, `profiles.default: local`, `jpa.open-in-view: false`) |
| `application-local.yml` | 로컬 개발용. 모든 값에 `${VAR:기본값}` 형태로 기본값 지정 → `docker compose up -d`만으로 바로 기동 가능해야 함 |
| `application-prod.yml` | 운영용. 기본값 없이 `${VAR}`만 사용해 값이 없으면 기동 실패하도록 강제 |

주요 환경변수 (product-service와 동일한 이름 체계):

| 변수 | 용도 | local 기본값 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | Postgres 접속 정보 | `localhost` / `5437` / `wms` |
| `DB_USERNAME` / `DB_PASSWORD` | Postgres 인증 | `postgres` / `password` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | 메시징 | `localhost` / `5672` / `guest` / `guest` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 캐시 | `localhost` / `6379` / (없음) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `docker-compose.yml`의 컨테이너 초기화 변수 (Spring 쪽 `DB_*`와 별개, 기본값을 동일한 값으로 맞춰 둠) | `wms` / `postgres` / `password` |

- 실제 시크릿(운영 JWT 키, 소셜 client secret 등)은 `application-secret.yml`에 두고 Git에 커밋하지 않습니다(`.gitignore`에 이미 등록됨).
- 새 외부 연동이 필요해지면 `application-local.yml`에는 기본값 포함, `application-prod.yml`에는 기본값 없이 필수값으로 추가합니다.

## 4. 패키지 구조 (Layered Architecture)

product-service를 기준으로 한 4계층 구조입니다. 하위 도메인이 늘어나면 각 계층 안에서 도메인별 폴더로 분리합니다(팀컨벤션 문서 2번 항목).

```
src/main/java/com/kurly/wms/
├── WmsServiceApplication.java
├── presentation/              # Controller + 요청/응답 DTO
│   └── dto/
├── application/               # Service (유스케이스 조합, 트랜잭션 경계)
│   ├── support/                #   서비스 보조 로직(예: 레이아웃/집계 계산기)
│   └── event/                  #   내부 애플리케이션 이벤트
├── domain/                    # 순수 도메인: 레포지토리 인터페이스, 도메인 dto/enum/exception
│   ├── dto/
│   ├── enums/
│   ├── exception/
│   └── repository/             #   기술 종속성 없는 Repository 인터페이스
└── infrastructure/             # 기술 구현체
    ├── entity/                  #   JPA 엔티티
    ├── jpa/                     #   Spring Data JpaRepository
    ├── impl/                    #   domain.repository 구현체 (복잡한 조회 로직이 있을 때만)
    ├── config/                  #   Bean 설정 (Cache, Messaging 등)
    ├── messaging/                #  RabbitMQ/Kafka publisher, listener, 이벤트 dto
    └── scheduler/                #  Outbox 발행 등 스케줄 잡
```

도메인이 여러 개로 늘어나면(예: `inbound`, `outbound`, `inventory`) `domain/inbound/`, `infrastructure/entity/inbound/`처럼 하위 폴더로 쪼갭니다. 파일 하나짜리 계층까지 미리 만들지 말고 필요해지는 시점에 분리합니다(YAGNI).

## 5. 코드 스타일 가이드

### 5.1 공통 규칙

- 들여쓰기 4칸, 클래스 PascalCase, 변수/메서드 camelCase, DB 컬럼 snake_case
- 내부 구현(가변 상태, JPA 매핑 등)이 없으면 **Record 우선**, 있으면(엔티티 등) class 사용
- Enum은 도메인 상태값에 사용 (`ProductStatus.SALE` 처럼 대문자 스네이크 없는 단어)

### 5.2 네이밍

| 유형 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | `*Controller` | `InboundController` |
| Service | `*Service` | `InboundOrderService`, `InventoryQueryService` |
| Repository (domain) | `*Repository` | `InventoryRepository` |
| Repository (JPA) | `*JpaRepository` | `InventoryJpaRepository` |
| Entity | 단수형 명사 | `Inventory`, `StockMovement` |
| 요청/응답 DTO | `*Request` / `*Response` | `InboundItemCreateRequest`, `InventoryResponse` |

서비스 메서드는 조회는 `find`가 아니라 `get`을 사용합니다 (`getById`, `getHomeRecommendations`처럼). Repository 인터페이스에서는 Spring Data 관례대로 `find*`를 유지합니다.

### 5.3 Repository: 2파일 구조 우선

`Repository 아키텍처 계층 분리 및 구현 전략.md` 결정에 따라 기본은 **2파일 구조**입니다.

```java
// domain/repository/InventoryRepository.java — 순수 인터페이스, 프레임워크 무의존
public interface InventoryRepository {
    Optional<Inventory> findById(Long id);
    List<Inventory> findByProductId(Long productId);
}

// infrastructure/jpa/InventoryJpaRepository.java — 다중 상속으로 프록시가 자동 위임
public interface InventoryJpaRepository extends JpaRepository<Inventory, Long>, InventoryRepository {
}
```

Querydsl 동적 쿼리나 JdbcTemplate 벌크 연산처럼 복잡한 기술 요구가 생겼을 때만 `infrastructure` 계층 안에서 `*CustomRepository` / `*CustomRepositoryImpl`을 추가로 확장합니다(product-service의 `ProductRepositoryImpl`처럼 domain 인터페이스를 별도 구현하는 방식은 조회 조건 분기가 복잡해질 때 선택).

### 5.4 Entity

- `@Entity` + `@Table(name = "snake_case")`, `@Getter`만 두고 Setter는 만들지 않음
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder`가 붙은 private 생성자 조합 (product-service `Product` 참고)
- 상태 변경은 의미 있는 메서드로 노출 (`inventory.reserve(quantity)` 등), 필드를 외부에서 직접 set하지 않음
- 하위 Enum은 엔티티 내부에 nested enum으로 선언 (`Product.ProductStatus`처럼)

### 5.5 DTO / Record

- 응답 DTO는 record + 정적 팩토리 메서드(`of`, `from`)로 엔티티 → DTO 변환

```java
public record InventoryResponse(Long id, Long productId, Integer quantity, Integer reservedQuantity) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(inventory.getId(), inventory.getProductId(),
                inventory.getQuantity(), inventory.getReservedQuantity());
    }
}
```

### 5.6 Controller / 응답 포맷

- 모든 응답은 `com.kurly.common.response.ApiResponse<T>`로 감싸기 (`ApiResponse.success(data)`)
- 인증 불필요 엔드포인트는 `@PublicApi`, 역할 제한은 `common`의 `@RequireRole` 사용
- 생성자 주입 사용 (Lombok `@RequiredArgsConstructor` 또는 명시적 생성자, product-service 내에서도 두 방식이 혼용되므로 서비스 내에서는 한 방식으로 통일)
- URL: 소문자 + 하이픈, 동사 대신 HTTP 메서드로 행위 표현 (`GET /api/v1/inbound-orders/{id}`)

### 5.7 예외 처리

- 도메인 규칙 위반: `com.kurly.common.exception.BusinessException(ErrorCode, message)`
- 엔티티 미존재: `com.kurly.common.exception.EntityNotFoundException`
- 서비스 전용 에러 코드가 필요하면 `domain/exception/WmsErrorCode`를 만들어 `ErrorCode` 구현 (product-service의 `ProductErrorCode` 패턴), 공통 상황은 `GlobalErrorCode` 재사용
- 예외 메시지는 한국어로 작성

### 5.8 테스트 컨벤션

- 파일 분리: `XxxUnitTest` (정상 흐름) / `XxxUnitExceptionTest` (예외 흐름) / `XxxIntegrationTest`
- `@ExtendWith(MockitoExtension.class)` + `@Nested`로 시나리오 묶기, `@DisplayName`은 한국어
- 테스트 메서드명은 한국어, 클래스명은 영어 (auth-service `AdminAuthServiceUnitTest` 참고)

```java
@ExtendWith(MockitoExtension.class)
class InventoryServiceUnitTest {

    @Mock InventoryRepository inventoryRepository;
    InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository);
    }

    @Nested
    @DisplayName("재고 예약 정상 처리")
    class ReserveTest {
        @Test
        void 가용_재고가_충분하면_예약_수량이_증가한다() {
            // given / when / then
        }
    }
}
```

- Line/Branch 커버리지 80% 이상을 목표로 작성 (`./gradlew :wms-service:test`가 jacocoTestReport까지 실행).

## 6. API 명세 (TypeSpec → OpenAPI → Swagger/Postman)

API를 먼저 TypeSpec으로 설계하고(`api-spec/`), 거기서 OpenAPI를 생성해 Swagger/Postman에 동기화하는 API-first 흐름을 사용합니다. wms-service가 이 저장소에서 TypeSpec을 쓰는 첫 서비스라 아래 구조는 다른 서비스에 그대로 재사용할 수 있게 일반적으로 잡았습니다.

### 6.1 폴더/파일 구조

모델(DTO)과 라우트(엔드포인트)를 분리하고, 라우트는 다시 `internal`(서비스 간 통신, `/internal/v1/wms/...`)과 `client`(BO 어드민/PDA 현장 작업자, `/api/v1/wms/...`)로 나눕니다. 같은 도메인이라도 호출 주체가 다르면 별도 인터페이스로 분리하되, 모델은 `models/`에서 공유합니다.

```
api-spec/
├── main.tsp                       # @service, @server, @useAuth, 전역 @tagMetadata. 모든 파일을 import.
├── tspconfig.yaml                  # @typespec/openapi3 emitter 설정 (출력: tsp-output/schema/)
├── redocly.yaml                     # lint/문서 빌드 설정
├── package.json
│
├── common/
│   ├── response.tsp                 # ApiResponse<T>/ErrorResponse — com.kurly.common.response.ApiResponse<T>와 1:1 대응
│   └── types.tsp                     # StorageType, TaskStatus 등 여러 도메인이 공유하는 enum
│
├── models/                           # Request/Response DTO. 라우트 파일은 여기서 import만 한다.
│   ├── products.dto.tsp                # 상품 동기화 DTO
│   ├── inbound.dto.tsp                  # ASN/검수/적치 DTO
│   ├── inventory.dto.tsp                 # 창고/로케이션 마스터, 재고 조회/선점/이동 DTO
│   ├── outbound.dto.tsp                   # 출고 지시/피킹/패킹/송장 DTO
│   └── workers.dto.tsp                     # 작업자 등록/상태 DTO
│
└── routes/
    ├── internal/                      # 서비스 간 통신 (SCM/OMS/상품 서비스 → WMS)
    │   ├── products.internal.tsp        # POST .../products/sync
    │   ├── inbound.internal.tsp          # POST .../inbounds/asn
    │   ├── inventory.internal.tsp         # POST .../inventories/restore
    │   └── outbound.internal.tsp           # POST .../outbounds/orders (수신 + FEFO 할당)
    └── client/                          # BO 어드민 / PDA 현장 작업자
        ├── inbound.api.tsp                # 검수, 적치 추천/확정
        ├── inventory.api.tsp               # 가용 재고 조회, 선점(Soft-alloc), 이동 지시
        │                                    #  + Locations interface: 구역/로케이션 마스터 (base path가 달라 별도 interface)
        ├── outbound.api.tsp                 # 피킹 Task 조회/할당/결과 반영, 패킹·송장(placeholder)
        ├── workers.api.tsp                   # 작업자 등록/상태 변경
        └── monitoring.api.tsp                # 히트맵/작업자 UPH — 지표 정의 전 구조만 (placeholder)
```

- 응답은 반드시 `WmsService.Common.ApiResponse<T>`로 감싸서 선언합니다 (실제 Spring 쪽 `ApiResponse<T>` 래핑과 동일하게, [5.6](#56-controller--응답-포맷) 참고). 실패 시 형태는 `WmsService.Common.ErrorResponse`이며, 각 op 리턴 타입에 `| WmsService.Common.ErrorResponse`로 명시합니다.
- 서비스/엔티티에 이미 있는 enum과 이름을 맞춥니다(`InboundUnit`, `OutboundOrderStatus`, `LocationType` 등 — `infrastructure/entity/*`의 nested enum과 1:1).
- 인증은 전역 `@useAuth(BearerAuth)`로 선언되어 있습니다 (실제로는 `common`의 JWT `Authorization: Bearer` 검증과 대응, [5.6](#56-controller--응답-포맷)의 `@RequireRole`/`@PublicApi`). `internal`/`client`를 실제로 다른 인증 방식(서비스 키 vs 사용자 JWT)으로 분리할지는 아직 결정되지 않았다.
- 재고 선점은 두 단계로 나뉜다: ① `POST /api/v1/wms/inventories/allocation` — 주문 접수 시점의 Soft-alloc, 아직 `OutboundOrder`가 없을 수 있어 호출측 `referenceId`로 식별. ② `POST /internal/v1/wms/outbounds/orders` — OMS 주문 확정 시 출고 지시 생성 + FEFO 하드 할당을 함께 수행. 취소되면 `POST /internal/v1/wms/inventories/restore`로 복구. 실제 연동 순서/책임 분리는 확정된 것이 아니라 계약상 가정이다.
- `routes/internal/outbound.internal.tsp`, `models/workers.dto.tsp` + `routes/client/workers.api.tsp`는 논의된 파일 목록에 없었지만, 각각 "OMS의 출고 지시 생성 호출(서비스 간 통신)"과 "`/api/v1/wms/workers/**`라는 독립된 base path"라 새로 추가했다. `Worker`는 아직 Java 엔티티가 없어 구현 전 엔티티/마이그레이션 추가가 필요하다(모델 파일에 TODO로 표시).
- `routes/client/inventory.api.tsp`의 `Locations` interface(`/api/v1/wms/warehouses/**`, `/api/v1/wms/locations/**`)는 base path가 `/api/v1/wms/inventories`와 달라 같은 파일 안에서도 별도 `interface`로 분리했다. Warehouse/Location 마스터 관리가 커지면 그때 `warehouse.dto.tsp`/`warehouse.api.tsp`로 완전히 독립시킨다.
- 아직 확정되지 않은 도메인 규칙(FEFO 할당, 패킹/송장, 모니터링 지표 등)에 걸린 모델·엔드포인트는 `[placeholder]` 표시 + `// TODO:`/`// 참고:` 주석으로 남기고, 계약은 최소 형태로만 작성합니다. 요구사항이 정해지면 그때 필드를 채웁니다.
- 엔티티에 없는 필드가 요청/응답에 필요해지면(예: `InboundOrder.poNumber`) `// TODO:` 주석으로 남기고 엔티티/마이그레이션 작업과 별도로 추적합니다.
- 도메인 하나의 모델/라우트가 파일 하나로 감당이 안 될 만큼 커지면 그때 더 세분화합니다(YAGNI) — 예: `monitoring.api.tsp`는 아직 모델이 적어 `models/monitoring.dto.tsp`로 분리하지 않고 파일 내부에 둡니다.

### 6.2 명령어

```bash
cd api-spec
npm install                # 최초 1회

npm run build               # tsp compile . → tsp-output/schema/{3.1.0,3.0.0}/openapi.yaml
npm run watch                # 파일 변경 감지하며 재컴파일만 (Swagger 미표시)
npm run swagger                # 빌드 1회 + OpenAPI 3.0 파일로 로컬 Swagger UI 기동 (:8090, 파일 변경 시 브라우저 자동 갱신)
npm run dev                     # watch + swagger를 함께 실행하는 단일 명령 — 평소 작업할 땐 이것만 쓰면 됨
npm run lint                     # redocly lint로 OpenAPI 스타일 검사 (에러 0개 유지)
npm run docs                      # 정적 Redoc 미리보기 HTML 생성 (tsp-output/docs.html, 공유/리뷰용)
npm run postman:generate           # OpenAPI → Postman 컬렉션 생성 (postman/wms-service.postman_collection.json)
```

- `tsp-output/`, `postman/`, `node_modules/`는 전부 생성 산출물이라 Git에 커밋하지 않습니다(루트 `.gitignore`).
- PR 전에 최소 `npm run lint`(에러 0개)까지는 통과시킵니다.
- `tspconfig.yaml`은 `kind: project`를 쓰지 않습니다 — 이 값이 있으면 `tsp compile . --watch`가 `config-project-not-as-cli-config` 에러로 죽는다(`tsp init` 스캐폴드 기본값이 이랬다). 일반 단일 엔트리포인트 프로젝트에서는 그냥 지운다.
- `openapi-versions`에 `3.1.0`과 `3.0.0`을 둘 다 지정해 두 버전을 함께 생성합니다. lint/docs/postman은 최신 사양인 3.1.0을 쓰고, 로컬 Swagger UI(`npm run swagger`/`dev`)만 3.0.0을 씁니다 — `swagger-ui-watcher`가 물고 있는 Swagger UI 번들(3.x대)이 3.1의 JSON Schema 문법(`data: T | null` 같은 nullable 유니온이 `anyOf`+`type: 'null'`로 나오는 것 등)을 완전히 지원하지 않을 수 있어서다.

### 6.3 Swagger 동기화

**`npm run dev` (또는 `npm run swagger`)가 실시간 동기화입니다.** `tsp compile . --watch`가 파일 저장마다 OpenAPI를 재생성하고, `swagger-ui-watcher`가 그 파일을 `chokidar`로 감시하다가 바뀌면 소켓으로 브라우저에 바로 밀어 넣습니다 — 브라우저 새로고침도 필요 없습니다. `http://127.0.0.1:8090`으로 접속해 두면 `.tsp` 파일을 저장하는 즉시 화면이 갱신되는 걸 확인할 수 있습니다 (2026-09-14 실제 동작 확인: 필드 하나 추가 → 재컴파일 → "File changed. Sent updated spec to the browser." 로그 → 파일에 반영까지 3초 내).

**`http://localhost:8085/swagger-ui/index.html`(springdoc)과는 별개입니다.** 그건 실행 중인 Spring Boot 앱이 실제 `@RestController`를 스캔해서 만드는 문서라, 컨트롤러가 하나도 없는 지금은 `paths: []`로 비어 있는 게 정상입니다 — TypeSpec과 동기화가 안 된 게 아니라 애초에 이 둘은 연결되어 있지 않습니다. 컨트롤러를 구현하기 시작하면 그때부터 `:8085`가 의미를 가지며, 이 시점부터는 **TypeSpec 계약 = 실제 컨트롤러 응답 모양이 일치하는지 사람이 리뷰**해야 합니다(둘을 자동으로 diff하는 CI는 아직 없음). 어긋나면 TypeSpec을 갱신하거나 컨트롤러를 계약에 맞게 고칩니다.

### 6.4 Postman 동기화

- `npm run postman:generate`로 로컬에 컬렉션 JSON을 만든 뒤, Postman 앱에서 Import하거나 팀 워크스페이스에 업로드합니다.
- 지속적으로 팀 워크스페이스와 동기화하려면(다음 단계): Postman API Key + 대상 컬렉션 UID를 발급받아 `PUT https://api.getpostman.com/collections/{collectionUid}`로 생성된 JSON을 올리는 스크립트를 추가합니다. 자격 증명이 필요한 작업이라 아직 자동화하지 않았습니다.
- `openapi-to-postmanv2`의 전이 의존성(`js-yaml`, `uuid`)에 알려진 취약점이 있습니다(로컬 변환 도구이고 우리가 만든 OpenAPI만 입력으로 받으므로 실사용 위험은 낮음). `npm audit`으로 상태를 주기적으로 확인하고, 상위 메이저 릴리스가 이를 해결하면 업그레이드합니다.

## 7. 참고 문서

- [팀컨벤션-코드스타일.md](../../../docs/팀컨벤션-코드스타일.md)
- [Repository 아키텍처 계층 분리 및 구현 전략.md](../../../docs/Repository%20아키텍처%20계층%20분리%20및%20구현%20전략.md)
- [service-port-convention.md](../../../docs/service-port-convention.md)
- [백엔드_시큐어코딩가이드.md](../../../docs/백엔드_시큐어코딩가이드.md)
- [erd-spec.md](./erd-spec.md) — wms-service 데이터 모델
- product-service 실제 구현체: `services/product-service/src/main/java/com/kurly/product/`

## 8. 개발 진행 상황

새 작업을 시작/완료할 때 아래 표에 이어서 기록합니다. 상태는 `계획` / `진행중` / `완료` / `보류` 중 하나.

| 날짜 | 작업 | 상태 | 비고 |
| --- | --- | --- | --- |
| 2026-09-14 | PostgreSQL `docker-compose.yml` 추가, `build.gradle` mysql→postgresql 드라이버 교체, `application.yml`을 local/prod로 분리 | 완료 | 포트 5437, env var 이름 product-service와 동일 |
| 2026-09-14 | 본 하네스 문서(`docs/CLAUDE.md`) 작성 | 완료 | product-service 실제 코드 + 팀컨벤션 문서 기준 |
| 2026-09-14 | Flyway 도입 (`V1__baseline_schema.sql`), ERD 기반 JPA 엔티티 10종(`Warehouse`, `WmsProduct`, `Location`, `InboundOrder`, `InboundItem`, `Inventory`, `StockMovement`, `OutboundOrder`, `OutboundItem`, `Outbox`) 구현 | 완료 | `ddl-auto: validate`로 기동 검증 완료. 공유 enum(`StorageType`, `OutboxStatus`)은 `domain/enums`, 엔티티 전용 enum은 nested로 배치 |
| 2026-09-14 | TypeSpec API-first 구조 수립, OpenAPI3/Postman 생성 파이프라인(`npm run build/lint/docs/postman:generate`) 구성 | 완료 | 상품 동기화·ASN 생성 2개 엔드포인트는 실제 예시 기반. `redocly lint` 에러 0개 |
| 2026-09-14 | `api-spec/`을 `common/`·`models/`·`routes/{internal,client}` 구조로 재구성, BO/PDA 클라이언트 API(검수·적치·피킹·이동지시) 및 모니터링/패킹/송장 placeholder 추가 | 완료 | 16개 경로 생성 확인. 패킹/송장/모니터링 지표는 요구사항 미확정 상태의 구조만 |
| 2026-09-14 | 전체 API 목록(18개 엔드포인트) 반영: 창고/로케이션 마스터(`Locations` interface), 작업자 관리(`workers.dto/api.tsp`, 신규), 재고 선점을 Soft-alloc(client)/FEFO 하드 할당(internal)/복구(internal) 3단계로 재설계 | 완료 | 21개 오퍼레이션(20개 경로) 생성, `redocly lint` 에러 0개. `Worker`는 Java 엔티티 아직 없음(TODO) |
| 2026-09-14 | 로컬 Swagger 실시간 동기화 구성(`swagger-ui-watcher` + `tsp compile --watch`, `npm run dev`), `tspconfig.yaml`의 `kind: project` 제거(있으면 `--watch`가 에러로 죽는 버그), `openapi-versions`에 3.0.0 추가 | 완료 | 필드 추가 → 브라우저 자동 반영까지 실측 3초 내. springdoc(`:8085/swagger-ui`)과는 여전히 별개(컨트롤러 미구현이라 `paths: []`) |
| - | Repository(2파일 구조)·Service·Controller 구현 | 계획 | 엔티티만 우선 반영, [erd-spec.md](./erd-spec.md)의 미결 사항(로케이션 주소 체계, 재고 예약 시점 등) 및 `api-spec/` 계약 먼저 확정 필요 |
| - | `InboundOrder`에 `po_number` 컬럼 추가 검토 | 계획 | `api-spec/inbound.tsp`의 TODO 참고 — SCM 발주번호 연계에 필요 |
| - | Postman 팀 워크스페이스 자동 동기화 스크립트 | 계획 | Postman API Key/컬렉션 UID 발급 후 진행 ([6.4](#64-postman-동기화) 참고) |
