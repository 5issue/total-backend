-- OutboundItemStatus에 PENDING_REPLENISHMENT(21자)가 추가되어 기존 VARCHAR(20)을 넘는다.
ALTER TABLE outbound_item
    ALTER COLUMN status TYPE VARCHAR(30);
