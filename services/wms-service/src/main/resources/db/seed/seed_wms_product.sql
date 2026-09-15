-- ================================================================
-- wms-service 로컬 개발용 시드 스크립트 — WmsProduct
--
-- 목적: product-service의 시드 데이터(seed_product_service.sql,
--       seed_product_service_kurly_picks.sql)가 만든 상품 중 UNIT 타입(실제 구매
--       단위)만 골라 WmsProduct를 채운다. GROUP은 목록에 보이는 껍데기일 뿐 실물
--       재고가 없어 WMS와 무관하므로 대상에서 제외한다.
--
--       WmsProduct.id는 상품 서비스 Product UNIT ID와 반드시 같은 값을 써야 해서
--       (docs/erd-spec.md 1번 — WmsProduct는 자체 채번하지 않는다) product-service
--       DB에서 실제 생성된 id를 그대로 가져와야 한다.
--
-- 전제 조건:
--   1. product-service와 wms-service는 완전히 분리된 Postgres 컨테이너다(포트
--      5436 vs 5437, 별도 docker-compose 프로젝트). 같은 SQL로 두 DB를 조인할 수
--      없어서, wms DB 쪽에서 dblink로 product 컨테이너에 직접 접속해 가져온다.
--   2. product-service의 두 seed 파일이 먼저 실행되어 있어야 한다(UNIT 상품이
--      이미 존재해야 함).
--   3. dblink 접속 주소는 host.docker.internal:5436 이다. Docker Desktop(macOS/
--      Windows)에서는 기본으로 되지만, Linux 환경(예: CI)에서는 wms-service의
--      docker-compose.yml에 있는 postgres-wms 서비스에
--        extra_hosts: ["host.docker.internal:host-gateway"]
--      를 추가해야 한다 (루트 docker-compose.yml의 swagger-ui가 이미 이 방식을
--      쓰고 있다). 계정/비밀번호가 로컬 기본값(postgres/password)과 다르면 아래
--      dblink 연결 문자열도 맞게 고쳐야 한다.
--
-- 매핑 (product → WmsProduct):
--   id            <- product.id (UNIT)                — 그대로 사용, 자체 채번 안 함
--   sku_code      <- product.sku_code
--   name          <- product.name
--   storage_type  <- product_spec.storage_type         — REFRIGERATED/FROZEN/ROOM_TEMPERATURE로
--                     enum 값이 이미 동일해 변환 없이 그대로 쓴다. product_spec이 없는
--                     UNIT은 없지만(2025-09-15 기준 213건 전수 확인), 방어적으로
--                     ROOM_TEMPERATURE를 기본값으로 둔다.
--   barcode       <- (없음) product-service에 바코드 데이터가 없어 NULL로 둔다.
--   box_unit_qty / pallet_box_qty / safety_stock
--                 <- (없음) 상품 서비스 데이터에 없는 WMS 전용 물류 속성이라 임의
--                    기본값을 둔다 — seed_product_service*.sql도 원본에 없는 값(가격/
--                    좋아요 수 등)은 동일하게 임의로 채웠다.
--
-- 실행 방법 (product-service 시드 실행 후):
--   docker exec -i kurly-postgres-wms psql -U postgres -d wms \
--     < src/main/resources/db/seed/seed_wms_product.sql
--
-- 주의: 멱등하지 않다. sku_code UNIQUE 제약과 id PK 제약이 있어 재실행하면
--       충돌한다. ON CONFLICT (id) DO NOTHING으로 에러 없이 건너뛰게는 해뒀지만,
--       product-service 쪽 데이터를 바꾼 뒤 값을 최신화하고 싶으면
--       TRUNCATE wms_product; 를 먼저 실행할 것(FK로 물린 inbound_item/inventory/
--       outbound_item/stock_movement가 있으면 그것들도 먼저 정리해야 한다).
-- ================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

INSERT INTO wms_product (id, sku_code, name, storage_type, box_unit_qty, pallet_box_qty, safety_stock)
SELECT
    src.id,
    src.sku_code,
    src.name,
    COALESCE(src.storage_type, 'ROOM_TEMPERATURE'),
    10, -- box_unit_qty: 박스당 낱개 수 (원본에 없어 임의 기본값)
    40, -- pallet_box_qty: 파레트당 박스 수 (원본에 없어 임의 기본값)
    20  -- safety_stock: 안전 재고, 낱개 기준 (원본에 없어 임의 기본값)
FROM dblink(
    'host=host.docker.internal port=5436 dbname=product user=postgres password=password',
    $$
        SELECT p.id, p.sku_code, p.name, ps.storage_type
        FROM product p
        LEFT JOIN product_spec ps ON ps.product_id = p.id
        WHERE p.type = 'UNIT'
        ORDER BY p.id
    $$
) AS src(id bigint, sku_code varchar, name varchar, storage_type varchar)
ON CONFLICT (id) DO NOTHING;

COMMIT;
