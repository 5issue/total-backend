-- 대사 선점과 대사 완료를 분리하고, 주문 인계 완료 여부를 남긴다.
--
-- (1) reconciled_at 하나가 "선점 표시"와 "완료 표시"를 겸하고 있었다. 선점 직후 프로세스가 죽으면
--     해제 코드가 실행되지 않아 그 결제는 영원히 대사 대상에서 빠진다. 승인 여부가 불명확한 결제가
--     조용히 묻히는 경로다. 선점은 만료되는 임대로, 완료는 별도 시각으로 나눈다.
--
-- (2) 승인 기록(SUCCESS 커밋)과 주문 인계는 서로 다른 트랜잭션이다. 그 사이에 프로세스가 죽으면
--     "결제는 성공했는데 주문은 모르는" 상태가 남는데, 대사는 REQUESTED·FAILED만 보므로 회수되지
--     않았다. 인계 완료 시각을 남겨 미인계 SUCCESS도 대사가 집도록 한다.

ALTER TABLE payments
    ADD COLUMN reconcile_claimed_until DATETIME(6) NULL COMMENT '대사 선점 만료 시각. 지나면 다른 워커가 다시 집는다',
    ADD COLUMN order_notified_at       DATETIME(6) NULL COMMENT '주문 서비스 인계 완료 시각. NULL이면 미인계';

-- 이미 처리를 마친 기존 성공 결제를 미인계로 오인해 대사가 집지 않도록 채워 둔다.
UPDATE payments
   SET order_notified_at = approved_at
 WHERE status = 'SUCCESS'
   AND approved_at IS NOT NULL;

-- 대사 대상 조건이 (미완료 + 선점 만료 + 유예 경과)로 바뀐다. status는 OR 조건이 되어 선행 컬럼에서 뺀다.
ALTER TABLE payments
    DROP INDEX idx_payments_reconcile,
    ADD INDEX idx_payments_reconcile (reconciled_at, reconcile_claimed_until, requested_at);
