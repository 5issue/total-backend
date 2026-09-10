-- PG 대사(reconciliation)를 위한 컬럼과 인덱스.
--
-- 승인 요청이 타임아웃되면 우리 기록과 PG의 진실이 어긋난다. 응답을 못 받았을 뿐 PG는 승인했을 수
-- 있어, 고객 돈은 빠져나갔는데 주문은 진행되지 않는 상태가 남는다. 애플리케이션 기록만으로는
-- 판별할 수 없으므로 배치가 PG에 직접 조회해 맞춘다.

ALTER TABLE payments
    -- 대사를 마친 시각. 한 건을 두 번 맞추지 않기 위한 표시이자, 배치 간 선점 표시를 겸한다.
    -- 대사 도중 실패하면 다시 NULL로 되돌려 다음 주기가 이어받게 한다.
    ADD COLUMN reconciled_at DATETIME(6) NULL COMMENT '대사 완료 시각. NULL이면 미대사',
    -- 대사 대상은 "아직 안 맞춘 비종결 결제"다. 선행 컬럼 순서를 선택도(status) 기준으로 둔다.
    -- payments의 생성 시각 컬럼은 requested_at이다(created_at이 아니다).
    ADD INDEX idx_payments_reconcile (status, reconciled_at, requested_at);

-- 매달린 멱등키 정리용. IN_PROGRESS로 남은 오래된 키를 오래된 순으로 훑는다.
ALTER TABLE idempotency_keys
    ADD INDEX idx_idempotency_keys_status_created_at (status, created_at);
