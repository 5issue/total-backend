CREATE TABLE oms_return_processes
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_id          BIGINT       NOT NULL,
    oms_order_id       BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    payment_id         BIGINT       NOT NULL,
    source_event_id    VARCHAR(36)  NOT NULL,
    refund_amount      BIGINT       NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oms_return_processes_return_id UNIQUE (return_id),
    CONSTRAINT uk_oms_return_processes_source_event_id UNIQUE (source_event_id),
    CONSTRAINT fk_oms_return_processes_order FOREIGN KEY (oms_order_id) REFERENCES oms_orders (id)
);

CREATE INDEX idx_oms_return_processes_order_id ON oms_return_processes (oms_order_id);
