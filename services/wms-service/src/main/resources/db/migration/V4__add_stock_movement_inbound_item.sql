-- put-away/confirm이 "이 검수 건에 대한 대기 중인 적치 작업 지시"를 로케이션 매칭 없이
-- 직접 찾을 수 있도록 stock_movement -> inbound_item 참조를 추가한다. 작업자가 추천과
-- 다른 로케이션에 실제로 적치할 수 있어(to_location_id가 바뀔 수 있음) location 기준
-- 매칭은 신뢰할 수 없다. REPLENISHMENT/RELOCATION은 입고 건과 무관해 NULL을 허용한다.
ALTER TABLE stock_movement ADD COLUMN inbound_item_id BIGINT NULL REFERENCES inbound_item(id);
