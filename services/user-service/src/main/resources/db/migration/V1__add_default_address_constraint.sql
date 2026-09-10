-- 회원당 기본 배송지는 최대 하나여야 한다.
--
-- 애플리케이션 검사만으로는 동시 요청을 막지 못한다. 두 요청이 각자 기존 기본 배송지를 해제한 뒤
-- 서로 다른 배송지를 기본으로 저장하면 기본 배송지가 2건 남고, 조회는 그중 임의의 한 건을 돌려준다.
--
-- is_default 컬럼에 그냥 UNIQUE를 걸 수는 없다. 기본이 아닌 배송지가 여러 개이기 때문이다.
-- "기본인 것만 회원당 하나"는 부분 유니크 인덱스가 필요한데 MySQL이 지원하지 않으므로,
-- 기본일 때만 값이 생기는 생성 컬럼에 유니크를 건다. NULL은 중복이 허용된다.
ALTER TABLE delivery_addresses
    ADD COLUMN default_user_id BIGINT
        GENERATED ALWAYS AS (IF(is_default = 1, user_id, NULL)) VIRTUAL
        COMMENT '기본 배송지일 때만 user_id. 회원당 기본 하나를 DB가 보장한다',
    ADD CONSTRAINT uk_delivery_addresses_default_user UNIQUE (default_user_id);
