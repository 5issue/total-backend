회원 도메인
```SQL
CREATE TABLE `delivery_addresses` ( -- 배송지
	`id`	BIGINT	NOT NULL,
	`address_name`	VARCHAR(50)	NULL,
	`recipient_name`	VARCHAR(50)	NULL,
	`phone`	VARCHAR(20)	NULL,
	`zip_code`	VARCHAR(10)	NULL,
	`address`	VARCHAR(255)	NULL,
	`address_detail`	VARCHAR(255)	NULL,
	`is_default`	BOOLEAN	NULL,
	`access_method`	VARCHAR(255)	NULL,
	`created_at`	DATETIME	NULL,
	`updated_at`	DATETIME	NULL,
	`user_id`	BIGINT	NOT NULL
);

CREATE TABLE `Users` ( -- 사용자
	`id`	BIGINT	NOT NULL,
	`email`	VARCHAR(255)	NULL,
	`name`	VARCHAR(50)	NULL,
	`status`	ENUM	NULL,
	`created_at`	DATETIME	NULL,
	`updated_at`	DATETIME	NULL
);


CREATE TABLE `Admins` ( -- 관리자
	`id`	BIGINT	NOT NULL,
	`name`	VARCHAR(50)	NULL,
	`role`	VARCHAR(30)	NULL,
	`department`	VARCHAR(50)	NULL,
	`status`	VARCHAR(20)	NULL,
	`created_at`	DATETIME	NULL,
	`updated_at`	DATETIME	NULL
);

```


인증 도메인
``` SQL
CREATE TABLE `user_refresh_tokens` ( -- 사용자 토큰 관리
	`id`	BIGINT	NOT NULL,
	`token`	VARCHAR(255)	NULL,
	`expires_at`	DATETIME	NULL,
	`is_revoked`	BOOLEAN	NULL,
	`created_at`	DATETIME	NULL,
	`auth_user_id`	BIGINT	NOT NULL
);


CREATE TABLE `auth_users` ( -- 사용자 인증 정보
	`id`	BIGINT	NOT NULL,
	`provider`	VARCHAR(20)	NULL,
	`provider_id`	VARCHAR(255)	NULL,
	`created_at`	DATETIME	NULL,
	`status`	VARCHAR(20)	NULL
);

CREATE TABLE `auth_admins` ( -- 관리자 인증 정보
	`id`	BIGINT	NOT NULL,
	`logint_id`	VARCHAR(50)	NULL,
	`password`	VARCHAR(255)	NULL,
	`created_at`	DATETIME	NULL,
	`updated_at`	DATETIME	NULL,
	`status`	VARCHAR(20)	NULL,
	`retry_count`	INT	NULL
);

CREATE TABLE `admin_refresh_tokens` ( -- 관리자 토큰 관리
	`id`	BIGINT	NOT NULL,
	`token`	VARCHAR(255)	NULL,
	`expires_at`	DATETIME	NULL,
	`is_revoked`	BOOLEAN	NULL,
	`created_at`	DATETIME	NULL,
	`auth_admin_id`	BIGINT	NOT NULL
);
```