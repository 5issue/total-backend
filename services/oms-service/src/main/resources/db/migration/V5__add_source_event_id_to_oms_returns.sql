ALTER TABLE oms_returns ADD COLUMN source_event_id VARCHAR(64) NOT NULL;
ALTER TABLE oms_returns ADD CONSTRAINT uq_oms_returns_source_event_id UNIQUE (source_event_id);
ALTER TABLE oms_returns ADD CONSTRAINT uq_oms_returns_oms_order_id UNIQUE (oms_order_id);
