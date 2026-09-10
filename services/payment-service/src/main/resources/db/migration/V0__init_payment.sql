
CREATE TABLE payments
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    order_id     BIGINT       NOT NULL COMMENT '주문 도메인 orders.id 참조값 (FK 아님)',
    user_id      BIGINT       NOT NULL COMMENT '결제 소유자. 토큰 sub와 비교하는 소유권 검사 기준',
    payment_key  VARCHAR(255) NULL COMMENT 'PG가 발급한 결제 식별자. 승인 전에는 없다',
    method       VARCHAR(50)  NULL COMMENT '결제 수단. 승인 응답으로 확정된다',
    total_amount BIGINT       NOT NULL COMMENT '결제 금액(원 단위 정수)',
    status       VARCHAR(20)  NOT NULL COMMENT 'REQUESTED / SUCCESS / FAILED / CANCELED',
    -- 승인 시 PG가 돌려주는 영수증 주소. 조회 때마다 PG에 물으면 읽기 경로에 외부 의존과 지연이
    -- 생기고, PG 장애가 곧 영수증 조회 장애가 된다.
    receipt_url  VARCHAR(255) NULL COMMENT 'PG 영수증 URL. 승인 응답으로 확정된다',
    requested_at DATETIME(6)  NOT NULL,
    approved_at  DATETIME(6)  NULL,
    canceled_at  DATETIME(6)  NULL,
    -- 한 주문에 성공한 결제는 최대 하나여야 한다. order_id에 그냥 UNIQUE를 걸 수는 없다.
    -- 승인 실패(FAILED) 후 같은 주문으로 재시도하면 행이 하나 더 생기기 때문이다.
    -- "성공한 것만 하나"는 부분 유니크 인덱스가 필요한데 MySQL이 지원하지 않으므로,
    -- 성공일 때만 값이 생기는 생성 컬럼에 유니크를 건다. NULL은 중복이 허용된다.
    success_order_id BIGINT GENERATED ALWAYS AS (IF(status = 'SUCCESS', order_id, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT uk_payments_success_order_id UNIQUE (success_order_id),
    CONSTRAINT payments_chk_1 CHECK (status IN ('REQUESTED', 'SUCCESS', 'FAILED', 'CANCELED')),
    CONSTRAINT payments_chk_2 CHECK (total_amount > 0),
    INDEX idx_payments_order_id (order_id),
    INDEX idx_payments_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제';

-- 부분 취소는 기획상 제공하지 않는다. 취소는 항상 전액이며, 결제 상태도 CANCELED 하나로만 옮긴다.
CREATE TABLE payment_cancels
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    payment_id     BIGINT       NOT NULL,
    cancel_reason  VARCHAR(255) NULL COMMENT '취소 사유',
    cancel_amount  BIGINT       NOT NULL COMMENT '취소 금액(원 단위 정수)',
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

CREATE TABLE idempotency_keys
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id         BIGINT       NOT NULL COMMENT '요청자. 키의 유효 범위를 사용자 단위로 좁힌다',
    idempotency_key VARCHAR(64)  NOT NULL COMMENT 'Idempotency-Key 헤더값(UUID)',
    request_path    VARCHAR(255) NOT NULL COMMENT '같은 키를 다른 엔드포인트에 재사용했는지 판별한다',
    -- 본문 원문이 아니라 지문을 저장한다. JSON 컬럼은 MySQL이 키 순서와 공백을 정규화해 되읽은
    -- 값이 직렬화 원문과 절대 일치하지 않으므로, 원문 비교는 항상 "다른 본문"으로 판정된다.
    -- 결제 본문에는 PG 인증 토큰이 들어 있어 원문을 남기지 않는 편이 안전하기도 하다.
    request_fingerprint CHAR(64) NOT NULL COMMENT '요청 본문 SHA-256. 같은 키에 다른 본문이 오면 거부한다',
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

    INDEX idx_payment_retries_status_next_retry_at (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '결제 재시도 큐';
