-- OMS 반품 환불의 중복 방지와 경로 식별(payment 추가 통신 명세).
--
-- dedup_key: 같은 환불 요청을 두 번 처리하지 않기 위한 유니크 키. 이벤트 식별자를 담는다.
--   조회 후 삽입으로는 동시 재배달을 막을 수 없다 — 두 컨슈머가 모두 "없음"을 보고 각자 환불한다.
--   DB 유니크 제약이 유일한 경합 차단점이며, 위반이 곧 중복 배달 신호다.
--   NULL을 허용해 기존 취소(전액 취소·사용자 취소)에는 영향이 없다. InnoDB는 NULL 중복을 허용한다.
--
-- oms_return_id: OMS가 반품 건을 찾는 조회 키. 완료 통보에 되돌려주고, 재시도·회수 경로가
--   "이 취소는 부분 환불이다"를 알아보는 표시로도 쓴다. 이 값이 없으면 회수 배치가 부분 환불을
--   전액 취소로 완료 처리해 결제 상태와 OMS 상태가 모두 어긋난다.

ALTER TABLE payment_cancels
    ADD COLUMN dedup_key     VARCHAR(100) NULL COMMENT '환불 요청 중복 방지 키(이벤트 식별자)',
    ADD COLUMN oms_return_id BIGINT       NULL COMMENT 'OMS 반품 건 식별자. 부분 환불 경로 표시',
    ADD CONSTRAINT uk_payment_cancels_dedup_key UNIQUE (dedup_key);
