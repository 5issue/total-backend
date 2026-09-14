# wms-service 개발 하네스 (CLAUDE.md)

`product-service`의 실제 구현 스타일과 팀 컨벤션 문서(`/docs/팀컨벤션-코드스타일.md`, `/docs/Repository 아키텍처 계층 분리 및 구현 전략.md`)를 근거로 정리한 wms-service 전용 개발 가이드입니다. Claude(및 팀원)가 이 서비스에서 코드를 작성할 때 참고하는 단일 문서로 관리합니다.

> 이 문서는 프로젝트 루트가 아니라 `wms-service/docs/`에 있으므로 Claude Code가 자동으로 로드하지 않습니다. 세션 시작 시 필요하면 이 파일을 직접 읽어서 컨텍스트로 사용하세요.

---

## 1. 서비스 개요

- 모듈명: `wms-service` (`com.kurly.wms`)
- 역할: 창고 관리(입고 → 적치/보충 → 재고 현행화 → 출고), FEFO 기준 LOT 관리
- 로컬 포트: 앱 `8085`, PostgreSQL `5437` (근거: [service-port-convention.md](../../../docs/service-port-convention.md))
- 상세 데이터 모델: [erd-spec.md](./erd-spec.md)
- API 명세: TypeSpec (`api-spec/main.tsp`)
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

## 6. 참고 문서

- [팀컨벤션-코드스타일.md](../../../docs/팀컨벤션-코드스타일.md)
- [Repository 아키텍처 계층 분리 및 구현 전략.md](../../../docs/Repository%20아키텍처%20계층%20분리%20및%20구현%20전략.md)
- [service-port-convention.md](../../../docs/service-port-convention.md)
- [백엔드_시큐어코딩가이드.md](../../../docs/백엔드_시큐어코딩가이드.md)
- [erd-spec.md](./erd-spec.md) — wms-service 데이터 모델
- product-service 실제 구현체: `services/product-service/src/main/java/com/kurly/product/`

## 7. 개발 진행 상황

새 작업을 시작/완료할 때 아래 표에 이어서 기록합니다. 상태는 `계획` / `진행중` / `완료` / `보류` 중 하나.

| 날짜 | 작업 | 상태 | 비고 |
| --- | --- | --- | --- |
| 2026-09-14 | PostgreSQL `docker-compose.yml` 추가, `build.gradle` mysql→postgresql 드라이버 교체, `application.yml`을 local/prod로 분리 | 완료 | 포트 5437, env var 이름 product-service와 동일 |
| 2026-09-14 | 본 하네스 문서(`docs/CLAUDE.md`) 작성 | 완료 | product-service 실제 코드 + 팀컨벤션 문서 기준 |
| 2026-09-14 | Flyway 도입 (`V1__baseline_schema.sql`), ERD 기반 JPA 엔티티 10종(`Warehouse`, `WmsProduct`, `Location`, `InboundOrder`, `InboundItem`, `Inventory`, `StockMovement`, `OutboundOrder`, `OutboundItem`, `Outbox`) 구현 | 완료 | `ddl-auto: validate`로 기동 검증 완료. 공유 enum(`StorageType`, `OutboxStatus`)은 `domain/enums`, 엔티티 전용 enum은 nested로 배치 |
| - | Repository(2파일 구조)·Service·Controller 구현 | 계획 | 엔티티만 우선 반영, [erd-spec.md](./erd-spec.md)의 미결 사항(로케이션 주소 체계, 재고 예약 시점 등) 먼저 확정 필요 |
| - | API 구현 (TypeSpec `api-spec/main.tsp` 기반) | 계획 | 입고/출고/재고 조회 엔드포인트 |
