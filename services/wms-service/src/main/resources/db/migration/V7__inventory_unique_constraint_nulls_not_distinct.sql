-- 기존 uk_inventory_lot는 표준 SQL 유니크 제약이라 lpn_code IS NULL인 행끼리는
-- "같은 값"으로 취급하지 않는다(NULL <> NULL). LPN 없이 관리하는 게 기본값인 이 서비스에서는
-- 사실상 (warehouse, location, product, lot_no, expired_date) 조합의 유일성이 전혀
-- 보장되지 않았다는 뜻이다 — 동시에 두 트랜잭션이 같은 조합을 "없음"으로 보고 각자 새 행을
-- 만들면(find-or-create 경합) 유니크 제약이 막아주지 못하고 재고가 두 행으로 쪼개진다.
-- (InventoryJpaRepository의 PESSIMISTIC_WRITE 조회는 이미 존재하는 행만 잠글 수 있어
-- 이 경합 자체는 막지 못한다 — 이 제약이 마지막 방어선이다.)
ALTER TABLE inventory DROP CONSTRAINT uk_inventory_lot;
ALTER TABLE inventory ADD CONSTRAINT uk_inventory_lot
    UNIQUE NULLS NOT DISTINCT (warehouse_id, location_id, product_id, lot_no, expired_date, lpn_code);
