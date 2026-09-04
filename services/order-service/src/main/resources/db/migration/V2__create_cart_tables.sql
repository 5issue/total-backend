-- V2__create_cart_tables.sql

CREATE TABLE carts
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    address_id    BIGINT      NULL,
    region_id     BIGINT      NULL,
    delivery_type VARCHAR(20) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_member_id UNIQUE (member_id),
    INDEX idx_carts_address_id (address_id),
    INDEX idx_carts_region_id (region_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE cart_items
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id      BIGINT      NOT NULL,
    product_id   BIGINT      NOT NULL,
    storage_type VARCHAR(20) NOT NULL,
    quantity     INT         NOT NULL DEFAULT 1,
    is_checked   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_cart_id
        FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    INDEX idx_cart_items_product_id (product_id),
    INDEX idx_cart_items_storage_type (storage_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;