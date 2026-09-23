-- 유휴 세션 자동 차단(인증인가_설계서 1.6)을 위한 마지막 활동 시각.
--
-- NULL을 허용한다. 이미 발급된 세션에는 활동 기록이 없으므로, 판정은 created_at으로 폴백한다.
-- 값 갱신 경로는 두 가지다.
--   1) 토큰 갱신 시 auth-service가 직접 기록(동기) — 메시지 유실과 무관하게 항상 동작한다
--   2) 각 서비스의 공통 인증 처리기가 보내는 활동 이벤트(비동기) — 갱신과 갱신 사이를 메운다

ALTER TABLE user_refresh_tokens
    ADD COLUMN last_used_at DATETIME(6) NULL COMMENT '마지막 활동 시각. NULL이면 created_at으로 판정';

ALTER TABLE admin_refresh_tokens
    ADD COLUMN last_used_at DATETIME(6) NULL COMMENT '마지막 활동 시각. NULL이면 created_at으로 판정';
