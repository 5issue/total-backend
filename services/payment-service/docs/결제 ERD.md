> 구현 기준은 `services/payment-service/src/main/resources/db/migration/V0__init_payment.sql`이다.
> 이 문서와 어긋나면 마이그레이션이 사실이다.
>
> **금액은 원 단위 정수(BIGINT)** 로 다룬다. DECIMAL로 두면 PG가 소수부를 절사·반올림했을 때
> 승인 금액과 저장 금액이 어긋나 정산 대사가 실패한다. 원화에는 유통 보조단위도 없다.
>
> **`status`는 네이티브 ENUM을 쓰지 않는다.** `VARCHAR(20)` + CHECK 제약으로 둔다.
> `ddl-auto: update`가 CHECK 제약을 갱신하지 않아 enum 값 변경 시 마이그레이션이 필요하다.

```SQL
CREATE TABLE `payments` ( -- 결제
	`id`	BIGINT	NOT NULL,           -- PK
	`order_id`	BIGINT	NOT NULL,       -- 주문 도메인 orders.id 참조값 (FK 아님, 다른 DB)
	`user_id`	BIGINT	NOT NULL,       -- 결제 소유자. 토큰 sub와 비교하는 소유권 검사 기준
	`payment_key`	VARCHAR(255)	NULL,   -- PG 발급 결제 식별자. 승인 전에는 없음. UNIQUE
	`method`	VARCHAR(50)	NULL,
	`total_amount`	BIGINT	NOT NULL,   -- 원 단위 정수
	`status`	VARCHAR(20)	NOT NULL,   -- REQUESTED / SUCCESS / FAILED / CANCELED / PARTIAL_CANCELED
	`requested_at`	DATETIME(6)	NOT NULL,
	`approved_at`	DATETIME(6)	NULL,
	`canceled_at`	DATETIME(6)	NULL
);
-- INDEX (order_id), INDEX (user_id)
-- order_id에 UNIQUE를 걸지 않았다. 승인 실패(FAILED) 후 같은 주문으로 재시도하면 행이 하나 더
-- 생기기 때문이다. "한 주문에 성공한 결제는 하나"는 부분 유니크 인덱스가 필요한데 MySQL이
-- 지원하지 않으므로 서비스 계층과 멱등키가 함께 보장한다. (남은 과제)

CREATE TABLE `payment_cancels` ( -- 결제 취소 이력
	`id`	BIGINT	NOT NULL,
	`payment_id`	BIGINT	NOT NULL,      -- FK → payments.id
	`cancel_reason`	VARCHAR(255)	NULL,
	`cancel_amount`	BIGINT	NOT NULL,   -- 원 단위 정수. 부분 취소를 위해 건별로 남긴다
	`pg_cancel_key`	VARCHAR(255)	NULL,
	`status`	VARCHAR(20)	NOT NULL,    -- REQUESTED / SUCCESS / FAILED
	`failure_reason`	VARCHAR(255)	NULL, -- 재시도 판단 근거
	`created_at`	DATETIME(6)	NOT NULL
);

CREATE TABLE `idempotency_keys` ( -- 결제 멱등키
	`id`	BIGINT	NOT NULL,               -- PK(대리키)
	`user_id`	BIGINT	NOT NULL,
	`idempotency_key`	VARCHAR(64)	NOT NULL,
	`request_path`	VARCHAR(255)	NOT NULL,
	`request_body`	JSON	NULL,
	`status`	VARCHAR(20)	NOT NULL,     -- IN_PROGRESS / COMPLETED
	`response_status`	INT	NULL,
	`response_body`	JSON	NULL,
	`created_at`	DATETIME(6)	NOT NULL,
	`updated_at`	DATETIME(6)	NOT NULL
);
-- UNIQUE (user_id, idempotency_key)
--
-- 멱등키 자체를 PK로 쓰지 않는다. 랜덤 UUID를 PK로 두면 InnoDB가 그 값으로 클러스터링해
-- 삽입이 인덱스 전역에 흩어지고, 이 테이블은 JSON 본문을 담아 행이 넓어 페이지 분할 비용이 크다.
--
-- 유니크를 사용자 단위로 좁힌다. 키만으로 잡으면 다른 사용자가 우연히 같은 UUID를 보냈을 때
-- 남의 응답(금액·영수증)을 그대로 돌려받는다.
--
-- status는 409 "처리 중"을 표현하기 위해 필요하다. 처리 전에 IN_PROGRESS로 먼저 INSERT하고
-- 완료 시 응답을 채우며 COMPLETED로 바꾼다. 유니크 위반이 곧 "이미 진행 중"의 신호가 된다.

CREATE TABLE `payment_outbox` ( -- 결제 이벤트 아웃박스
	`id`	BIGINT	NOT NULL,
	`event_id`	CHAR(36)	NOT NULL,      -- 이벤트 UUID. 소비자의 멱등성 판단 기준. UNIQUE
	`event_type`	VARCHAR(200)	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,    -- PENDING / PUBLISHED / FAILED
	`payload`	JSON	NOT NULL,          -- JWT 원문은 싣지 않는다. userId 등 식별정보만 담는다
	`created_at`	DATETIME(6)	NOT NULL,
	`published_at`	DATETIME(6)	NULL
);
-- INDEX (status, created_at) — 발행 워커가 미발행 건을 오래된 순으로 훑는다.

CREATE TABLE `payment_retries` ( -- 결제 재시도
	`id`	BIGINT	NOT NULL,
	`payment_id`	BIGINT	NOT NULL,      -- FK → payments.id
	`task_type`	VARCHAR(50)	NOT NULL,   -- 예: PG_CANCEL
	`payload`	JSON	NULL,
	`retry_count`	INT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL,    -- PENDING / SUCCESS / FAILED
	`next_retry_at`	DATETIME(6)	NULL,   -- 지수 백오프
	`last_error`	VARCHAR(255)	NULL,
	`created_at`	DATETIME(6)	NOT NULL,
	`updated_at`	DATETIME(6)	NOT NULL
);
-- INDEX (status, next_retry_at) — 배치가 실행 대상을 고르는 조건.
```
