-- oms_orders에 region_id 추가
ALTER TABLE oms_orders
    ADD COLUMN region_id BIGINT;
ALTER TABLE oms_orders
    ADD CONSTRAINT fk_oms_orders_region FOREIGN KEY (region_id) REFERENCES tam_regions (id);

-- shipments에서 region_id FK 및 컬럼 제거
ALTER TABLE shipments
    DROP CONSTRAINT fk_shipments_region;
ALTER TABLE shipments
    DROP COLUMN region_id;