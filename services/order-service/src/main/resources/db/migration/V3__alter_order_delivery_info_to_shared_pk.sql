-- 1. 기존 외래키 및 유니크 제약조건 제거
ALTER TABLE order_delivery_info
    DROP FOREIGN KEY fk_order_delivery_info_order_id;

ALTER TABLE order_delivery_info
    DROP INDEX uk_order_delivery_info_order_id;

-- 2. 기존 auto_increment PK 제거 및 order_id를 PK로 변경
ALTER TABLE order_delivery_info
    MODIFY COLUMN id BIGINT NOT NULL;

ALTER TABLE order_delivery_info
    DROP PRIMARY KEY;

ALTER TABLE order_delivery_info
    DROP COLUMN id;

ALTER TABLE order_delivery_info
    ADD PRIMARY KEY (order_id);

-- 3. 외래키 제약조건 재설정
ALTER TABLE order_delivery_info
    ADD CONSTRAINT fk_order_delivery_info_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;