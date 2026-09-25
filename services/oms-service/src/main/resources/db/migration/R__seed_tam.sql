SET client_encoding = 'UTF8';

-- 1. TAM 권역 데이터 (수도권 + 5대 광역시 새벽배송, 기타 지역 일반택배)
INSERT INTO tam_regions (region_code, region_name, delivery_type, status, created_at, updated_at)
VALUES ('DAWN_SEOUL_METRO', '수도권 새벽배송 권역 (서울/경기/인천)', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('DAWN_BUSAN', '부산광역시 새벽배송 권역', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('DAWN_DAEGU', '대구광역시 새벽배송 권역', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('DAWN_DAEJEON', '대전광역시 새벽배송 권역', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('DAWN_GWANGJU', '광주광역시 새벽배송 권역', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('DAWN_ULSAN', '울산광역시 새벽배송 권역', 'DAWN', 'ACTIVE', NOW(), NOW()),
       ('PARCEL_NATIONWIDE', '전국 일반택배 권역', 'PARCEL', 'ACTIVE', NOW(), NOW())
ON CONFLICT (region_code) DO UPDATE
SET region_name   = EXCLUDED.region_name,
    delivery_type = EXCLUDED.delivery_type,
    status        = EXCLUDED.status,
    updated_at    = NOW();

-- 2. 배송 회차 데이터 (새벽배송: 23시 마감 -> 익일 07시 도착, 일반택배: 18시 마감 -> 2일뒤 도착)
INSERT INTO delivery_slots (region_id, slot_code, slot_name, cutoff_time, dispatch_time, delivery_start_time,
                            delivery_end_time, lead_days, is_active, created_at, updated_at)
SELECT id,
       'SLOT_' || region_code,
       region_name || ' 1회차',
       '23:00:00',
       '00:00:00',
       '01:00:00',
       '07:00:00',
       1,
       TRUE,
       NOW(),
       NOW()
FROM tam_regions
WHERE region_code IN ('DAWN_SEOUL_METRO', 'DAWN_BUSAN', 'DAWN_DAEGU', 'DAWN_DAEJEON', 'DAWN_GWANGJU',
                      'DAWN_ULSAN')
ON CONFLICT (region_id, slot_code) DO UPDATE
SET slot_name           = EXCLUDED.slot_name,
    cutoff_time         = EXCLUDED.cutoff_time,
    dispatch_time       = EXCLUDED.dispatch_time,
    delivery_start_time = EXCLUDED.delivery_start_time,
    delivery_end_time   = EXCLUDED.delivery_end_time,
    lead_days           = EXCLUDED.lead_days,
    is_active           = EXCLUDED.is_active,
    updated_at          = NOW();

INSERT INTO delivery_slots (region_id, slot_code, slot_name, cutoff_time, dispatch_time, delivery_start_time,
                            delivery_end_time, lead_days, is_active, created_at, updated_at)
SELECT id,
       'SLOT_PARCEL_NATIONWIDE',
       '전국 일반택배 1회차',
       '18:00:00',
       '20:00:00',
       '09:00:00',
       '18:00:00',
       2,
       TRUE,
       NOW(),
       NOW()
FROM tam_regions
WHERE region_code = 'PARCEL_NATIONWIDE'
ON CONFLICT (region_id, slot_code) DO UPDATE
SET slot_name           = EXCLUDED.slot_name,
    cutoff_time         = EXCLUDED.cutoff_time,
    dispatch_time       = EXCLUDED.dispatch_time,
    delivery_start_time = EXCLUDED.delivery_start_time,
    delivery_end_time   = EXCLUDED.delivery_end_time,
    lead_days           = EXCLUDED.lead_days,
    is_active           = EXCLUDED.is_active,
    updated_at          = NOW();
