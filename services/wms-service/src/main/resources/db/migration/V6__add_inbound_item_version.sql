-- InboundItem은 검수(inspect)/적치(put-away confirm)에서 같은 행을 읽어 상태를 전이시키는
-- 흐름이라, 동시에 두 요청(중복 클릭, 재시도 등)이 들어오면 둘 다 PENDING/INSPECTED 상태
-- 체크를 통과한 뒤 서로 덮어쓸 수 있다. JPA @Version 기반 낙관적 락으로 두 번째 커밋을
-- 실패시켜 막는다. 기존 행은 0부터 시작해도 안전하다(그 시점까지는 동시 수정 이력이 없다).
ALTER TABLE inbound_item ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
