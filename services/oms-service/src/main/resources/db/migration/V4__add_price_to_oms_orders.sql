-- oms_orders에 원 결제총액 저장
ALTER TABLE oms_orders
    ADD COLUMN paid_amount BIGINT NOT NULL DEFAULT 0;

-- oms_order_items에 품목별 구매단가 저장
ALTER TABLE oms_order_items
    ADD COLUMN unit_price BIGINT NOT NULL DEFAULT 0;

-- oms_returns에 계산된 최종 환불금액 및 차감 배송비 컬럼 추가
ALTER TABLE oms_returns
    ADD COLUMN total_refund_amount BIGINT;
ALTER TABLE oms_returns
    ADD COLUMN deducted_shipping_fee BIGINT DEFAULT 0;