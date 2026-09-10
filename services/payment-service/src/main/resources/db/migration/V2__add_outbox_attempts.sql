-- 아웃박스 발행 시도 횟수와 백오프.
--
-- 지금은 발행에 실패한 이벤트를 그냥 PENDING으로 두어 다음 주기에 다시 시도한다. 브로커 장애처럼
-- 일시적인 실패에는 맞지만, 페이로드가 깨졌거나 라우팅 키가 잘못된 이벤트는 <b>영원히</b> 5초마다
-- 재시도되며 배치 묶음을 차지한다. 뒤에 쌓인 정상 이벤트가 그만큼 밀린다.
--
-- 시도 횟수를 세어 상한에 도달하면 FAILED로 멈춘다. 그때부터는 사람이 봐야 하는 상태이며,
-- payment_retries의 영구 실패와 같은 취급이다(관리자 대시보드 노출 대상).

ALTER TABLE payment_outbox
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT '누적 발행 시도 횟수. 상한 초과 시 중단한다',
    -- NULL이면 지금 바로 대상이다. 적재 직후가 그렇다.
    ADD COLUMN next_attempt_at DATETIME(6) NULL COMMENT '다음 발행 시각. 지수 백오프로 늘린다',
    ADD COLUMN last_error VARCHAR(255) NULL COMMENT '마지막 실패 사유',
    ADD CONSTRAINT payment_outbox_chk_2 CHECK (attempt_count >= 0);

-- 발행 워커의 조회 조건이 (status, next_attempt_at)으로 바뀐다. 기존 인덱스는 더 이상 맞지 않는다.
ALTER TABLE payment_outbox
    DROP INDEX idx_payment_outbox_status_created_at,
    ADD INDEX idx_payment_outbox_status_next_attempt_at (status, next_attempt_at, created_at);
