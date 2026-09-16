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
- CI(`.github/workflows/ci-wms-service.yml`의 `build-and-test` job)는 `./gradlew :common:build :wms-service:build`를 그대로 돌립니다(product-service와 동일 패턴). 아직 리포지토리/서비스 레이어와 테스트가 없어 Postgres 서비스 컨테이너는 없습니다 — 실제 DB를 쓰는 통합 테스트가 추가되면 auth-service의 MySQL 서비스 컨테이너 패턴을 참고해 Postgres 컨테이너를 붙입니다.

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

### 3.1 로컬 시드 데이터

`db/seed/seed_wms_product.sql`이 product-service의 시드 데이터(UNIT 타입만)를 가져와 `wms_product`를 채웁니다. product-service와 wms-service는 완전히 분리된 Postgres 컨테이너(포트 5436/5437, 별도 docker-compose 프로젝트)라 SQL 하나로 조인할 수 없어서, wms DB 쪽에서 `dblink` 확장으로 product 컨테이너에 직접 접속해 가져옵니다.

```bash
# 1) product-service 시드가 먼저 실행되어 있어야 한다 (UNIT 상품이 존재해야 함)
# 2) 그다음 wms-service에서:
docker exec -i kurly-postgres-wms psql -U postgres -d wms \
  < src/main/resources/db/seed/seed_wms_product.sql
```

- 매핑: `id`/`sku_code`/`name`은 product-service 값을 그대로 쓰고(`WmsProduct.id`는 자체 채번하지 않고 Product UNIT ID와 같아야 함), `storage_type`은 `product_spec.storage_type`을 그대로 씁니다(enum 값이 이미 동일). `barcode`, `box_unit_qty`, `pallet_box_qty`, `safety_stock`은 상품 서비스에 없는 WMS 전용 값이라 임의 기본값을 둡니다.
- `host.docker.internal:5436`으로 접속합니다. Docker Desktop(macOS/Windows)은 기본 동작하고, Linux에서는 `docker-compose.yml`의 `postgres-wms`에 붙여둔 `extra_hosts: ["host.docker.internal:host-gateway"]`가 있어야 풀립니다(루트 `docker-compose.yml`의 swagger-ui와 동일 패턴).
- 멱등하지 않습니다(`id`/`sku_code` 제약). `ON CONFLICT (id) DO NOTHING`으로 재실행 시 에러 없이 건너뛰지만, 값을 최신화하려면 `TRUNCATE wms_product;`(FK로 물린 다른 테이블도 먼저 정리) 후 재실행합니다.
- 2026-09-15 기준 UNIT 213건 전량 정상 삽입 확인(REFRIGERATED 103 / ROOM_TEMPERATURE 97 / FROZEN 13), 재실행 시 중복 삽입 없음 확인.

`db/seed/seed_wms_warehouse.sql`은 실제 컬리 물류센터/컬리나우 매장 8곳으로 `warehouse`를 채웁니다(product 쪽 의존이 없어 dblink 불필요, 단독 실행 가능). `code`/`is_active`는 원본에 없어 임의로 채웠습니다(물류센터는 `<지명>_DC`, 컬리나우는 `CNOW_<지점명>`).

```bash
docker exec -i kurly-postgres-wms psql -U postgres -d wms \
  < src/main/resources/db/seed/seed_wms_warehouse.sql
```

- `location`은 8곳 중 **김포물류센터(`GIMPO_DC`)에 대해서만** 예시로 채웁니다 — 실제 물류센터는 로케이션이 수천 단위라 8곳 전부를 지금 채우는 건 의미가 없고, 구조를 보여주는 샘플 하나면 충분하다고 판단했습니다. 다른 창고도 필요해지면 이 파일의 "2. Location" 블록에서 `code` 조건만 바꿔 재사용합니다.
- 구성(53건): 버퍼(BUFFER) 2개 + storage_type(냉장/냉동/상온)별 보관존(PALLET_RACK) 8개 × 3 + 피킹존(SHELF_BIN) 3×3 격자(F01~F09) × 3. `docs/erd-spec.md`의 로케이션 설계 원칙을 그대로 따른 예시 레이아웃이며 실제 김포물류센터 구조와는 무관합니다. `zone`은 `location_type`과 1:1(BUFFER=BUFFER/PALLET_RACK=STORAGE/SHELF_BIN=PICKING)이라 항상 고정값입니다.
- storage_type마다 물리적 통로(aisle)를 분리했습니다(상온=A, 냉장=B, 냉동=C 접두사) — 같은 주소에 냉장/냉동 파레트가 같이 있을 수 없어서, `UNIQUE(warehouse_id, aisle, rack, level, bin)` 제약과도 자연히 맞습니다.
- `warehouse.code`/`location`의 복합 UNIQUE 제약 덕분에 `ON CONFLICT DO NOTHING`으로 재실행해도 안전합니다. 2026-09-15 기준 창고 8건 + 로케이션 53건 삽입, 재실행 시 중복 없음 확인.

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
- 같은 행을 읽어서 상태를 전이시키는 서비스 메서드가 있는 엔티티(사용자 액션 기반 상태 머신 등, `InboundItem`처럼)에는 `@Version` 낙관적 락을 추가한다. 그 행을 읽는 repository 메서드는 `@Lock(LockModeType.OPTIMISTIC)`로 선언해 커밋 시점 충돌을 명시적으로 검증하고(`InboundItemJpaRepository.findWithOptimisticLockById` 참고), 실패 시 `ObjectOptimisticLockingFailureException`은 `common`의 `GlobalExceptionHandler`가 409(`GlobalErrorCode.CONFLICT`)로 변환해준다 — 서비스 코드에서 따로 잡을 필요 없다.

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

모델(DTO)과 라우트(엔드포인트/이벤트)를 분리하고, 라우트는 다시 `internal`(서비스 간 REST 통신, `/internal/v1/wms/...`), `client`(BO 어드민/PDA 현장 작업자, `/api/v1/wms/...`), `events`(비동기 RabbitMQ 이벤트, HTTP 아님)로 나눕니다. 셋 다 `main.tsp` 하나에서 컴파일되는 **같은 프로젝트**이고(`tsp compile .` 한 번으로 REST 스키마와 이벤트 스키마가 같은 `tsp-output/schema/*.yaml`에 함께 실린다), 모델은 `models/`에서 공유합니다.

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
├── models/                           # Request/Response/이벤트 페이로드 DTO. 라우트 파일은 여기서 import만 한다.
│   ├── products.dto.tsp                # 상품 동기화 DTO
│   ├── inbound.dto.tsp                  # ASN/검수/적치 DTO
│   ├── inventory.dto.tsp                 # 창고/로케이션 마스터, 재고 조회/선점/이동 DTO
│   ├── outbound.dto.tsp                   # 출고 지시/피킹/패킹/송장 DTO
│   ├── workers.dto.tsp                     # 작업자 등록/상태 DTO
│   └── events.dto.tsp                       # 이벤트 페이로드(OrderEvent 등) — 6.6 참고
│
└── routes/
    ├── internal/                      # 서비스 간 REST 통신 (SCM/상품 서비스 → WMS)
    │   ├── products.internal.tsp        # POST .../products/sync
    │   └── inbound.internal.tsp          # POST .../inbounds/asn
    ├── client/                          # BO 어드민 / PDA 현장 작업자
    │   ├── inbound.api.tsp                # 검수, 적치 추천/확정
    │   ├── inventory.api.tsp               # 가용 재고 조회, 선점(Soft-alloc), 이동 지시
    │   │                                    #  + Locations interface: 구역/로케이션 마스터 (base path가 달라 별도 interface)
    │   ├── outbound.api.tsp                 # 피킹 Task 조회/할당/결과 반영, 패킹·송장(placeholder)
    │   ├── workers.api.tsp                   # 작업자 등록/상태 변경
    │   └── monitoring.api.tsp                # 히트맵/작업자 UPH — 지표 정의 전 구조만 (placeholder)
    └── events/                          # 비동기(RabbitMQ) 이벤트 정의. @route 대신 리터럴 타입 필드 — 6.6 참고
        ├── inventory.events.tsp           # order.inventory.confirm, order.canceled.inventory-restore (order → wms/product 소비)
        ├── inbound.events.tsp              # wms.inbound.completed (wms → product 발행)
        └── return.events.tsp                # wms.return.inspected (wms → product,order 발행)
```

- 응답은 반드시 `WmsService.Common.ApiResponse<T>`로 감싸서 선언합니다 (실제 Spring 쪽 `ApiResponse<T>` 래핑과 동일하게, [5.6](#56-controller--응답-포맷) 참고). 실패 시 형태는 `WmsService.Common.ErrorResponse`이며, 각 op 리턴 타입에 `| WmsService.Common.ErrorResponse`로 명시합니다.
- 서비스/엔티티에 이미 있는 enum과 이름을 맞춥니다(`InboundUnit`, `OutboundItemStatus`, `LocationType` 등 — `infrastructure/entity/*`의 nested enum과 1:1).
- 인증은 전역 `@useAuth(BearerAuth)`로 선언되어 있습니다 (실제로는 `common`의 JWT `Authorization: Bearer` 검증과 대응, [5.6](#56-controller--응답-포맷)의 `@RequireRole`/`@PublicApi`). `internal`/`client`를 실제로 다른 인증 방식(서비스 키 vs 사용자 JWT)으로 분리할지는 아직 결정되지 않았다.
- 재고 선점/할당/복구는 REST와 비동기 이벤트로 나뉜다: ① `POST /api/v1/wms/inventories/allocation` — 주문 접수 시점의 Soft-alloc(REST, client), 아직 `OutboundOrder`가 없을 수 있어 호출측 `referenceId`로 식별. ② `order.inventory.confirm` 이벤트 — 결제 완료 시 출고 지시 생성 + FEFO 하드 할당(비동기, [6.6](#66-비동기-이벤트-명세-typespec--notion-event-db) 참고). ③ `order.canceled.inventory-restore` 이벤트 — 주문 취소 시 재고 복구(비동기). ②③은 원래 REST(`/internal/v1/wms/outbounds/orders`, `/internal/v1/wms/inventories/restore`)였다가 전환했다. 실제 연동 순서/책임 분리는 확정된 것이 아니라 계약상 가정이다.
- `models/workers.dto.tsp` + `routes/client/workers.api.tsp`는 논의된 파일 목록에 없었지만 "`/api/v1/wms/workers/**`라는 독립된 base path"라 새로 추가했다. `Worker`는 아직 Java 엔티티가 없어 구현 전 엔티티/마이그레이션 추가가 필요하다(모델 파일에 TODO로 표시).
- `routes/client/inventory.api.tsp`의 `Locations` interface(`/api/v1/wms/warehouses/**`, `/api/v1/wms/locations/**`)는 base path가 `/api/v1/wms/inventories`와 달라 같은 파일 안에서도 별도 `interface`로 분리했다. Warehouse/Location 마스터 관리가 커지면 그때 `warehouse.dto.tsp`/`warehouse.api.tsp`로 완전히 독립시킨다.
- 아직 확정되지 않은 도메인 규칙(FEFO 할당, 패킹/송장, 모니터링 지표 등)에 걸린 모델·엔드포인트는 `[placeholder]` 표시 + `// TODO:`/`// 참고:` 주석으로 남기고, 계약은 최소 형태로만 작성합니다. 요구사항이 정해지면 그때 필드를 채웁니다.
- 엔티티에 없는 필드가 요청/응답에 필요해지면 `// TODO:` 주석으로 남기고 엔티티/마이그레이션 작업과 별도로 추적합니다 (실제 사례: `InboundOrder.poNumber`는 계약에만 있다가 `POST /internal/v1/wms/inbounds/asn` 구현 시점에 `V2` 마이그레이션으로 반영했다).
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

### 6.5 Notion 동기화

동기화 로직은 리포 공용 패키지 `tools/notion-sync-kit`(`bin/sync-notion-db.js`)에 있고, wms-service는 `file:` 의존성(`@total-backend/notion-sync-kit`)으로 가져다 쓴다 — 여러 서비스가 같은 로직을 공유하며, 로직을 고치려면 이 공용 패키지 하나만 수정하면 된다. wms-service의 `package.json` `sync:notion*` 스크립트는 그 패키지가 노출하는 `sync-notion-db`/`sync-notion-events` 커맨드를 호출하는 얇은 래퍼다.

```bash
cp .env.example .env        # NOTION_TOKEN, NOTION_DATABASE_ID, SERVICE_DOMAIN(=WMS) 채워넣기
npm run sync:notion:dry       # 노션 호출 없이 결과만 tsp-output/notion-dry-run.json에 저장 (실제 실행 전 항상 먼저 이걸로 확인)
npm run sync:notion            # 실제 upsert
```

- 매칭 키는 `PATH(endpoint)` + `METHOD`. 같은 조합이 있으면 속성 갱신 + 본문 전체 재작성, 없으면 새 행 생성.
- `피드백/수정요청`, `검토 상태`, `중요도`는 업데이트 payload에 아예 포함하지 않아서 절대 덮어쓰지 않는다(`buildProperties`가 기존 행에는 이 키들을 아예 만들지 않는 방식). 셋 다 새 행을 만들 때만 기본값(중요도 "중", 검토 상태 "🟢 정상")을 채우고, 그 이후로는 팀원이 노션에서 바꾼 값을 그대로 유지한다.
- 페이지 "속성"과 달리 페이지 "본문"(Request/Response 블록)은 매번 통째로 지우고 새로 쓴다. 팀원이 본문에 직접 적어둔 메모는 다음 sync에서 사라지니, 코멘트는 본문이 아니라 `피드백/수정요청` 속성에 남기도록 안내한다.
- 페이지 본문 순서: 📖 API 개요(오퍼레이션의 `@doc`, 없으면 "(작성 필요)") → 🔹 Request(Headers/Path/Query/Body) → 🔹 Response(성공/실패 예시) → HTTP 에러 코드 정의. `@doc`이 맨 위에 오는 건 events(6.6)의 "📖 통신 개요"와 같은 자리를 REST에도 맞춘 것(2026-09-16 추가 — 그 전엔 `op.description`을 파싱만 하고 본문에 렌더링하지 않는 채로 남아 있었다).
- Request Body/Response 예시 JSON은 TypeSpec에 `@example`을 안 붙여놔서 스키마를 보고 자동 생성한 값이다 (문자열은 `"string"`, `*Id`류 정수는 `1`, `*quantity`류는 `10` 등 휴리스틱). 실제 값이 아니라 "필드가 이런 모양"이라는 뜻으로만 보면 된다.
- "HTTP 에러 코드 정의" 표는 OpenAPI 스펙에 도메인별 에러 코드가 없어서(`default` → `ErrorResponse`만 정의됨) `common/exception/GlobalErrorCode.java`의 공통 코드(COMMON400~500)로 채운 기본값이다. 엔드포인트별 도메인 에러 코드는 이 스크립트가 알 방법이 없으니 팀원이 직접 보강해야 한다.
- 노션 API 2025-09-03부터 데이터베이스 아래에 "data source" 개념이 생겨서, 행 조회는 `dataSources.query`로 한다(SDK에 `databases.query`가 아예 없어졌다). 스크립트가 `NOTION_DATABASE_ID`로 첫 번째 data source를 자동으로 찾아 쓰므로 평소 쓰는 단일 data source 데이터베이스라면 신경 쓸 필요 없다.
- 실행 시작 시 데이터베이스에 9개 속성(이름/PATH(endpoint)/METHOD/Bearer/도메인/상세/중요도/피드백·수정요청/검토 상태)이 정확한 타입으로 있는지 먼저 검증하고, 하나라도 다르면 무엇이 문제인지 즉시 알려주고 종료한다.
- **REST/이벤트 명세 DB는 서비스별로 따로 두지 않고 리포 전체가 공유한다.** 각 행의 `도메인`(REST) 또는 `송신`/`수신`(이벤트) 속성으로 어느 서비스 것인지 구분한다. wms-service는 `SERVICE_DOMAIN=WMS`로 이 값을 넘긴다. 다른 서비스가 같은 패턴을 쓰려면 `tools/notion-sync-kit/template/`을 참고해서 자기 `api-spec/`을 부트스트랩하면 된다.
- GitHub Actions: `.github/workflows/ci-wms-service.yml`에 job이 두 개다. `build-and-test`는 push/PR 둘 다에서 돌며 다른 서비스와 동일한 컴파일/테스트 검사를 한다. `sync-notion`은 `services/wms-service/**` 변경을 `develop`에 push할 때(+ 수동 실행)만 돌고 PR에서는 돌지 않는다(`if: github.event_name != 'pull_request'`) — merge된 것만 문서에 반영하기 위함. 실제 단계(체크아웃/`npm ci`/두 DB 동기화/실패 격리)는 `.github/workflows/reusable-notion-sync.yml`이라는 `workflow_call` 재사용 워크플로우 하나로 관리되고, `ci-wms-service.yml`은 `working-directory: services/wms-service/api-spec`, `domain: WMS`만 넘겨 호출한다 — 다른 서비스도 이 두 값만 바꿔 같은 워크플로우를 호출하면 된다. `sync-notion`(정확히는 `reusable-notion-sync.yml`의 job)은 진행 중인 실행을 취소하지 않는다(빌드는 취소해도 되지만 노션 sync는 취소하면 페이지가 반쯤 갱신된 채 남을 수 있음). 리포지토리 Settings → Secrets에 `NOTION_TOKEN`, `NOTION_DATABASE_ID`, `NOTION_EVENT_DATABASE_ID`를 등록해야 동작하며, 이 시크릿들은 모든 서비스가 공유한다(`secrets: inherit`).
- OpenAPI 파싱/예시 생성/노션 블록 빌더/노션 API 호출 같은 공통 로직은 공용 패키지의 `lib/notion-openapi.js`(`tools/notion-sync-kit/lib/notion-openapi.js`)에 모아뒀다. [6.6](#66-비동기-이벤트-명세-typespec--notion-event-db)의 이벤트 동기화 스크립트도 이 파일을 그대로 가져다 쓴다.

### 6.6 비동기 이벤트 명세 (TypeSpec → Notion Event DB)

RabbitMQ/Kafka 이벤트도 REST와 같은 TypeSpec 프로젝트(`main.tsp`) 안에서 관리한다 — 별도 컴파일 단위로 뺄 이유가 없었다: 이벤트는 HTTP 오퍼레이션이 없어서 `doc.paths`에는 전혀 나타나지 않고, `sync-notion-events`(공용 패키지 `tools/notion-sync-kit/bin/sync-notion-events.js`)는 애초에 `doc.components.schemas`에서 이벤트 메타데이터 필드(아래)를 가진 것만 골라 읽으므로 REST DTO와 한 파일에 섞여 있어도 서로 간섭하지 않는다. `routes/events/`는 `routes/internal/`, `routes/client/`와 나란한 세 번째 카테고리다 — 다만 `@route/@get/@post` 대신 리터럴 타입 필드로 계약을 표현한다는 점만 다르다.

```
models/events.dto.tsp            # 이벤트 페이로드 모델 (OrderEvent, OrderEventItem — REST의 Request/Response DTO와 같은 자리)
routes/events/inventory.events.tsp # 이벤트 정의 (OrderInventoryConfirmEvent 등 — REST의 interface와 같은 자리)
```

이벤트 하나는 producer/consumer/topic/exchange/queue/dataFormat/payloadType을 **리터럴 타입 필드**로 박아 넣은 모델이다. TypeSpec에서 `producer: "order";`처럼 프로퍼티 타입 자체를 문자열 리터럴로 주면, OpenAPI로 컴파일됐을 때 `{ type: "string", enum: ["order"] }`가 되고, `sync-notion-events`가 `enum[0]`을 읽어 메타데이터로 파싱한다 — 별도 emitter나 커스텀 데코레이터 없이 기존 `@typespec/openapi3` 파이프라인을 그대로 재사용하는 트릭이다. HTTP 오퍼레이션이 하나도 없는 모델(orphan)도 `@service`가 선언된 프로젝트 안에 있으면 전부 `components.schemas`에 실린다는 걸 확인하고 이 방식으로 정했다. producer/consumer가 여럿이면 콤마로 구분한 문자열 하나로 둔다(예: `"product,wms"`) — 스크립트가 split해서 노션 Multi-select 배열로 바꾼다.

```bash
npm run build                     # main.tsp 전체 컴파일 → REST 경로 + 이벤트 스키마가 같은 tsp-output/schema/*.yaml에 함께 실림
npm run sync:notion:events:dry     # 노션 호출 없이 tsp-output/notion-events-dry-run.json으로 미리보기
npm run sync:notion:events          # 실제 upsert (NOTION_TOKEN, NOTION_EVENT_DATABASE_ID 필요)
```

- 매칭 키는 `Topic` **또는** `개요` — 둘 중 하나라도 같으면 같은 이벤트로 보고 갱신한다.
- 노션 DB 속성: `개요`(Title), `Topic`(Text — 매칭용으로 추가. 사용자가 준 컬럼 목록엔 없었지만 매칭 키로 꼭 필요해서 넣었다), `송신`/`수신`(Multi-select), `통신 방식`(Select, 이 저장소에 RabbitMQ만 있어서 항상 `"rabbitmq"`로 채움), `피드백/수정요청`/`검토 상태`(REST와 동일하게 보호).
- 페이지 본문: 📌 메타데이터 callout → 📖 통신 개요(모델의 `@doc`) → ⚙️ 라우팅 상세(bullet) → 🔹 Header 코드블록(`content_type`/`__TypeId__`) → 🔹 Body Payload 코드블록(payload 스키마 기반 자동 예시).
- 2026-09-15: `POST /internal/v1/wms/inventories/restore`와 `POST /internal/v1/wms/outbounds/orders` REST 스펙을 비동기 이벤트로 전환하면서 삭제했다(`routes/internal/{inventory,outbound}.internal.tsp` 제거, 관련 orphan 모델도 정리). 이제 이 두 흐름은 아래 이벤트가 대체한다:
  - `order.inventory.confirm` — 결제 완료 → WMS가 FEFO 재고 할당 + 출고 지시 생성 (기존 outbounds/orders를 대체)
  - `order.canceled.inventory-restore` — 결제 후 주문 취소 → WMS가 재고 복구 (기존 inventories/restore를 대체)
- ⚠️ **실제 코드에서 발견한 불일치**: `order.canceled.inventory-restore`는 order-service의 `PaymentCancellationEvent`가 실제로 발행하는 routingKey다. 그런데 product-service의 `InventoryMessagingProperties.restoreRoutingKey` 기본값은 `order.inventory.restore`로, 아무도 발행하지 않는 값이다(레포 전체에서 이 문자열을 publish하는 코드가 없다). 즉 product-service의 재고 복구 큐는 지금 아무 메시지도 못 받고 있을 가능성이 있다 — WMS는 실제로 발행되는 값(`order.canceled.inventory-restore`)을 기준으로 스펙을 작성했다. product-service 쪽은 이번 작업 범위가 아니라 손대지 않았으니 별도로 확인이 필요하다.

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
| 2026-09-14 | OpenAPI → Notion 동기화 스크립트(`scripts/sync-notion-db.js`) 작성, `ci-wms-service.yml`에 `sync-notion` job 추가(별도 워크플로 파일은 만들지 않고 기존 CI 파일에 통합) | 완료 | 21개 엔드포인트 전부 `--dry-run`으로 파싱/블록 생성 검증 완료(실제 노션 호출은 미검증 — 토큰 없음). `@notionhq/client` v5가 Notion API 2025-09-03(data source 모델) 대상이라 `dataSources.query`로 구현, `databases.query`는 SDK에 없음 |
| 2026-09-15 | `POST /internal/v1/wms/inventories/restore`, `POST /internal/v1/wms/outbounds/orders`를 비동기 이벤트(`order.inventory.confirm`, `order.canceled.inventory-restore`)로 전환. `events/` TypeSpec 스펙 + `scripts/sync-notion-events.js` 작성, 공통 로직은 `scripts/lib/notion-openapi.js`로 추출해 REST 스크립트와 공유 | 완료 | REST/이벤트 양쪽 `--dry-run` 검증 완료. order-service `PaymentCancellationEvent`가 실제 발행하는 routingKey(`order.canceled.inventory-restore`)와 product-service `InventoryMessagingProperties`의 restore 기본값(`order.inventory.restore`)이 서로 다른 걸 발견 — product-service 쪽 재고 복구 큐가 메시지를 못 받고 있을 가능성, 별도 확인 필요(이번 작업 범위 아님) |
| 2026-09-15 | 이벤트 스펙을 별도 컴파일 단위(`api-spec/events/`)에서 `main.tsp` 하나로 통합 — `models/events.dto.tsp`(페이로드) + `routes/events/inventory.events.tsp`(이벤트 정의, `routes/{internal,client}`와 나란한 세 번째 카테고리)로 재배치, `build:events` 스크립트 제거 | 완료 | 분리해뒀던 이유(별도 emitter 필요할까 봐)가 실제로는 근거가 없었음 — `doc.paths`/`doc.components.schemas` 모양으로 구분하는 두 스크립트는 REST/이벤트가 한 파일에 섞여 있어도 문제없다는 걸 재확인. `npm run build` 한 번으로 REST 19개 경로 + 이벤트 2개 스키마 동시 생성, 양쪽 `--dry-run` 재검증 완료 |
| 2026-09-15 | `ci-wms-service.yml`에 `build-and-test` job 추가(product-service와 동일 패턴: `./gradlew :common:build :wms-service:build`), trigger 경로에 `common/`·`build.gradle`·`settings.gradle`·`gradle/**`·`gradlew` 추가, `sync-notion`과 취소 정책이 달라 job별 `concurrency`로 분리 | 완료 | 기존엔 `sync-notion` job만 있어서 실제 빌드/컴파일 검증이 CI에 전혀 없었음. 로컬에서 `./gradlew :common:build :wms-service:build` 실행해 성공 확인. DB 서비스 컨테이너는 아직 리포지토리/테스트가 없어 미추가(product-service도 동일 상태) — 통합 테스트 생기면 auth-service의 MySQL 컨테이너 패턴 참고해 Postgres로 추가 |
| 2026-09-15 | product-service 시드(UNIT 213건)를 `dblink`로 가져와 `wms_product`를 채우는 `db/seed/seed_wms_product.sql` 작성, `docker-compose.yml`에 `extra_hosts` 추가 | 완료 | 실제 컨테이너 대상으로 실행해 213건 삽입(REFRIGERATED 103/ROOM_TEMPERATURE 97/FROZEN 13) 및 id·sku_code·name 일치 확인, 재실행 시 `ON CONFLICT`로 중복 없음도 확인. `barcode`/`box_unit_qty`/`pallet_box_qty`/`safety_stock`은 원본에 없어 임의 기본값 |
| 2026-09-15 | `db/seed/seed_wms_warehouse.sql` 작성 — 실제 컬리 물류센터/컬리나우 매장 8곳 warehouse 시드 + 김포물류센터(GIMPO_DC) location 예시 53건 | 완료 | 실제 컨테이너에 실행해 창고 8/로케이션 53건 삽입 확인, storage_type별 보관존(24)·피킹존(27)·버퍼(2) 구성, 재실행 시 `ON CONFLICT`로 중복 없음 확인. `code`/`is_active`/로케이션 레이아웃은 원본에 없어 임의로 채움 |
| - | Repository(2파일 구조)·Service·Controller 구현 | 계획 | 엔티티만 우선 반영, [erd-spec.md](./erd-spec.md)의 미결 사항(로케이션 주소 체계, 재고 예약 시점 등) 및 `api-spec/` 계약 먼저 확정 필요 |
| 2026-09-15 | `POST /internal/v1/wms/inbounds/asn` 구현 — `InboundOrderService`/`InboundInternalController`/DTO/Repository(2파일 구조: `InboundOrderRepository`+`InboundOrderJpaRepository`, `InboundItem`/`Warehouse`/`WmsProduct`는 plain JPA repo). `InboundOrder`에 누락됐던 `po_number` 컬럼도 추가(`V2` 마이그레이션) | 완료 | 실제 서버 기동 후 curl로 성공/창고없음/상품없음/유효성검증 4가지 케이스 검증, 트랜잭션 롤백(상품 못 찾으면 InboundOrder도 안 남음) 확인. `/internal/**`이라 order/user-service 패턴대로 `@PublicApi` 사용(사용자 JWT 컨텍스트 없는 서비스 간 호출) |
| 2026-09-15 | `POST /api/v1/wms/inbounds/inspect` 구현 — 입고 확정 플로우 1~2단계(검수 완료 + InboundOrder 상태 갱신 + 버퍼 로케이션 Inventory 생성/증가). `InboundItem.inspect()` 시그니처 확장(lotNo/expiredDate/targetLocation), `Inventory.receive()` 신규, `LocationJpaRepository`/`InventoryJpaRepository` 신규. TypeSpec `InspectItemRequest`도 요청 형태에 맞춰 갱신(`inboundItemId`를 path→body로, inboundUnit/targetLocationId nullable 추가) | 완료 | 실제 서버로 전 시나리오 검증: 단위 미지정(ASN 값 사용)/오버라이드(PALLET로 재계산) 둘 다 정상, 마지막 아이템 검수 시 InboundOrder가 COMPLETED+completedDate 자동 반영, 동일 상품/LOT/유통기한 재검수 시 Inventory가 새 행이 아니라 기존 행에 누적(800→850) 확인, 없는 item/location/유효성검증 실패 3가지 에러 + 트랜잭션 롤백 확인. targetLocationId=null(자동 추천)은 로케이션 배정 전략이 erd-spec.md 미결 사항이라 추천 로직 없이 비워두기만 함(3단계 put-away 구현 시 채울 예정) |
| 2026-09-16 | `Location.zone`을 자유 문자열에서 `Zone{BUFFER,PICKING,STORAGE}` enum으로 변경(`V3` 마이그레이션, 기존 값은 `location_type` 기준 정규화), `api-spec`/`erd-spec.md` 동기화, put-away/confirm API 설명 정정 | 완료 | 실제 서버 기동으로 Flyway V3 적용 확인(`location` 53건 zone 값 BUFFER 2/STORAGE 24/PICKING 27로 정규화), `chk_location_zone` CHECK 제약 확인 |
| 2026-09-16 | `POST /api/v1/wms/inbounds/inspect`의 `targetLocationId=null`(자동 추천) 분기 구현 — 보관존(STORAGE) PALLET_RACK 중 완전히 빈 로케이션을 aisle/rack/level/bin 오름차순 1건 추천(`LocationJpaRepository.findAvailableLocations`, `NOT EXISTS` 서브쿼리로 유효 재고/진행 중 이동 지시 배제), 없으면 `WmsErrorCode.NO_AVAILABLE_LOCATION`(`WMS409`) | 완료 | 실제 서버로 검증: 정상 추천(최전방 1건), 후보 전부 점유 시 409 확인 후 테스트 데이터 정리. 사용자가 준 규칙에서 두 가지를 바꿔 구현함 — ① `location_type`을 `inboundUnit`에 따라 SHELF_BIN으로도 분기하는 조건은 `zone=STORAGE`와 결합하면(SHELF_BIN은 항상 zone=PICKING) 항상 0건이 되는 모순이 있어, put-away는 개념상 항상 대량 보관용 PALLET_RACK만 대상으로 한다고 보고 고정함(피킹존 보충은 별도 REPLENISHMENT 영역). ② 예외를 `IllegalStateException` 대신 프로젝트 컨벤션인 `BusinessException`+`WmsErrorCode`(신규 파일, `ErrorCode` 구현)로 처리해 `ApiResponse.error()` JSON 응답이 나가도록 함 |
| 2026-09-16 | 입고 확정 플로우 3단계(StockMovement 생성) 구현 — `inspect()`가 버퍼 로케이션 재고 증가 뒤, `movement_type=PUT_AWAY`/`status=PENDING`/`from=버퍼`/`to=target_location_id`인 적치 작업 지시를 `StockMovementJpaRepository`(신규, plain JpaRepository)로 생성. `InboundUnit→MovementUnit` 매핑(PALLET→PALLET, CARTON·BOX→BOX) 추가 | 완료 | 실제 서버로 검증: ASN 생성→검수(자동 추천) 후 `stock_movement` 1건이 from=DOCK(BUFFER)/to=추천된 STORAGE 로케이션/PENDING으로 정확히 생성됨 확인, 테스트 데이터 정리. 물리 이동 완료 처리(StockMovement.complete() + 재고 위치 이전 + `InboundItem.putAway()`)는 put-away/confirm API 구현 시점(4단계 이전 별도 작업)으로 남겨둠 |
| 2026-09-16 | 입고 확정 플로우 4단계(상품 도메인 이벤트 발행)의 TypeSpec 계약 작성 — `wms.inbound.completed` 이벤트(producer: wms, consumer: product, exchange `wms.topic.exchange`, queue `product.inbound.completed.queue`) 신규. `routes/events/inbound.events.tsp`(신규 파일, `inventory.events.tsp`와 도메인 분리) + `models/events.dto.tsp`에 `InboundCompletedEventPayload` 추가 | 완료 | `npm run build`/`lint` 통과(에러 0, warning 21→22 — 신규 orphan 스키마 1개는 기존 두 이벤트와 동일 패턴이라 정상), `sync:notion:events:dry`로 3개 이벤트 전부 파싱/블록 생성 확인. Java 발행자(Outbox 패턴 재사용 예정)·product-service 소비자 둘 다 아직 미구현 — 계약만 우선 정의, 실 연동 전 컨슈머 팀 확인 필요하다고 이벤트 설명에 명시 |
| 2026-09-16 | `POST /api/v1/wms/inbounds/put-away/confirm` 구현 — 버퍼→목표 로케이션 물리 이동 완료 처리. 대기 중인 PUT_AWAY StockMovement를 `inbound_item_id`로 직접 찾아 완료 처리(`reassignTarget`+`complete`), `Inventory.remove()`(신규) + `receive()`로 버퍼→목표 로케이션 재고 이전, `InboundItem.putAway()`로 상태 전환. `stock_movement.inbound_item_id` FK 추가(`V4` 마이그레이션) | 완료 | 실제 서버로 검증: 추천 로케이션과 다른 로케이션으로 확정해도 정상 반영(재고 이전 + StockMovement.to_location_id 갱신) 확인, 이미 PUT_AWAY/아직 PENDING 상태에서 재확정 시도 시 `WMS4092`(409) 확인, 존재하지 않는 inboundItemId/targetLocationId 시 404 확인. location 기준으로 대기 작업 지시를 찾으면 작업자가 추천과 다른 곳에 적치했을 때 매칭이 깨져서, StockMovement에 `inbound_item_id`를 직접 연결하는 방식으로 설계함(REPLENISHMENT/RELOCATION은 입고 건과 무관해 NULL 허용) |
| 2026-09-16 | `PutAwayConfirmRequest`를 `{inboundItemId, targetLocationId}`에서 `{stockMovementId, targetLocationId}`로 변경 — 작업 지시(StockMovement) 자체의 id로 직접 조회하는 편이 "이 검수 건의 PENDING PUT_AWAY 작업 지시"를 역으로 찾는 것보다 단순하고 애매함이 없음. `InboundItemResponse`에 `stockMovementId` 필드 추가(inspect 응답에 실려 옴 — PDA가 그대로 confirm에 재사용). `StockMovementJpaRepository`의 커스텀 조회 메서드 제거(표준 `findById`로 충분해짐), `WmsErrorCode.INVALID_INBOUND_ITEM_STATUS`→`INVALID_STOCK_MOVEMENT_STATUS`로 개명(검증 대상이 InboundItem이 아니라 StockMovement 자체의 movementType/status가 됨) | 완료 | 실제 서버로 재검증: inspect 응답의 stockMovementId로 confirm 성공(추천과 다른 로케이션 지정 포함), 이미 COMPLETED인 stockMovementId 재확정 시 409, 존재하지 않는 stockMovementId 시 404 확인. TypeSpec(`PutAwayConfirmRequest`, `InboundItemResponse`) 동기화 및 `npm run build/lint` 통과 |
| 2026-09-16 | `wms.inbound.completed` 이벤트 발행 구현(Outbox 패턴) — product-service의 검증된 RabbitMQ 아웃박스 구조(`ProductOutbox`/`OutboxService`/`OutboxPublishService`/`OutboxEagerPublisher`/`OutboxPublishScheduler`/`RabbitEventPublisher`)를 `Wms*` 이름으로 그대로 이식. 기존 `Outbox` 엔티티(erd-spec의 범용 aggregate_type/aggregate_id/event_type, Kafka 전제 설계, 발행자 없이 방치돼 있었음)를 `WmsOutbox`(exchange/routing_key/type_id/published_at)로 교체(`V5` 마이그레이션, 테이블명도 `wms_outbox`). `InboundOrderService.inspect()`가 StockMovement 생성 직후 같은 트랜잭션에서 `outboxService.recordInboundCompleted(...)` 호출 → 커밋 후 `OutboxEagerPublisher`가 즉시 발행 시도, 실패하면 `OutboxPublishScheduler`(5초 주기)가 재시도 | 완료 | 실제 서버 + RabbitMQ(`kurly-rabbitmq`)로 검증: (1) 큐 미존재 상태에서 커밋 직후 발행 시도 → 브로커가 unroutable로 반려 → 아웃박스 PENDING 유지, 5초 뒤 스케줄러 재시도 로그 확인(설계대로 동작), (2) 관리 API로 테스트 큐를 `wms.topic.exchange`에 바인딩한 뒤 다음 재시도에서 ACK 수신 → 아웃박스 PUBLISHED 전환 + 큐에 실제 메시지 도달, `__TypeId__` 헤더/payload 필드 전부 계약과 일치 확인. product-service 쪽 소비자는 여전히 미구현(이번 범위 아님) — 테스트 큐/바인딩/데이터는 정리함 |
| 2026-09-16 | `GET /api/v1/wms/inventories/movement` 신설 — StockMovement 목록 조회(warehouseId/movementType/status 선택 필터, limit 기본 50·최대 200, createdAt 오름차순). PDA 작업자가 대기 중인 적치/보충/이동 작업을 창고 단위로 훑어보는 용도. `put-away/recommendation`(inboundItemId 1건 미리보기, inbound 도메인)과는 성격이 달라 inventory 도메인(`inventory.api.tsp`)에 배치 — 기존 recommendation API는 그대로 유지 | 완료 | `StockMovementResponse`에 lotNo/expiredDate/inboundItemId/unitQuantity/quantity/createdAt/completedAt 보강(TypeSpec+Java 동시 반영). 실제 서버로 검증: warehouseId+movementType+status 복합 필터, 필터 없음(default limit), 존재하지 않는 warehouseId(빈 배열), limit 파라미터, confirm 이후 status=PENDING→COMPLETED 필터 전환까지 전부 확인. `npm run build/lint` 통과(경고는 신규 오퍼레이션 1개분만 증가, 기존 패턴과 동일) |
| 2026-09-16 | `put-away/recommendation`을 "단건 미리보기 조회"에서 "대기 중인 적치 작업 지시의 목표 로케이션 재추천/재배정"으로 재정의(`GET`→`POST`, `inboundItemId`→`stockMovementId`). `InboundItem.reassignTargetLocation()`(신규) + `StockMovement.reassignTarget()`(기존 재사용)로 InboundItem/StockMovement 양쪽을 함께 갱신. confirmPutAway와 재추천이 공유하는 "PENDING PUT_AWAY 작업 지시 조회+검증" 로직을 `findPendingPutAwayMovement()`로 추출해 중복 제거 | 완료 | 실제 서버로 검증: 원래 추천 로케이션(3)과 다른 로케이션(15)으로 재배정되는지 확인(자기 자신의 PENDING 작업 지시가 원래 로케이션을 점유 중이라 자동 제외됨), InboundItem.targetLocation/StockMovement.toLocation 둘 다 갱신 확인, 이미 COMPLETED인 작업 지시에 재추천 시도 시 409(WMS4092), 존재하지 않는 stockMovementId 시 404 확인. TypeSpec(`PutAwayRecommendationRequest` 신규, `PutAwayRecommendationResult`에 stockMovementId 추가) 동기화 및 build/lint 통과 |
| 2026-09-16 | TypeSpec→Notion 동기화 로직/CI를 다른 서비스도 쓸 수 있게 일반화 — `scripts/sync-notion-{db,events}.js`+`scripts/lib/notion-openapi.js`를 리포 루트 공용 패키지 `tools/notion-sync-kit`(`file:` 의존성)로 추출, `DEFAULT_DOMAIN='WMS'` 하드코딩을 `SERVICE_DOMAIN` 환경변수로 교체, `PROJECT_ROOT` 계산을 `import.meta.url` 기반에서 `process.cwd()` 기반으로 변경(공용 패키지에서 실행돼도 호출한 서비스의 api-spec 디렉터리를 가리키도록). CI의 `sync-notion` job도 `.github/workflows/reusable-notion-sync.yml`(`workflow_call`)로 추출, `ci-wms-service.yml`은 `working-directory`/`domain: WMS`만 넘겨 호출하도록 축소. `.env.example` 신규 추가(기존에 문서만 있고 파일은 없었음) | 완료 | 리팩터링 전후 `sync:notion:dry`/`sync:notion:events:dry` 결과 비교로 동작 동일함 확인(도메인 값 `WMS`로 동일하게 채워짐), `node_modules` 삭제 후 `npm install`/`npm ci` 클린 설치로 `file:` 의존성 해석 확인, `SERVICE_DOMAIN` 미설정 시 fail-fast 확인, 재사용 워크플로우 YAML 문법 검증 완료(실제 GitHub Actions 트리거는 미검증). 다른 서비스 부트스트랩용 템플릿은 `tools/notion-sync-kit/template/`에 추가(실제 다른 서비스의 TypeSpec 명세 작성은 이번 범위 아님) |
| - | Postman 팀 워크스페이스 자동 동기화 스크립트 | 계획 | Postman API Key/컬렉션 UID 발급 후 진행 ([6.4](#64-postman-동기화) 참고) |
