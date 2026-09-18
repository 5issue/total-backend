-- 역물류(반품) 전용 테이블 신설
CREATE TABLE oms_returns
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oms_order_id BIGINT      NOT NULL,
    order_id     BIGINT      NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    admin_note   VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oms_returns_oms_order FOREIGN KEY (oms_order_id) REFERENCES oms_orders (id)
);

CREATE INDEX idx_oms_returns_oms_order_id ON oms_returns (oms_order_id);
CREATE INDEX idx_oms_returns_order_id ON oms_returns (order_id);

CREATE TABLE oms_return_items
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oms_return_id     BIGINT      NOT NULL,
    oms_order_item_id BIGINT      NOT NULL,
    decision          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reject_reason     VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oms_return_items_return FOREIGN KEY (oms_return_id) REFERENCES oms_returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_oms_return_items_order_item FOREIGN KEY (oms_order_item_id) REFERENCES oms_order_items (id)
);