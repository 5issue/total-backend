CREATE TABLE orders
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no                    VARCHAR(30)  NOT NULL,
    member_id                   BIGINT       NOT NULL,
    payment_id                  BIGINT       NULL,
    status                      VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PAYMENT',
    item_amount                 BIGINT       NOT NULL DEFAULT 0,
    shipping_fee                BIGINT       NOT NULL DEFAULT 0,
    payment_amount              BIGINT       NOT NULL DEFAULT 0,
    paid_at                     DATETIME     NULL,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    oms_order_id                BIGINT       NULL,
    fulfillment_status          VARCHAR(30)  NULL,
    delivery_status             VARCHAR(30)  NULL,
    expected_delivery_at        DATETIME     NULL,
    inventory_reservation_token VARCHAR(64)  NULL,
    inventory_reserved_until    DATETIME     NULL,
    CONSTRAINT uk_orders_order_no UNIQUE (order_no),
    INDEX idx_orders_member_id (member_id),
    INDEX idx_orders_payment_id (payment_id),
    INDEX idx_orders_oms_order_id (oms_order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_items
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    deal_product_id BIGINT       NOT NULL,
    sku_id          BIGINT       NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    option_name     VARCHAR(150) NULL,
    storage_type    VARCHAR(20)  NOT NULL,
    quantity        INT          NOT NULL,
    unit_price      BIGINT       NOT NULL,
    line_amount     BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_order_items_product_id (product_id),
    INDEX idx_order_items_sku_id (sku_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_delivery_info
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT       NOT NULL,
    source_address_id BIGINT       NULL,
    recipient_name    VARCHAR(80)  NOT NULL,
    phone             VARCHAR(30)  NOT NULL,
    zip_code          VARCHAR(10)  NOT NULL,
    address           VARCHAR(255) NOT NULL,
    address_detail    VARCHAR(255) NULL,
    address_name      VARCHAR(50)  NULL,
    access_method     VARCHAR(30)  NULL,
    access_detail     VARCHAR(255) NULL,
    packing_type      VARCHAR(30)  NULL,
    delivery_message  VARCHAR(255) NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_delivery_info_order_id UNIQUE (order_id),
    CONSTRAINT fk_order_delivery_info_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_claims
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id               BIGINT       NOT NULL,
    claim_type             VARCHAR(20)  NOT NULL,
    requester_type         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    reason_code            VARCHAR(40)  NOT NULL,
    reason_detail          VARCHAR(500) NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'REQUESTED',
    refund_status          VARCHAR(20)  NOT NULL DEFAULT 'NOT_REQUESTED',
    expected_refund_amount BIGINT       NULL,
    refund_amount          BIGINT       NULL,
    payment_cancel_id      BIGINT       NULL,
    requested_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at           DATETIME     NULL,
    failure_reason         VARCHAR(500) NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_claims_order_id UNIQUE (order_id),
    CONSTRAINT fk_order_claims_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_order_claims_payment_cancel_id (payment_cancel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refund_attachments
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_claim_id     BIGINT       NOT NULL,
    s3_bucket          VARCHAR(100) NOT NULL,
    s3_object_key      VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NULL,
    content_type       VARCHAR(100) NULL,
    file_size          BIGINT       NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refund_attachments_s3_object_key UNIQUE (s3_object_key),
    CONSTRAINT fk_refund_attachments_order_claim_id
        FOREIGN KEY (order_claim_id) REFERENCES order_claims (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_outbox
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INT          NOT NULL DEFAULT 0,
    published_at   DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_outbox_publish (status, created_at),
    INDEX idx_order_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
