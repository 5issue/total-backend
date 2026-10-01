-- 인박스(product_consumed_event)에 재고 확정/복구 요청의 멱등성 마커 종류를 추가한다.
--
-- 재고 확정(confirm)은 예약 토큰당, 재고 복구(restore)는 주문당 한 번만 DB 수량을 바꿔야 하는데
-- 같은 요청이 브로커 재전달로 다시 오면 DB 선점 수량이 이중으로 반영되거나(확정) 다른 주문의 선점까지
-- 풀렸다(복구). 요청 키를 같은 트랜잭션 안에서 이 테이블에 먼저 적재해 두 번째 요청을 걸러낸다.

ALTER TABLE product_consumed_event DROP CONSTRAINT chk_product_consumed_event_type;
ALTER TABLE product_consumed_event ADD CONSTRAINT chk_product_consumed_event_type
    CHECK (event_type IN ('INBOUND_COMPLETED', 'OUTBOUND_COMPLETED',
                          'INVENTORY_CONFIRM_REQUESTED', 'INVENTORY_RESTORE_REQUESTED'));
