ALTER TABLE orders
    ADD COLUMN delivered_at DATETIME NULL AFTER expected_delivery_at;
