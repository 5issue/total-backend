-- 1. TAM 배송 권역 테이블
CREATE TABLE tam_regions
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_code   VARCHAR(30)  NOT NULL,
    region_name   VARCHAR(100) NOT NULL,
    delivery_type VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tam_regions_region_code UNIQUE (region_code)
);

-- 2. 권역별 배송 회차 테이블
CREATE TABLE delivery_slots
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_id           BIGINT      NOT NULL,
    slot_code           VARCHAR(30) NOT NULL,
    slot_name           VARCHAR(80) NOT NULL,
    cutoff_time         TIME        NOT NULL,
    dispatch_time       TIME        NOT NULL,
    delivery_start_time TIME        NOT NULL,
    delivery_end_time   TIME        NOT NULL,
    lead_days           INT         NOT NULL DEFAULT 0,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_slots_region FOREIGN KEY (region_id) REFERENCES tam_regions (id),
    CONSTRAINT uk_delivery_slots_region_slot UNIQUE (region_id, slot_code)
);

-- 3. 출고 센터 참조 테이블
CREATE TABLE fulfillment_centers
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warehouse_id BIGINT       NOT NULL,
    center_code  VARCHAR(30)  NOT NULL,
    center_name  VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fulfillment_centers_warehouse_id UNIQUE (warehouse_id),
    CONSTRAINT uk_fulfillment_centers_center_code UNIQUE (center_code)
);

-- 4. 권역-센터 매핑 테이블
CREATE TABLE region_center_mappings
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_id  BIGINT      NOT NULL,
    center_id  BIGINT      NOT NULL,
    priority   INT         NOT NULL DEFAULT 1,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rc_mappings_region FOREIGN KEY (region_id) REFERENCES tam_regions (id),
    CONSTRAINT fk_rc_mappings_center FOREIGN KEY (center_id) REFERENCES fulfillment_centers (id)
);

-- 5. 센터 회차별 CAPA 계획 테이블
CREATE TABLE capacity_plans
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    center_id         BIGINT      NOT NULL,
    slot_id           BIGINT      NOT NULL,
    service_date      DATE        NOT NULL,
    base_capacity     INT         NOT NULL,
    adjusted_capacity INT         NOT NULL,
    reserved_capacity INT         NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    wfm_event_id      VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_capacity_plans_center FOREIGN KEY (center_id) REFERENCES fulfillment_centers (id),
    CONSTRAINT fk_capacity_plans_slot FOREIGN KEY (slot_id) REFERENCES delivery_slots (id),
    CONSTRAINT uk_capacity_plans_center_slot_date UNIQUE (center_id, slot_id, service_date)
);

-- 6. OMS 주문 테이블
CREATE TABLE oms_orders
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    order_no           VARCHAR(30)  NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'ORDER_RECEIVED',
    source_event_id    VARCHAR(64)  NOT NULL,
    recipient_name     VARCHAR(80)  NOT NULL,
    recipient_phone    VARCHAR(30)  NOT NULL,
    postal_code        VARCHAR(10)  NOT NULL,
    road_address       VARCHAR(255) NOT NULL,
    detail_address     VARCHAR(255),
    cancel_reason_code VARCHAR(40),
    cancelled_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oms_orders_order_id UNIQUE (order_id),
    CONSTRAINT uk_oms_orders_source_event_id UNIQUE (source_event_id)
);

-- 7. OMS 주문 품목 테이블
CREATE TABLE oms_order_items
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oms_order_id  BIGINT      NOT NULL,
    order_item_id BIGINT      NOT NULL,
    product_id    BIGINT      NOT NULL,
    sku_id        BIGINT      NOT NULL,
    storage_type  VARCHAR(20) NOT NULL,
    quantity      INT         NOT NULL,
    volume_cm3    DECIMAL(12, 2),
    weight_gram   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oms_order_items_order FOREIGN KEY (oms_order_id) REFERENCES oms_orders (id) ON DELETE CASCADE
);

-- 8. 출고 단위 (Shipment) 테이블
CREATE TABLE shipments
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oms_order_id           BIGINT      NOT NULL,
    shipment_no            VARCHAR(40) NOT NULL,
    storage_type           VARCHAR(20) NOT NULL,
    center_id              BIGINT,
    region_id              BIGINT      NOT NULL,
    slot_id                BIGINT,
    status                 VARCHAR(30) NOT NULL DEFAULT 'SHIPMENT_CREATED',
    delivery_status        VARCHAR(30),
    original_dispatch_date DATE,
    expected_dispatch_date DATE,
    rolled_over_at         TIMESTAMPTZ,
    rollover_reason_code   VARCHAR(40),
    box_type               VARCHAR(30),
    coolant_type           VARCHAR(20) NOT NULL DEFAULT 'NONE',
    coolant_quantity       INT         NOT NULL DEFAULT 0,
    cancel_reason_code     VARCHAR(40),
    inventory_event_id     VARCHAR(64),
    inventory_failure_code VARCHAR(40),
    tms_delivery_id        VARCHAR(64),
    last_delivery_event_id VARCHAR(64),
    delivered_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_shipments_shipment_no UNIQUE (shipment_no),
    CONSTRAINT fk_shipments_order FOREIGN KEY (oms_order_id) REFERENCES oms_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_shipments_center FOREIGN KEY (center_id) REFERENCES fulfillment_centers (id),
    CONSTRAINT fk_shipments_region FOREIGN KEY (region_id) REFERENCES tam_regions (id),
    CONSTRAINT fk_shipments_slot FOREIGN KEY (slot_id) REFERENCES delivery_slots (id)
);

-- 9. 출고 단위 품목 매핑 테이블
CREATE TABLE shipment_items
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shipment_id       BIGINT      NOT NULL,
    oms_order_item_id BIGINT      NOT NULL,
    quantity          INT         NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipment_items_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE,
    CONSTRAINT fk_shipment_items_order_item FOREIGN KEY (oms_order_item_id) REFERENCES oms_order_items (id)
);

-- 10. Spring Modulith 이벤트 아웃박스 테이블 (PostgreSQL 규격)
CREATE TABLE IF NOT EXISTS event_publication
(
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    listener_id      VARCHAR(512) NOT NULL,
    event_type       VARCHAR(512) NOT NULL,
    serialized_event TEXT         NOT NULL,
    publication_date TIMESTAMP(6) NOT NULL,
    completion_date  TIMESTAMP(6) NULL DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX IF NOT EXISTS idx_event_publication_publication_date ON event_publication (publication_date);