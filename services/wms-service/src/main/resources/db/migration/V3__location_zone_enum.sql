-- location.zone을 자유 문자열에서 BUFFER/PICKING/STORAGE 3값 enum으로 좁힌다.
-- 기존 값(REFRI_STORAGE, FROZEN_PICKING 등 storage_type과 결합된 문자열, 또는 BUFFER는 NULL)은
-- location_type을 기준으로 정규화한다 — location_type과 이 매핑이 1:1이라 유실 없이 변환된다.
UPDATE location SET zone = 'BUFFER' WHERE location_type = 'BUFFER';
UPDATE location SET zone = 'STORAGE' WHERE location_type = 'PALLET_RACK';
UPDATE location SET zone = 'PICKING' WHERE location_type = 'SHELF_BIN';

ALTER TABLE location ALTER COLUMN zone SET NOT NULL;
ALTER TABLE location ADD CONSTRAINT chk_location_zone CHECK (zone IN ('BUFFER', 'PICKING', 'STORAGE'));
