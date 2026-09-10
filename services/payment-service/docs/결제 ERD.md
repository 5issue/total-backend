> 구현 기준은 `services/payment-service/src/main/resources/db/migration/` 의 마이그레이션이다
> (`V0__init_payment.sql`).
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
	`status`	VARCHAR(20)	NOT NULL,   -- REQUESTED / SUCCESS / FAILED / CANCELED
	`receipt_url`	VARCHAR(255)	NULL,  -- PG 영수증 URL. 승인 응답으로 확정된다
	`requested_at`	DATETIME(6)	NOT NULL,
	`approved_at`	DATETIME(6)	NULL,
	`canceled_at`	DATETIME(6)	NULL,
	`success_order_id`	BIGINT	GENERATED ALWAYS AS (IF(status='SUCCESS', order_id, NULL)) VIRTUAL
);
-- INDEX (order_id), INDEX (user_id), UNIQUE (success_order_id)
--
-- 한 주문에 성공한 결제는 최대 하나여야 한다. order_id에 그냥 UNIQUE를 걸 수는 없다 — 승인
-- 실패(FAILED) 후 재시도하면 행이 하나 더 생긴다. "성공한 것만 하나"는 부분 유니크 인덱스가
-- 필요한데 MySQL이 지원하지 않으므로, 성공일 때만 값이 생기는 생성 컬럼에 유니크를 건다.
-- NULL은 중복이 허용되므로 실패·취소 건은 걸리지 않고, 취소되면 제약이 풀려 재결제가 가능하다.
--
-- 제약은 PG 승인 후에 걸리므로 위반 시 방금 승인분을 보상 취소해야 한다(PaymentCheckoutService).

-- 부분 취소는 기획상 제공하지 않는다(전액 취소만). 스키마와 상태값(PARTIAL_CANCELED)은 지원하지만
-- API로 금액을 받지 않으므로 현재 경로에서는 항상 전액이다. 반품 정책상 부분 환불이 필요해지면
-- 요청 필드만 열면 된다.
CREATE TABLE `payment_cancels` ( -- 결제 취소 이력
	`id`	BIGINT	NOT NULL,
	`payment_id`	BIGINT	NOT NULL,      -- FK → payments.id
	`cancel_reason`	VARCHAR(255)	NULL,
	`cancel_amount`	BIGINT	NOT NULL,   -- 원 단위 정수. 취소 건별 금액
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
	`request_fingerprint`	CHAR(64)	NOT NULL,  -- 요청 본문 SHA-256
	`status`	VARCHAR(20)	NOT NULL,     -- IN_PROGRESS / COMPLETED
	`response_status`	INT	NULL,
	`response_body`	JSON	NULL,
	`created_at`	DATETIME(6)	NOT NULL,
	`updated_at`	DATETIME(6)	NOT NULL
);
-- UNIQUE (user_id, idempotency_key)
--
-- 멱등키 자체를 PK로 쓰지 않는다. 랜덤 UUID를 PK로 두면 InnoDB가 그 값으로 클러스터링해
-- 삽입이 인덱스 전역에 흩어져 페이지 분할이 잦아진다. AUTO_INCREMENT PK는 순차 삽입이라 그 비용이 없다.
--
-- 요청 본문은 원문이 아니라 지문으로 보관한다. JSON 컬럼은 MySQL이 키 순서와 공백을 정규화해
-- 되읽은 값이 직렬화 원문과 절대 일치하지 않으므로, 원문 비교는 항상 "다른 본문"으로 판정된다.
-- 결제 본문에는 PG 인증 토큰이 들어 있어 원문을 남기지 않는 편이 안전하기도 하다.
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
--
-- ## 취소 실패의 처리 흐름
--
-- 1. PG 취소가 실패하면 `payment_cancels`에 FAILED로 남기고 이 큐에 작업을 넣는다.
-- 2. 배치 스케줄러가 `next_retry_at`이 지난 PENDING 건을 집어 다시 시도한다.
--    간격은 지수 백오프(1분 → 2 → 4 → 8분)로 늘린다. 고정 간격이면 PG 장애가 길어질 때
--    같은 부하를 계속 실어 회복을 방해한다.
-- 3. 상한(5회)에 도달하면 **영구 실패(FAILED)** 로 전환하고 배치는 더 이상 집어가지 않는다.
--    `next_retry_at`을 NULL로 비워 조회 조건에서 빠지게 한다. (여기까지 구현됨)
-- 4. **영구 실패 건은 관리자 대시보드에 노출한다.** 자동으로 되돌릴 방법을 다 쓴 상태이므로
--    사람이 PG 관리자 콘솔에서 직접 취소하거나 고객에게 개별 안내해야 한다.
--    조회 조건은 `status = 'FAILED'`이며 `last_error`에 마지막 실패 사유가 남아 있다.
--    **대시보드와 알림은 미구현이다.** 현재는 이 테이블을 직접 조회해야 확인할 수 있다.
--
-- 여기서 멈추면 고객 돈이 묶인 채로 남는다. 4번이 없으면 3번의 영구 실패를 아무도 모른다.
```
