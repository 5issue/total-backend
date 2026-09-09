-- payment-service 초기 스키마 (결제 ERD 기준)
--
-- 주문 도메인(order-service)의 orders는 별도 DB에 있으므로 FK 제약을 걸지 않는다.
-- payments.order_id / payments.user_id는 논리적 참조값이다.
--
-- 금액은 원 단위 정수(BIGINT)로 다룬다. DECIMAL로 두면 PG가 소수부를 절사·반올림했을 때
-- 승인 금액과 저장 금액이 어긋나 정산 대사가 실패한다. 원화에는 유통 보조단위도 없다.
--
-- status의 CHECK 제약은 Hibernate가 @Enumerated(STRING) 컬럼에 생성하는 것과 동일하다.
-- enum 값을 추가·삭제할 때는 이 제약도 함께 바꾸는 마이그레이션이 필요하다.

CREATE TABLE payments
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    order_id     BIGINT       NOT NULL COMMENT '주문 도메인 orders.id 참조값 (FK 아님)',
    user_id      BIGINT       NOT NULL COMMENT '결제 소유자. 토큰 sub와 비교하는 소유권 검사 기준',
    payment_key  VARCHAR(255) NULL COMMENT 'PG가 발급한 결제 식별자. 승인 전에는 없다',
    method       VARCHAR(50)  NULL COMMENT '결제 수단. 승인 응답으로 확정된다',
    total_amount BIGINT       NOT NULL COMMENT '결제 금액(원 단위 정수)',
    status       VARCHAR(20)  NOT NULL COMMENT 'REQUESTED / SUCCESS / FAILED / CANCELED / PARTIAL_CANCELED',
    requested_at DATETIME(6)  NOT NULL,
    approved_at  DATETIME(6)  NULL,
    canceled_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- PG 발급 식별자는 전역 유일하다. 승인 전 행은 NULL이며 MySQL은 NULL 중복을 허용한다.
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT payments_chk_1 CHECK (status IN
                                     ('REQUESTED', 'SUCCESS', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED')),
    CONSTRAINT payments_chk_2 CHECK (total_amount > 0),
    -- 한 주문의 결제 이력 조회, 소유자별 조회에 쓴다.
    -- order_id에 유니크를 걸지 않은 이유는 주석 하단 참조.
    INDEX idx_payments_order_id (order_id),
    INDEX idx_payments_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제';

-- order_id에 UNIQUE를 걸지 않았다. 승인 실패(FAILED) 후 같은 주문으로 재시도하면 행이 하나 더
-- 생기기 때문이다. "한 주문에 성공한 결제는 하나"는 부분 유니크 인덱스가 필요한데 MySQL이
-- 지원하지 않으므로, 서비스 계층과 멱등키가 함께 보장한다. 남은 과제로 기록한다.

CREATE TABLE payment_cancels
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    payment_id     BIGINT       NOT NULL,
    cancel_reason  VARCHAR(255) NULL COMMENT '취소 사유',
    cancel_amount  BIGINT       NOT NULL COMMENT '취소 금액(원 단위 정수). 부분 취소를 위해 건별로 남긴다',
    pg_cancel_key  VARCHAR(255) NULL COMMENT 'PG가 발급한 취소 식별자. 실패 시에는 없다',
    status         VARCHAR(20)  NOT NULL COMMENT 'REQUESTED / SUCCESS / FAILED',
    failure_reason VARCHAR(255) NULL COMMENT 'PG 취소 실패 사유. 재시도 판단 근거',
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_cancels_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT payment_cancels_chk_1 CHECK (status IN ('REQUESTED', 'SUCCESS', 'FAILED')),
    CONSTRAINT payment_cancels_chk_2 CHECK (cancel_amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제 취소 이력';

-- 멱등키.
--
-- 대리키를 PK로 둔다. 랜덤 UUID를 PK로 쓰면 InnoDB가 그 값으로 클러스터링해 삽입이 인덱스
-- 전역에 흩어지고, 이 테이블은 JSON 본문을 담아 행이 넓어 페이지 분할 비용이 크다.
--
-- 유니크를 (user_id, idempotency_key)로 잡는다. 키만으로 잡으면 다른 사용자가 우연히 같은
-- UUID를 보냈을 때 남의 응답을 그대로 돌려받는다. 결제 응답에는 금액과 영수증이 담긴다.
CREATE TABLE idempotency_keys
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id         BIGINT       NOT NULL COMMENT '요청자. 키의 유효 범위를 사용자 단위로 좁힌다',
    idempotency_key VARCHAR(64)  NOT NULL COMMENT 'Idempotency-Key 헤더값(UUID)',
    request_path    VARCHAR(255) NOT NULL COMMENT '같은 키를 다른 엔드포인트에 재사용했는지 판별한다',
    request_body    JSON         NULL COMMENT '같은 키에 다른 본문이 오면 거부하기 위해 보관한다',
    status          VARCHAR(20)  NOT NULL COMMENT 'IN_PROGRESS / COMPLETED',
    response_status INT          NULL COMMENT '완료된 응답의 HTTP 상태. 재요청 시 그대로 재생한다',
    response_body   JSON         NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT idempotency_keys_chk_1 CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제 멱등키';

-- 결제 이벤트 아웃박스.
--
-- event_id는 소비자의 중복 처리 방어 기준이다(주문-결제 시퀀스 2절 — Product가 event_id로
-- 멱등성을 검증한다). 발행이 최소 1회를 보장하므로 소비자는 같은 이벤트를 두 번 받을 수 있다.
CREATE TABLE payment_outbox
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    event_id     CHAR(36)     NOT NULL COMMENT '이벤트 UUID. 소비자의 멱등성 판단 기준',
    event_type   VARCHAR(200) NOT NULL COMMENT '예: PAYMENT_CANCELED',
    status       VARCHAR(20)  NOT NULL COMMENT 'PENDING / PUBLISHED / FAILED',
    payload      JSON         NOT NULL COMMENT 'JWT 원문은 싣지 않는다. userId 등 식별정보만 담는다',
    created_at   DATETIME(6)  NOT NULL,
    published_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_outbox_event_id UNIQUE (event_id),
    CONSTRAINT payment_outbox_chk_1 CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    -- 발행 워커가 미발행 건을 오래된 순으로 훑는다.
    INDEX idx_payment_outbox_status_created_at (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제 이벤트 아웃박스';

-- 재시도 큐. PG 취소 실패처럼 즉시 성공하지 못한 작업을 배치가 다시 집어간다
-- (주문-결제 시퀀스 1절 단계 3 — 보상 취소 실패 시 이력에 기록하고 스케줄러가 재시도).
CREATE TABLE payment_retries
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    payment_id    BIGINT       NOT NULL,
    task_type     VARCHAR(50)  NOT NULL COMMENT '예: PG_CANCEL',
    payload       JSON         NULL,
    retry_count   INT          NOT NULL COMMENT '누적 시도 횟수. 상한 초과 시 중단하고 알린다',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / SUCCESS / FAILED',
    next_retry_at DATETIME(6)  NULL COMMENT '다음 시도 시각. 지수 백오프로 늘린다',
    last_error    VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_retries_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT payment_retries_chk_1 CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT payment_retries_chk_2 CHECK (retry_count >= 0),
    -- 배치가 실행 대상을 고르는 조건.
    INDEX idx_payment_retries_status_next_retry_at (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제 재시도 큐';
