-- Warehouse가 담당하는 배송 권역. 한 창고가 여러 권역을 동시에 담당할 수 있어(예: 김포물류센터가
-- 경기서부+수도권을 같이 커버) PostgreSQL의 배열 컬럼으로 둔다 — 별도 테이블 없이 warehouse
-- 한 행에 여러 값을 담는다.
ALTER TABLE warehouse ADD COLUMN regions VARCHAR(20)[] NOT NULL DEFAULT '{}';

-- 배열의 모든 원소가 물류 배송 권역 값 중 하나인지 검증한다(<@ 는 "좌변 배열이 우변 배열의
-- 부분집합인가"를 뜻하는 PostgreSQL 연산자).
ALTER TABLE warehouse ADD CONSTRAINT chk_warehouse_regions CHECK (
    regions <@ ARRAY[
        'SEOUL_METRO', 'GYEONGGI_EAST', 'GYEONGGI_WEST', 'CHUNGCHEONG',
        'GANGWON', 'YEONGNAM', 'HONAM', 'JEJU'
    ]::VARCHAR(20)[]
);
