-- user-service 초기 스키마 (회원 인증 ERD 기준)
--
-- 인증 도메인(auth-service)의 auth_users.user_id가 users.id를 참조하지만
-- 서로 다른 DB에 있으므로 FK 제약은 걸지 않는다. 토큰의 sub에도 이 id가 담기며,
-- 다른 서비스의 소유권 비교 기준이 된다(인증인가_설계서 2.2).
--
-- status의 CHECK 제약은 Hibernate가 @Enumerated(STRING) 컬럼에 생성하는 것과 동일하다.
-- enum 값을 추가·삭제할 때는 이 제약도 함께 바꾸는 마이그레이션이 필요하다.

CREATE TABLE users
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK. 토큰 sub와 타 서비스의 소유자 식별자',
    provider    VARCHAR(20)  NOT NULL COMMENT '소셜 제공자. sync-profile 멱등성의 키',
    provider_id VARCHAR(255) NOT NULL COMMENT '소셜 제공자가 발급한 식별자. sync-profile 멱등성의 키',
    email       VARCHAR(255) NULL COMMENT '소셜 제공자가 동의 항목에 따라 주지 않을 수 있어 선택',
    name        VARCHAR(50)  NULL,
    status      VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / SUSPENDED / WITHDRAWN',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- sync-profile은 auth-service 재시도에도 회원이 중복 생성되지 않아야 한다.
    -- 애플리케이션 조회만으로는 동시 요청을 막지 못하므로 DB 제약으로 보장한다.
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT users_chk_1 CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT users_chk_2 CHECK (provider IN ('KAKAO', 'NAVER'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '회원';

CREATE TABLE delivery_addresses
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id        BIGINT       NOT NULL COMMENT '소유자. 소유권 검사 기준',
    address_name   VARCHAR(50)  NULL COMMENT '배송지 별칭',
    recipient_name VARCHAR(50)  NULL,
    phone          VARCHAR(20)  NULL,
    zip_code       VARCHAR(10)  NULL,
    address        VARCHAR(255) NULL,
    address_detail VARCHAR(255) NULL,
    is_default     TINYINT(1)   NOT NULL COMMENT '기본 배송지 여부',
    access_method  VARCHAR(255) NULL COMMENT '공동현관 출입 방법 등',
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '배송지';

-- 관리자. auth-service의 auth_admins.admin_id가 이 id를 참조하며 토큰 sub에도 담긴다.
-- role은 백오피스 내부 업무 구분용이며, 토큰의 role 클레임(USER/ADMIN 2종)과는 별개다
-- (인증인가_설계서 2.5 — 최상위 역할을 늘리지 않고 admin 내 permission으로 처리).
CREATE TABLE admins
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK. 토큰 sub 값',
    name       VARCHAR(50) NULL,
    role       VARCHAR(30) NULL COMMENT '백오피스 업무 구분 (표시·권한용)',
    department VARCHAR(50) NULL,
    status     VARCHAR(20) NOT NULL COMMENT 'ACTIVE / DISABLED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '관리자';
