-- V7__alter_orders_inventory_reservation_token_to_uuid.sql

-- 1. BINARY(16) 포맷의 임시 컬럼 생성
ALTER TABLE orders ADD COLUMN new_token BINARY(16);

-- 2. 기존 문자열 정제 및 UUID BINARY(16) 변환
UPDATE orders
SET new_token = CASE
                    WHEN inventory_reservation_token IS NULL OR TRIM(inventory_reservation_token) = '' THEN NULL
                    WHEN inventory_reservation_token LIKE 'rsv_%' AND CHAR_LENGTH(SUBSTRING(inventory_reservation_token, 5)) = 36
                        THEN UUID_TO_BIN(SUBSTRING(inventory_reservation_token, 5))
                    WHEN CHAR_LENGTH(inventory_reservation_token) = 36
                        THEN UUID_TO_BIN(inventory_reservation_token)
                    ELSE NULL
    END;

-- 3. 기존 컬럼 삭제 후 교체
ALTER TABLE orders DROP COLUMN inventory_reservation_token;
ALTER TABLE orders RENAME COLUMN new_token TO inventory_reservation_token;