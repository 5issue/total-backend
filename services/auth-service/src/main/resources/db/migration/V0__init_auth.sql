-- auth-service 초기 스키마
--
-- 회원 도메인(user-service)의 Users·Admins는 별도 DB에 있으므로 FK 제약을 걸지 않는다.
-- auth_users.user_id / auth_admins.admin_id는 논리적 참조값이며, 토큰의 sub에 담긴다.
--
-- status·provider의 CHECK 제약은 Hibernate가 @Enumerated(STRING) 컬럼에 생성하는 것과 동일하다.
-- enum 값을 추가·삭제할 때는 이 제약도 함께 바꾸는 마이그레이션이 필요하다.

CREATE TABLE auth_users
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    provider    VARCHAR(20)  NOT NULL COMMENT '소셜 제공자',
    provider_id VARCHAR(255) NOT NULL COMMENT '소셜 제공자가 발급한 사용자 식별자',
    user_id     BIGINT       NOT NULL COMMENT '회원 도메인 Users.id 참조값 (FK 아님)',
    status      VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / SUSPENDED / WITHDRAWN',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_users_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT auth_users_chk_1 CHECK (provider IN ('KAKAO', 'NAVER')),
    CONSTRAINT auth_users_chk_2 CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '고객 소셜 인증 정보';

CREATE TABLE auth_admins
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    login_id     VARCHAR(50)  NOT NULL COMMENT '로그인 아이디',
    password     VARCHAR(255) NOT NULL COMMENT 'BCrypt 단방향 해시. 평문 저장 금지',
    admin_id     BIGINT       NOT NULL COMMENT '회원 도메인 Admins.id 참조값 (FK 아님)',
    status       VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / DISABLED. 일시 잠금은 locked_until이 관리',
    retry_count  INT          NOT NULL COMMENT '연속 인증 실패 횟수. 로그인 성공 시 0으로 초기화',
    locked_until DATETIME(6)  NULL COMMENT '일시 잠금 해제 시각. 경과하면 자동 해제',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_admins_login_id UNIQUE (login_id),
    CONSTRAINT auth_admins_chk_1 CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '관리자 인증 정보';

-- refresh token은 원본이 아닌 해시를 저장한다(인증인가_설계서 1.5).
CREATE TABLE user_refresh_tokens
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    token        VARCHAR(255) NOT NULL COMMENT 'refresh token의 SHA-256 해시',
    expires_at   DATETIME(6)  NOT NULL,
    is_revoked   TINYINT(1)   NOT NULL COMMENT '무효화 여부',
    created_at   DATETIME(6)  NOT NULL,
    auth_user_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_user_refresh_tokens_auth_user FOREIGN KEY (auth_user_id) REFERENCES auth_users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '고객 refresh token 세션';

CREATE TABLE admin_refresh_tokens
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    token         VARCHAR(255) NOT NULL COMMENT 'refresh token의 SHA-256 해시',
    expires_at    DATETIME(6)  NOT NULL,
    is_revoked    TINYINT(1)   NOT NULL COMMENT '무효화 여부',
    created_at    DATETIME(6)  NOT NULL,
    admin_user_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_admin_refresh_tokens_auth_admin FOREIGN KEY (admin_user_id) REFERENCES auth_admins (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '관리자 refresh token 세션';
