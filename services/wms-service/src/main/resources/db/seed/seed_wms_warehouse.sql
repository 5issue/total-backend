-- ================================================================
-- wms-service 로컬 개발용 시드 스크립트 — Warehouse / Location
--
-- 목적: 실제 컬리 물류센터/컬리나우 매장 8곳으로 Warehouse를 채운다. 창고/로케이션은
--       상품과 무관한 별개 개념이라 seed_wms_product.sql과는 분리된 파일로 관리한다
--       (product-service가 카테고리/상품 배치를 파일별로 나눈 것과 같은 이유).
--
--       Location(로케이션)은 이 중 김포물류센터 한 곳에 대해서만 예시로 채운다 —
--       실제 물류센터는 로케이션이 수천 개 단위라 8곳 전부를 지금 다 채우는 건
--       의미가 없고, 구조를 보여주는 샘플 하나면 충분하다고 판단했다. 다른 창고에도
--       필요해지면 이 파일의 "2. Location" 블록에서 warehouse code만 바꿔 재사용한다.
--
-- 원본에 없어 임의로 채운 값:
--   - code: 8곳 다 실제로는 없는 값이라 아래 규칙으로 만들었다.
--       물류센터(대형 창고) -> <지명>_DC (Distribution Center)
--       컬리나우(도심 소형 매장) -> CNOW_<지점명>
--   - is_active: 전부 true (운영 중인 곳이라고 가정).
--   - Location 상세(zone/aisle/rack/level/bin 체계, 랙/빈 개수)는 docs/erd-spec.md의
--     로케이션 설계 원칙(보관존=PALLET_RACK, 피킹존=SHELF_BIN, 버퍼=BUFFER, 피킹존
--     3x3 격자 바구니 F01~F09)을 그대로 따라 만든 예시다. 실제 김포물류센터의
--     레이아웃과는 무관하다.
--
-- 실행 방법:
--   docker exec -i kurly-postgres-wms psql -U postgres -d wms \
--     < src/main/resources/db/seed/seed_wms_warehouse.sql
--
-- 재실행 안전: Warehouse는 code UNIQUE, Location은 (warehouse_id, aisle, rack,
-- level, bin) UNIQUE라 ON CONFLICT DO NOTHING으로 재실행해도 중복 삽입되지 않는다.
-- ================================================================

BEGIN;

-- ============================================================
-- 1. Warehouse — 물류센터 4곳 + 컬리나우(도심 매장) 4곳
-- ============================================================
INSERT INTO warehouse (code, name, address, is_active) VALUES
    ('GIMPO_DC',      '김포물류센터',    '대한민국 경기도 김포시 고촌읍 아라육로 75', true),
    ('PYEONGTAEK_DC', '평택물류센터',    '대한민국 경기도 평택시 청북면 고렴리 1137', true),
    ('CHANGWON_DC',   '창원물류센터',    '대한민국 경상남도 창원시 진해구 두동 1883', true),
    ('ANSAN_DC',      '안산물류센터',    '대한민국 경기도 안산시 단원구 해봉로 232', true),
    ('CNOW_DMC',      '컬리나우DMC점',   '대한민국 서울특별시 서대문구 수색로 102 1층', true),
    ('CNOW_DOGOK',    '컬리나우도곡점',  '대한민국 서울특별시 강남구 도곡로 331 1층', true),
    ('CNOW_SEOCHO',   '컬리나우서초점',  '대한민국 서울특별시 서초구 서초대로 277, (기영빌딩) 지하 1층', true),
    ('CNOW_SONGPA',   '컬리나우송파점',  '대한민국 서울특별시 송파구 가락로 78, 2층 컬리나우송파점', true)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. Location — 김포물류센터(GIMPO_DC)만 예시로 채운다.
--
--    storage_type별로 물리적 통로(aisle)를 분리했다(같은 주소에 냉장/냉동 파레트가
--    같이 있을 수 없으므로): 상온=A, 냉장=B, 냉동=C 접두사.
--      보관존(PALLET_RACK): aisle <접두>S01, rack 01~02, level 1~2, bin 01~02
--      피킹존(SHELF_BIN):   aisle <접두>P01, rack 01, level 1, bin F01~F09(3x3 격자)
--      버퍼(BUFFER):        aisle DOCK, rack 01~02 (임시 하차 도크 2개)
-- ============================================================

-- 2-1. 버퍼(임시 하차 도크) 2개
INSERT INTO location (warehouse_id, location_type, storage_type, zone, aisle, rack, level, bin, status)
SELECT w.id, 'BUFFER', 'ROOM_TEMPERATURE', NULL, 'DOCK', lpad(n::text, 2, '0'), 1, '01', 'ACTIVE'
FROM warehouse w
CROSS JOIN generate_series(1, 2) AS n
WHERE w.code = 'GIMPO_DC'
ON CONFLICT (warehouse_id, aisle, rack, level, bin) DO NOTHING;

-- 2-2. 보관존(PALLET_RACK) — storage_type별 2랙 x 2단 x 2빈 = 8개 x 3종 = 24개
INSERT INTO location (warehouse_id, location_type, storage_type, zone, aisle, rack, level, bin, status)
SELECT w.id, 'PALLET_RACK', st.storage_type, st.zone_word || '_STORAGE',
       st.aisle_prefix || 'S01', lpad(rack::text, 2, '0'), level, lpad(bin::text, 2, '0'), 'ACTIVE'
FROM warehouse w
CROSS JOIN (VALUES
    ('ROOM_TEMPERATURE', 'ROOM', 'A'),
    ('REFRIGERATED',     'REFRI', 'B'),
    ('FROZEN',           'FROZEN', 'C')
) AS st(storage_type, zone_word, aisle_prefix)
CROSS JOIN generate_series(1, 2) AS rack
CROSS JOIN generate_series(1, 2) AS level
CROSS JOIN generate_series(1, 2) AS bin
WHERE w.code = 'GIMPO_DC'
ON CONFLICT (warehouse_id, aisle, rack, level, bin) DO NOTHING;

-- 2-3. 피킹존(SHELF_BIN) — storage_type별 3x3 격자(F01~F09) = 9개 x 3종 = 27개
INSERT INTO location (warehouse_id, location_type, storage_type, zone, aisle, rack, level, bin, status)
SELECT w.id, 'SHELF_BIN', st.storage_type, st.zone_word || '_PICKING',
       st.aisle_prefix || 'P01', '01', 1, 'F' || lpad(n::text, 2, '0'), 'ACTIVE'
FROM warehouse w
CROSS JOIN (VALUES
    ('ROOM_TEMPERATURE', 'ROOM', 'A'),
    ('REFRIGERATED',     'REFRI', 'B'),
    ('FROZEN',           'FROZEN', 'C')
) AS st(storage_type, zone_word, aisle_prefix)
CROSS JOIN generate_series(1, 9) AS n
WHERE w.code = 'GIMPO_DC'
ON CONFLICT (warehouse_id, aisle, rack, level, bin) DO NOTHING;

COMMIT;
