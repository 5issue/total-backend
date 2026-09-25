-- V7__alter_orders_inventory_reservation_token_to_uuid.sql

-- 1. BINARY(16) 포맷의 임시 컬럼 생성
ALTER TABLE orders ADD COLUMN new_token BINARY(16);

-- 2. 기존 문자열 정제 및 UUID BINARY(16) 변환
UPDATE orders
SET new_token = CASE
                    WHEN inventory_reservation_token IS NULL OR TRIM(inventory_reservation_token) = '' THEN NULL
                    WHEN LEFT(inventory_reservation_token, 4) = 'rsv_'
                         AND SUBSTRING(inventory_reservation_token, 5) REGEXP '^[0-9a-fA-F]{32}$'
                        THEN UUID_TO_BIN(CONCAT(
                            SUBSTRING(inventory_reservation_token, 5, 8), '-',
                            SUBSTRING(inventory_reservation_token, 13, 4), '-',
                            SUBSTRING(inventory_reservation_token, 17, 4), '-',
                            SUBSTRING(inventory_reservation_token, 21, 4), '-',
                            SUBSTRING(inventory_reservation_token, 25, 12)
                        ))
                    WHEN IS_UUID(inventory_reservation_token) = 1
                        THEN UUID_TO_BIN(inventory_reservation_token)
                    ELSE NULL
    END;

-- 3. 기존 컬럼 삭제 후 교체
ALTER TABLE orders DROP COLUMN inventory_reservation_token;
ALTER TABLE orders RENAME COLUMN new_token TO inventory_reservation_token;
