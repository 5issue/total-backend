# market-kurly-clone — 백엔드

마켓컬리를 참고한 **이커머스 + 풀필먼트** 백엔드. Gradle 멀티모듈 MSA로 8개 서비스를 운영한다.

`Java 25` · `Spring Boot 4.1.0` · `Gradle 9.0.0` · `MySQL 8.4` / `PostgreSQL` · `RabbitMQ` · `Redis` · `EKS` + `ArgoCD`

---

## 빠르게 시작하기

```bash
git clone <레포 주소> && cd total-backend

cp .env.example .env          # 최초 1회
docker compose up -d          # 공통 인프라 + 일부 서비스 DB
docker compose ps             # 전부 healthy 확인

./gradlew build               # 전체 빌드 + 테스트
./gradlew :auth-service:bootRun
```

| 주소 | 용도 |
| --- | --- |
| http://localhost:15672 | RabbitMQ 관리 UI (`guest` / `guest`) |
| http://localhost:8080 | 통합 Swagger UI |

> **`docker compose up -d`로 전부 뜨지 않는다.** 루트 compose에는 auth·payment·user·scm의 DB만
> 있다. 나머지는 각 서비스가 자기 compose 파일을 갖는다 — [로컬 인프라](#로컬-인프라) 참조.

---

## 서비스 구성

| n | 서비스 | 역할 | 앱 | DB |
| ---: | --- | --- | ---: | --- |
| 1 | `auth-service` | 소셜 로그인, JWT 발급·검증, 세션, 관리자 인증 | 8081 | MySQL 3307 |
| 2 | `order-service` | 장바구니, 주문, 취소·반품 | 8082 | MySQL 3308 |
| 3 | `payment-service` | PG 연동, 결제 승인·취소, 아웃박스 | 8083 | MySQL 3309 |
| 4 | `product-service` | 상품, 카테고리, 재고 | 8084 | PostgreSQL 5436 |
| 5 | `wms-service` | 입고 검수·적치, 창고 재고 | 8085 | PostgreSQL 5437 |
| 6 | `oms-service` | 주문 이행, 출고 지시, 배송 약속 | 8086 | PostgreSQL 5438 |
| 7 | `scm-service` | 공급망 (골격만 구현됨) | 8087 | PostgreSQL 5439 |
| 8 | `user-service` | 회원 프로필, 배송지 | 8088 | MySQL 3314 |

포트는 서비스 번호 `n`에 대해 앱 `8080+n`, MySQL `3306+n`, PostgreSQL `5432+n`이다.
자세한 규칙은 [service-port-convention.md](docs/service-port-convention.md).

**`common`** 은 실행 서비스가 아니라 전 서비스가 의존하는 라이브러리다. 아래 참조.

---

## common 모듈

단순 유틸이 아니라 **Spring Boot 자동 구성 라이브러리**다. 여기에 전역 빈이나 핸들러를 추가하면
`common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
에 **반드시 등록**해야 적용된다.

| 구성 | 내용 |
| --- | --- |
| 인증 처리기 | JWT 검증 + 역할 인가. `kurly.security.enabled=true`인 서비스에서만 동작 |
| 응답 규약 | `ApiResponse<T>` — `status`/`message`/`data`/`error`/`timestamp` |
| 예외 처리 | `GlobalExceptionHandler` — 서비스마다 `@RestControllerAdvice`를 새로 만들지 않는다 |
| 메시징 | RabbitMQ JSON `MessageConverter` |
| 설정 가드 | `UnresolvedPlaceholderGuard` — 운영에서 환경변수가 빠지면 기동 전에 중단 |

Spring Boot 4라 Jackson은 **Jackson 3**(`tools.jackson.databind.json.JsonMapper`)를 쓴다.
`com.fasterxml.jackson.databind.ObjectMapper`가 아니다.

---

## 인증·인가

게이트웨이를 두지 않고 **각 서비스가 진입부**가 되어 스스로 JWT를 검증한다. 인증을 한곳에 모으면
그 지점이 병목이 되고, 소유권 검사는 결국 각 서비스의 데이터를 봐야 하기 때문이다.

```
[클라이언트] ──access token──▶ [ALB] ──▶ [각 서비스]
                                           ├ JWT 서명·만료 검증 (JWKS 공개키)
                                           ├ 역할 인가 (user / admin)
                                           └ 소유권 검사 (서비스 책임)
```

- 서명 **ES256**, 운영은 **KMS**로 서명해 개인키를 프로세스가 들고 있지 않는다
- 공개키는 `GET /.well-known/jwks.json`으로 배포. 회전 중에는 키가 둘이므로 `kid`로 고른다
- access 30분(관리자 15분) / refresh 14일. refresh는 **회전**하며 재사용이 감지되면 전 세션 폐기
- refresh 토큰은 **RDB에 해시로** 저장한다. Redis는 캐시이지 인증 저장소가 아니다

### 엔드포인트마다 애노테이션이 필수다

```java
@PublicApi        // 인증 불필요
@Authenticated    // 로그인 필요
@RequireRole(Role.ADMIN)
```

**하나라도 빠뜨리면 서버가 기동되지 않는다.** 인가 누락은 런타임에 오류 없이 그냥 열리므로,
`HandlerAuthorizationAuditor`가 포트가 열리기 전에 중단시킨다. 메서드 표기가 클래스 표기보다 우선한다.

인증 주체는 `@AuthPrincipal AuthenticatedPrincipal` 파라미터로 받는다. `ThreadLocal`은 쓰지 않는다.

적용 절차는 [공통 인증처리기 적용가이드](docs/공통_인증처리기_적용가이드.md),
규격은 [인증인가 설계서 v2.1](docs/인증인가_설계서_v2.1.md).

---

## 로컬 인프라

### 루트 compose — 공통 인프라 + 일부 DB

```bash
docker compose up -d        # RabbitMQ, Redis, Swagger UI, auth·payment·user·scm DB
docker compose down         # -v 를 붙이면 볼륨까지 삭제된다
```

### 서비스별 compose — 나머지 DB

```bash
docker compose -f services/order-service/docker-compose.local.yml up -d
docker compose -f services/oms-service/docker-compose.local.yml   up -d
docker compose -f services/product-service/docker-compose.yml     up -d
docker compose -f services/wms-service/docker-compose.yml         up -d
```

compose 프로젝트가 나뉘어 있어 **`docker compose down`은 루트 것만 내린다.** 전부 내리려면
`-p <프로젝트명>`으로 각각 처리한다(`order-service`, `oms-service`, `product-service`, `wms-service`).

---

## 개발

```bash
./gradlew build                      # 전체 빌드 + 테스트 + 커버리지 검증
./gradlew :auth-service:build        # 모듈 경로는 services/ 없이 :서비스명
./gradlew :auth-service:bootRun
./gradlew test                       # 전체 테스트
./gradlew :order-service:test --tests 'com.kurly.order.SomeTest'
./gradlew :order-service:test --tests 'com.kurly.order.SomeTest.메서드명'
```

`settings.gradle`이 `services/*`를 최상위 이름으로 매핑하므로 Gradle 경로가 평평하다
(`:services:auth-service`가 아니라 `:auth-service`).

린터·포매터는 설정되어 있지 않다. 코드 스타일은 [팀컨벤션](docs/팀컨벤션-코드스타일.md)을 따른다.

### 테스트 커버리지

Jacoco 리포트는 전 모듈에서 생성되지만, **임계값 검증은 서비스별로 적용**한다. 테스트가 아직
없는 서비스의 빌드를 깨뜨리지 않기 위해서다.

| 모듈 | 게이트 |
| --- | --- |
| `auth` · `payment` · `user` | 라인 80% / 브랜치 80% |
| `oms` | 70% / 70% |
| `common` | 65% / 75% — 현재 수준을 지키는 래칫. 단계적으로 80%까지 올린다 |
| 그 외 | 게이트 없음 |

리포트는 `<모듈>/build/reports/jacoco/test/html/index.html`.

### 일부 서비스의 통합 테스트는 기본적으로 건너뛴다

실제 DB·외부 환경이 필요한 테스트는 환경변수 가드가 걸려 있다.

```bash
AUTH_INTEGRATION_TEST=true ./gradlew :auth-service:test
```

---

## API 규약

모든 컨트롤러는 `ApiResponse<T>`로 감싼 응답을 돌려준다(UTC ISO-8601).

```json
{ "status": "SUCCESS", "message": "...", "data": { }, "error": null,
  "timestamp": "2026-10-02T00:00:00Z" }
```

예외는 `BusinessException`을 상속하고 `ErrorCode`를 구현한 도메인별 enum으로 확장한다.
공통 코드는 `GlobalErrorCode`(`COMMON400`, `COMMON401`, …). 인증·인가 실패는 401과 403을 구분하되
**실패 사유를 과도하게 노출하지 않는다.**

**내부 전용 경로는 `/internal/v1/...`** 로 통일한다. 인그레스가 `/internal/**` 접두어로 외부 접근을
차단하므로, 중간에 `internal`이 들어간 경로(`/api/v1/internal/...`)는 **차단되지 않는다.**

---

## 배포

```
develop에 push
   → CI: 빌드·테스트 (서비스별 paths 필터)
   → CD: 멀티 아키텍처 이미지 빌드 → ECR push
       → total-k8s 레포의 이미지 태그 갱신 커밋
           → ArgoCD가 감지해 자동 배포
```

- CI/CD는 `.github/workflows/{ci,cd}-<서비스>.yml`. `common`이나 빌드 스크립트가 바뀌면 함께 돈다
- `pr-notify.yml`이 워크플로 완료를 받아 Discord로 알린다. **CI 워크플로 이름을 바꾸면 이 목록도 함께 고쳐야 한다**
- 운영 설정은 환경변수로 주입한다. 빠지면 `UnresolvedPlaceholderGuard`가 기동을 중단시킨다

---

## 서비스를 추가할 때

함께 손봐야 하는 곳이다. 하나라도 빠지면 조용히 어긋난다.

1. `settings.gradle`의 `serviceModules`
2. [service-port-convention.md](docs/service-port-convention.md) 표
3. `.github/workflows/ci-<서비스>.yml`, `cd-<서비스>.yml`
4. `pr-notify.yml`의 `workflows` 목록
5. DB가 필요하면 `docker-compose.yml` 또는 서비스별 compose

---

## 문서

### 설계·규격

| 문서 | 내용 |
| --- | --- |
| [인증인가 설계서 v2.1](docs/인증인가_설계서_v2.1.md) | 보안팀 확정 규격. 토큰·키·서비스 간 통신 |
| [공통 인증처리기 적용가이드](docs/공통_인증처리기_적용가이드.md) | 각 서비스에 인증을 붙이는 실무 절차 |
| [백엔드 시큐어코딩 가이드](docs/백엔드_시큐어코딩가이드.md) | BE-01~BE-20 체크리스트. 코드 리뷰 기준 |
| [보안 정책 가이드](docs/보안_정책가이드.md) | 정책 기준 |
| [Repository 아키텍처 계층 분리 및 구현 전략](docs/Repository%20아키텍처%20계층%20분리%20및%20구현%20전략.md) | 도메인·인프라 계층 분리 |
| [팀컨벤션-코드스타일](docs/팀컨벤션-코드스타일.md) | 코드 스타일 |
| [service-port-convention](docs/service-port-convention.md) | 로컬 포트 규칙 |

### 통신 명세

| 문서 | 내용 |
| --- | --- |
| [주문-결제 시퀀스 다이어그램](docs/주문-결제%20시퀀스%20다이어그램%200.6v.md) | 결제 흐름 전체 |
| [주문 서비스 통신 명세서](docs/주문%20서비스%20통신%20명세서.md) | 주문 연동 |
| [세션활동 이벤트 통신명세](docs/세션활동_이벤트_통신명세.md) | 유휴 세션 차단용 활동 이벤트 |

### 서비스별 API·ERD

| 서비스 | 문서 |
| --- | --- |
| auth | [인증 API 명세서](services/auth-service/docs/인증%20API%20명세서.md) · [회원 인증 ERD](services/auth-service/docs/회원%20인증%20ERD.md) |
| order | [api-spec-order](services/order-service/docs/api-spec-order.md) |
| payment | [결제 API 명세서](services/payment-service/docs/결제%20API%20명세서.md) · [결제 ERD](services/payment-service/docs/결제%20ERD.md) |
| user | [회원 API 명세서](services/user-service/docs/회원%20API%20명세서.md) |
| wms | [erd-spec](services/wms-service/docs/erd-spec.md) |

---

## 협업 규칙

- 기본 브랜치는 **`develop`**. 브랜치 이름은 `feat/auth-init#6`처럼 `타입/설명#이슈번호`
- 커밋 메시지는 **한국어**. `feat:` `fix:` `docs:` `chore:` + 설명
- PR·이슈·코드리뷰 모두 한국어 기준이다
- CodeRabbit 리뷰는 오버엔지니어링·중복·불필요한 의존성·Spring Boot 4 / Gradle 9 비호환을 중점적으로 본다.
  **현재 요구사항 이상으로 추상화하지 않는다**

### 서비스 담당

서비스마다 담당자가 있다. 전체를 훑는 작업이라도 **담당 서비스만 고치고 나머지는 인계 항목으로
보고한다.** 불가피하게 다른 영역을 건드렸다면 PR 본문에 그 사실과 확인이 필요한 지점을 적는다.
