-- ================================================================
-- product-service 로컬 개발용 시드 스크립트 — 카테고리 트리 + "컬리 픽" 56개 상품
--
-- 목적: STANDARD/DISPLAY 카테고리 전체 트리를 만들고, 그 위에 실제 컬리
--       상품명/이미지를 참고한 56개 상품을 GROUP(목록에 보이는 껍데기)
--       + UNIT(상세에서 보이는 실제 구매 단위) 구조로 채운다.
--
-- 이 파일과 seed_product_service_sample_products.sql 의 관계:
--   - category 테이블은 이 파일에서만 만든다(INSERT). seed_product_service_
--     sample_products.sql(가상 브랜드로 구성한 대량 GROUP 상품 100개)은
--     category INSERT를 전혀 하지 않고, 이 파일이 만든 STANDARD/DISPLAY
--     트리를 이름으로 조회해서 재사용한다 — 그래서 그 파일을 실행하려면
--     이 파일이 먼저 실행되어(카테고리 트리와 product.type CHECK 제약
--     수정이 끝나 있어야) 한다.
--   - product.sku_code 는 "SKU-K0001" ~ "SKU-K0056" 처럼 "K" 접두를 붙여서
--     seed_product_service_sample_products.sql 의 SKU-0001~SKU-0100 과
--     절대 겹치지 않게 했다. sku_code 에는 유니크 제약이 없어 겹쳐도
--     insert 자체는 실패하지 않지만, 겹치면 어느 파일이 만든 행인지 구분할
--     방법이 없어져 이후 카테고리 매핑/UNIT 연결 쿼리를 전부 시간 기반
--     조건으로 짜야 했다 — 접두사로 네임스페이스를 분리하면 그럴 필요가
--     아예 없어진다. created_at 도 sample_products 파일의 값(최소 1일 전)
--     보다 항상 최근으로 잡아, 정렬 기준이 created_at DESC 인 화면에서도
--     이 배치가 앞서 노출되게 했다.
--   - product_media 는 이 파일에서만 쓴다(sample_products 파일은 이미지를
--     안 채운다). product_media 는 GROUP 상품에만 매핑한다 — UNIT 에는
--     매핑하지 않고, 상세 화면에서 이미지가 필요하면 UNIT.parent_id 로
--     GROUP 을 찾아 그 product_media 를 그대로 재사용한다. 대표 이미지는
--     THUMBNAIL(sequence=1), 추가 상세컷이 있는 상품은 DETAIL(sequence=2부터)
--     로 이어 붙인다.
--
-- 구성 (테이블별로 한 블록씩):
--   0. product.type CHECK 제약 재정의
--   1. category         - STANDARD 최상위 15개
--   2. category         - STANDARD 하위
--   3. category         - DISPLAY (최상위 + 하위)
--   4. product          - GROUP 상품 56개 (SKU-K0001 ~ SKU-K0056)
--   5. product          - UNIT 상품 56개 (GROUP 당 정확히 1개, parent_id 로 연결)
--   6. product_media    - GROUP 상품 전부 THUMBNAIL 1장 (56건, UNIT 에는 매핑하지 않음)
--   6-1. product_media  - GROUP 추가 상세사진 (DETAIL, sequence 2부터, 데이터가 있는 상품만)
--   7. product_category - STANDARD 리프 매핑 (GROUP 기준, is_main = true, 56건 —
--                         반드시 1개, 원 데이터에 없던 브랜드는 상품명 앞
--                         대괄호를 그대로 brand 로 옮겨 담았다)
--   8. product_category - DISPLAY 매핑 (GROUP 기준, is_main = false, 상품 성격에
--                         맞춰 0~2개)
--   9. product_spec     - UNIT 상품 전부 (product_spec 은 UNIT 에만 매핑)
--   10. product_inventory - UNIT 상품 전부 (seed_product_service_sample_products.sql
--                          과 같은 산식)
--
-- 참고: price/discount_rate/short_description/brand/like_count/total_sales_count/
--       storage_type/packaging_type/attributes/재고 수량은 원본 목록에 없어서
--       상품 성격에 맞춰 임의로 채웠다(요청받은 대로).
--
-- 실행 방법 (Flyway 마이그레이션이 아닌 수동 실행 전용, 이 파일을 먼저 실행할 것):
--   docker exec -i kurly-postgres-product psql -U postgres -d product \
--     < src/main/resources/db/seed/seed_product_service.sql
--   docker exec -i kurly-postgres-product psql -U postgres -d product \
--     < src/main/resources/db/seed/seed_product_service_sample_products.sql
--
-- 주의: 멱등(idempotent)하지 않다. 재실행하면 category/product/product_media 가
--       중복 INSERT 된다. 재실행 전 아래 TRUNCATE 블록의 주석을 풀거나 직접
--       지울 것 — TRUNCATE ... category 는 CASCADE 로 product_category 도
--       함께 지우지만, product/product_media 는 category 와 무관하므로
--       같은 TRUNCATE 문에 함께 넣었다. category 를 지우면 이 파일과
--       sample_products 파일이 만든 product_category 매핑도 함께 사라지므로
--       두 파일을 처음부터 다시 실행해야 한다.
-- ================================================================

BEGIN;

-- ============================================================
-- (재실행용) 기존 시드 데이터 비우기 — 필요할 때만 주석 해제
-- ============================================================
-- TRUNCATE product_inventory, product_spec, product_category, product, category RESTART IDENTITY CASCADE;

-- ============================================================
-- 0. stale CHECK 제약조건 수정
--    엔티티(Product.ProductType)는 GROUP/UNIT 인데 DB에는 예전 스키마의
--    MAIN/OPTION 체크가 남아있어 GROUP/UNIT insert가 막혀 있었음.
-- ============================================================
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_type_check;
ALTER TABLE product ADD CONSTRAINT product_type_check CHECK (type IN ('GROUP', 'UNIT'));

-- ============================================================
-- 1. category - STANDARD 최상위 카테고리 15개
--    (하위 카테고리는 이 상위 카테고리들이 이미 있다고 가정하고 자식만
--     INSERT 하므로, 상위 15개를 먼저 만들어준다)
-- ============================================================
INSERT INTO category (name, parent_id, sequence, type) VALUES
    ('채소', NULL, 1, 'STANDARD'),
    ('과일·견과·쌀', NULL, 2, 'STANDARD'),
    ('수산·해산·건어물', NULL, 3, 'STANDARD'),
    ('정육·가공육·달걀', NULL, 4, 'STANDARD'),
    ('국·반찬·메인요리', NULL, 5, 'STANDARD'),
    ('간편식·밀키트·샐러드', NULL, 6, 'STANDARD'),
    ('면·양념·오일', NULL, 7, 'STANDARD'),
    ('생수·음료', NULL, 8, 'STANDARD'),
    ('커피·차', NULL, 9, 'STANDARD'),
    ('간식·과자·떡', NULL, 10, 'STANDARD'),
    ('베이커리', NULL, 11, 'STANDARD'),
    ('유제품', NULL, 12, 'STANDARD'),
    ('건강식품', NULL, 13, 'STANDARD'),
    ('와인·위스키·데낄라', NULL, 14, 'STANDARD'),
    ('전통주', NULL, 15, 'STANDARD');

-- ============================================================
-- 2. category - STANDARD 하위 카테고리
-- ============================================================
INSERT INTO category (name, parent_id, sequence, type)
SELECT c.name, p.id, c.seq, 'STANDARD'
FROM (VALUES
    ('채소', '친환경', 1), ('채소', '고구마·감자·당근', 2), ('채소', '시금치·쌈채소·나물', 3), ('채소', '브로콜리·파프리카·양배추', 4), ('채소', '양파·대파·마늘·배추', 5), ('채소', '오이·호박·고추', 6), ('채소', '냉동·이색·간편채소', 7), ('채소', '콩나물·버섯', 8),
    ('과일·견과·쌀', '친환경', 1), ('과일·견과·쌀', '제철과일', 2), ('과일·견과·쌀', '국산과일', 3), ('과일·견과·쌀', '수입과일', 4), ('과일·견과·쌀', '간편과일', 5), ('과일·견과·쌀', '냉동·건과일', 6), ('과일·견과·쌀', '견과류', 7), ('과일·견과·쌀', '쌀·잡곡', 8),
    ('수산·해산·건어물', '제철수산', 1), ('수산·해산·건어물', '생선류', 2), ('수산·해산·건어물', '굴비·반건류', 3), ('수산·해산·건어물', '연어·참치', 4), ('수산·해산·건어물', '회·탕류', 5), ('수산·해산·건어물', '오징어·낙지·문어', 6), ('수산·해산·건어물', '해산물·전복·조개류', 7), ('수산·해산·건어물', '새우·게·랍스터', 8), ('수산·해산·건어물', '수산가공품', 9), ('수산·해산·건어물', '명란', 10), ('수산·해산·건어물', '젓갈·장류', 11), ('수산·해산·건어물', '간편구이', 12), ('수산·해산·건어물', '김·미역·해조류', 13), ('수산·해산·건어물', '멸치·황태·다시팩', 14), ('수산·해산·건어물', '조미오징어·어포·쥐포', 15),
    ('정육·가공육·달걀', '국내산 소고기', 1), ('정육·가공육·달걀', '수입산 소고기', 2), ('정육·가공육·달걀', '국내산 돼지고기', 3), ('정육·가공육·달걀', '수입산 돼지고기·양고기', 4), ('정육·가공육·달걀', '닭·오리고기', 5), ('정육·가공육·달걀', '식단관리용 가공육', 6), ('정육·가공육·달걀', '양념육', 7), ('정육·가공육·달걀', '돈까스·떡갈비·함박', 8), ('정육·가공육·달걀', '소시지·베이컨·하몽', 9), ('정육·가공육·달걀', '달걀·가공란', 10),
    ('국·반찬·메인요리', '국·탕·찌개', 1), ('국·반찬·메인요리', '밑반찬', 2), ('국·반찬·메인요리', '김치·젓갈·장류', 3), ('국·반찬·메인요리', '두부·어묵·부침개', 4), ('국·반찬·메인요리', '메인요리', 5),
    ('간편식·밀키트·샐러드', '밀키트', 1), ('간편식·밀키트·샐러드', '샐러드·샌드위치', 2), ('간편식·밀키트·샐러드', '선식·시리얼', 3), ('간편식·밀키트·샐러드', '도시락·밥류', 4), ('간편식·밀키트·샐러드', '짜장·짬뽕·파스타·면류', 5), ('간편식·밀키트·샐러드', '떡볶이·튀김·순대', 6), ('간편식·밀키트·샐러드', '치킨·피자·핫도그·만두', 7),
    ('면·양념·오일', '라면', 1), ('면·양념·오일', '파스타·면류·조리용 떡', 2), ('면·양념·오일', '밀가루·가루·믹스', 3), ('면·양념·오일', '햄·통조림·병조림', 4), ('면·양념·오일', '죽·스프·카레', 5), ('면·양념·오일', '양념·액젓·장류', 6), ('면·양념·오일', '식용유·참기름·오일', 7), ('면·양념·오일', '식초·소스·드레싱', 8), ('면·양념·오일', '소금·설탕·향신료', 9), ('면·양념·오일', '조미료', 10), ('면·양념·오일', '면·양념·오일 선물세트', 11), ('면·양념·오일', '대용량 가공식품', 12),
    ('생수·음료', '생수/얼음', 1), ('생수·음료', '탄산수', 2), ('생수·음료', '탄산·스포츠음료', 3), ('생수·음료', '과일·야채음료', 4), ('생수·음료', '차음료', 5), ('생수·음료', '어린이음료·선물세트', 6),
    ('커피·차', '원두커피', 1), ('커피·차', '드립백/커피백', 2), ('커피·차', '캡슐커피', 3), ('커피·차', '콜드브루', 4), ('커피·차', '커피음료', 5), ('커피·차', '인스턴트 커피', 6), ('커피·차', '곡물차/전통차', 7), ('커피·차', '홍차/녹차/보이차', 8), ('커피·차', '허브차/꽃차/과일차', 9), ('커피·차', '코코아/밀크티/기타 차', 10), ('커피·차', '액상차/청', 11), ('커피·차', '커피/차 선물세트', 12),
    ('간식·과자·떡', '과자·간식', 1), ('간식·과자·떡', '쿠키·비스킷·크래커', 2), ('간식·과자·떡', '초콜릿·젤리·캔디', 3), ('간식·과자·떡', '유아 과자·간식', 4), ('간식·과자·떡', '대용량 과자·간식', 5), ('간식·과자·떡', '떡·한과', 6),
    ('베이커리', '✨이 주의 추천 베이커리✨', 1), ('베이커리', '식빵·모닝빵·베이글', 2), ('베이커리', '간식빵', 3), ('베이커리', '타르트·파이', 4), ('베이커리', '디저트', 5), ('베이커리', '케이크', 6), ('베이커리', '잼·스프레드', 7),
    ('유제품', '우유·두유', 1), ('유제품', '요거트·생크림', 2), ('유제품', '자연치즈', 3), ('유제품', '가공치즈', 4), ('유제품', '버터', 5), ('유제품', '아이스크림', 6),
    ('건강식품', '비타민·미네랄', 1), ('건강식품', '건강즙·건강음료', 2), ('건강식품', '홍삼·인삼', 3), ('건강식품', '꿀·과일청', 4), ('건강식품', '영양제', 5), ('건강식품', '유산균', 6), ('건강식품', '건강분말·건강환', 7), ('건강식품', '체중관리', 8), ('건강식품', '프로틴', 9), ('건강식품', '이너뷰티', 10), ('건강식품', '유아동/키즈', 11),
    ('와인·위스키·데낄라', 'CU BAR 픽업', 1), ('와인·위스키·데낄라', '라빈리커 픽업', 2), ('와인·위스키·데낄라', '와인앤모어 픽업', 3), ('와인·위스키·데낄라', '레드텅 와인샵 픽업', 4), ('와인·위스키·데낄라', '레드와인', 5), ('와인·위스키·데낄라', '화이트·로제와인', 6), ('와인·위스키·데낄라', '내추럴와인', 7), ('와인·위스키·데낄라', '샴페인·스파클링', 8), ('와인·위스키·데낄라', '스위트와인', 9), ('와인·위스키·데낄라', '위스키·리큐르', 10), ('와인·위스키·데낄라', '사케', 11), ('와인·위스키·데낄라', '맥주·하이볼', 12), ('와인·위스키·데낄라', '고량주', 13), ('와인·위스키·데낄라', '데낄라', 14), ('와인·위스키·데낄라', '논알콜·무알콜', 15), ('와인·위스키·데낄라', '와인·위스키용품', 16),
    ('전통주', '선물세트', 1), ('전통주', '막걸리·탁주', 2), ('전통주', '증류주·약주·청주', 3), ('전통주', '과실주·리큐르', 4), ('전통주', '라빈리커 픽업', 5)
) AS c(parent_name, name, seq)
JOIN category p ON p.name = c.parent_name AND p.parent_id IS NULL AND p.type = 'STANDARD';

-- ============================================================
-- 3. category - DISPLAY 카테고리 (최상위 + 하위)
-- ============================================================
INSERT INTO category (name, parent_id, sequence, type)
VALUES
    ('베스트', NULL, 1, 'DISPLAY'),
    ('단독', NULL, 2, 'DISPLAY'),
    ('멤버스', NULL, 3, 'DISPLAY'),
    ('세일', NULL, 4, 'DISPLAY');

INSERT INTO category (name, parent_id, sequence, type)
SELECT c.name, p.id, c.seq, 'DISPLAY'
FROM (VALUES
    ('베스트', '베스트 랭킹', 1),
    ('베스트', '간편식', 2),
    ('베스트', '정육/달걀', 3),
    ('베스트', '과일', 4),
    ('베스트', '베이커리', 5),
    ('베스트', '수산', 6),
    ('베스트', '채소/쌀', 7),
    ('베스트', '간식', 8),
    ('단독', '많이 보는', 1),
    ('단독', '단독의 발견', 2),
    ('단독', '맞춤추천', 3)
) AS c(parent_name, name, seq)
JOIN category p ON p.name = c.parent_name AND p.parent_id IS NULL AND p.type = 'DISPLAY';

-- ============================================================
-- 4. product - GROUP 상품 56개 (SKU-K0001 ~ SKU-K0056)
--    모두 status='SALE', type='GROUP', parent_id=NULL.
--    created_at 은 seed_product_service_sample_products.sql 의 최솟값(NOW() - 1 day)보다 항상
--    최근이 되도록 촘촘한 간격(분 단위)만 뒀다 — 최신순 정렬에서 이 배치가
--    항상 앞에 오게 하기 위함.
-- ============================================================
INSERT INTO product (sku_code, parent_id, name, short_description, brand, price, discount_rate, sale_price, type, status, like_count, total_sales_count, created_at) VALUES
    ('SKU-K0001', NULL, '전용목장우유 900mL', '연세우유와 마켓컬리가 함께 만든 전용목장 신선우유', '연세우유 x 마켓컬리', 3200, 5, 3040, 'GROUP', 'SALE', 3200, 18900, NOW() - INTERVAL '1 minute' * 1),
    ('SKU-K0002', NULL, '햄 가득 송탄식 부대찌개', '햄과 사리를 가득 채운 얼큰한 송탄식 부대찌개', '차려낸', 9900, 10, 8910, 'GROUP', 'SALE', 1450, 3890, NOW() - INTERVAL '1 minute' * 2),
    ('SKU-K0003', NULL, '감숙왕 바나나 2종', '달콤하고 부드러운 스미후루 감숙왕 바나나', '스미후루', 5900, 8, 5428, 'GROUP', 'SALE', 2670, 9870, NOW() - INTERVAL '1 minute' * 3),
    ('SKU-K0004', NULL, '호두과자 (30g X 10개)', '우리쌀 반죽에 호두를 가득 채운 전통 호두과자', '우리쌀 왕호두', 6900, 6, 6486, 'GROUP', 'SALE', 1230, 4120, NOW() - INTERVAL '1 minute' * 4),
    ('SKU-K0005', NULL, '태우한우 1+ 실속 구이 세트 (냉동)', '마블링 좋은 1+ 등급 한우로 구성한 실속 구이 세트', '선물세트', 49900, 15, 42415, 'GROUP', 'SALE', 980, 1670, NOW() - INTERVAL '1 minute' * 5),
    ('SKU-K0006', NULL, '초코송이 36g x 18입', '바삭한 과자에 초콜릿을 입힌 오리온 초코송이', '오리온', 12900, 5, 12255, 'GROUP', 'SALE', 3450, 15230, NOW() - INTERVAL '1 minute' * 6),
    ('SKU-K0007', NULL, '브로콜리 1입 (250g)', '아삭한 식감의 KF365 국내산 브로콜리', 'KF365', 3900, 8, 3588, 'GROUP', 'SALE', 1120, 4560, NOW() - INTERVAL '1 minute' * 7),
    ('SKU-K0008', NULL, '듬뿍 무화과 잼 140g', '포비베이글이 만든 무화과 과육 듬뿍 잼', '포비베이글', 8900, 6, 8366, 'GROUP', 'SALE', 780, 1980, NOW() - INTERVAL '1 minute' * 8),
    ('SKU-K0009', NULL, '부산식 얼큰 낙곱새', '낙지·곱창·새우를 한 번에, 부산식 얼큰 낙곱새', '일상식탁', 13900, 12, 12232, 'GROUP', 'SALE', 1670, 3450, NOW() - INTERVAL '1 minute' * 9),
    ('SKU-K0010', NULL, '소불고기 전골', '깊은 육수에 소불고기를 더한 사리원 전골', '사리원', 15900, 10, 14310, 'GROUP', 'SALE', 2120, 5670, NOW() - INTERVAL '1 minute' * 10),
    ('SKU-K0011', NULL, '야채참치 150g', '야채를 더해 담백한 동원 야채참치', '동원', 3200, 4, 3072, 'GROUP', 'SALE', 4120, 23450, NOW() - INTERVAL '1 minute' * 11),
    ('SKU-K0012', NULL, '국산콩 두부 3종 (택1)', '국산 콩으로 만든 Kurly''s 두부 3종 중 선택', 'Kurly''s', 2900, 5, 2755, 'GROUP', 'SALE', 1890, 12340, NOW() - INTERVAL '1 minute' * 12),
    ('SKU-K0013', NULL, '떡볶이 (2~3인분)', '부산 상국이네의 매콤달콤 즉석 떡볶이', '부산 상국이네', 8900, 9, 8099, 'GROUP', 'SALE', 2340, 8120, NOW() - INTERVAL '1 minute' * 13),
    ('SKU-K0014', NULL, '클래식 우유 식빵 420g', '우유를 듬뿍 넣어 부드럽게 구운 클래식 식빵', '컬리베이커리', 4200, 6, 3948, 'GROUP', 'SALE', 2670, 9340, NOW() - INTERVAL '1 minute' * 14),
    ('SKU-K0015', NULL, '숙성 광어&우럭 모둠회 200g (냉장)', '은하수산이 당일 손질한 광어·우럭 모둠회', '은하수산', 22900, 15, 19465, 'GROUP', 'SALE', 1230, 2670, NOW() - INTERVAL '1 minute' * 15),
    ('SKU-K0016', NULL, '아삭 복숭아 1.2kg (딱복)', '과육이 단단하고 아삭한 딱복 품종 복숭아', '청과마켓', 13900, 12, 12232, 'GROUP', 'SALE', 2340, 6780, NOW() - INTERVAL '1 minute' * 16),
    ('SKU-K0017', NULL, '16brix 고당도 애플청포도 500g (미국산)', '당도 16brix 이상만 골라 담은 미국산 청포도', 'KF365', 9900, 10, 8910, 'GROUP', 'SALE', 1890, 5230, NOW() - INTERVAL '1 minute' * 17),
    ('SKU-K0018', NULL, '바이오 그릭 요거트 무가당 플레인 400g', '유청을 제거해 꾸덕한 매일 무가당 그릭요거트', '매일', 5900, 8, 5428, 'GROUP', 'SALE', 3120, 14560, NOW() - INTERVAL '1 minute' * 18),
    ('SKU-K0019', NULL, '서해안 갯벌 동죽조개 1kg (생물)', '대흥이 서해안 갯벌에서 채취한 싱싱한 동죽조개', '대흥', 12900, 14, 11094, 'GROUP', 'SALE', 670, 1230, NOW() - INTERVAL '1 minute' * 19),
    ('SKU-K0020', NULL, '전용목장우유 1.8L', '연세우유와 마켓컬리가 함께 만든 대용량 목장우유', '연세우유 x 마켓컬리', 5900, 5, 5605, 'GROUP', 'SALE', 2340, 12340, NOW() - INTERVAL '1 minute' * 20),
    ('SKU-K0021', NULL, '나 100% 우유 1000mL', '원유 함량 100%, 서울우유 나 100% 우유', '서울우유', 2900, 3, 2813, 'GROUP', 'SALE', 3670, 21230, NOW() - INTERVAL '1 minute' * 21),
    ('SKU-K0022', NULL, '제주 목초 우유 무항생제 750mL', '제주 목초지에서 자란 소의 무항생제 우유', '제주우유', 4200, 5, 3990, 'GROUP', 'SALE', 1230, 4560, NOW() - INTERVAL '1 minute' * 22),
    ('SKU-K0023', NULL, 'A2 플러스 우유 710mL', '소화가 편한 A2 단백질만 담은 서울우유', '서울우유', 4900, 4, 4704, 'GROUP', 'SALE', 1670, 5670, NOW() - INTERVAL '1 minute' * 23),
    ('SKU-K0024', NULL, '나100% 우유 2.3L 2종 (택1)', '온 가족이 함께 마시는 대용량 서울우유', '서울우유', 6900, 5, 6555, 'GROUP', 'SALE', 2120, 8900, NOW() - INTERVAL '1 minute' * 24),
    ('SKU-K0025', NULL, '제주목장 무항생제 목초 우유 900mL', '제주축산농협이 담은 무항생제 목초 우유', '제주축산농협', 4900, 6, 4606, 'GROUP', 'SALE', 890, 2670, NOW() - INTERVAL '1 minute' * 25),
    ('SKU-K0026', NULL, '저지우유 750mL', '고소하고 진한 제주 저지종 원유 우유', '제주우유', 5900, 7, 5487, 'GROUP', 'SALE', 1560, 3890, NOW() - INTERVAL '1 minute' * 26),
    ('SKU-K0027', NULL, '저지방 전용목장 우유 900mL', '지방은 줄이고 고소함은 그대로, 저지방 목장우유', '연세우유 x 마켓컬리', 3400, 5, 3230, 'GROUP', 'SALE', 1230, 5670, NOW() - INTERVAL '1 minute' * 27),
    ('SKU-K0028', NULL, '소화가 잘되는 우유 930mL 2종(택 1)', '유당을 분해해 속이 편한 매일 우유', '매일', 3900, 4, 3744, 'GROUP', 'SALE', 2890, 15670, NOW() - INTERVAL '1 minute' * 28),
    ('SKU-K0029', NULL, '아몬드브리즈 950mL 2종 (택1)', '고소한 아몬드로 만든 식물성 음료', '매일', 4900, 6, 4606, 'GROUP', 'SALE', 1780, 6780, NOW() - INTERVAL '1 minute' * 29),
    ('SKU-K0030', NULL, '모짜렐라 슈레드 치즈 2종(택1)', '쭉 늘어나는 상하치즈 모짜렐라 슈레드', '상하치즈', 8900, 10, 8010, 'GROUP', 'SALE', 2340, 8900, NOW() - INTERVAL '1 minute' * 30),
    ('SKU-K0031', NULL, '동물복지 유정란 20구', 'Kurly''s 동물복지 인증 농장의 신선 유정란', 'Kurly''s', 9900, 8, 9108, 'GROUP', 'SALE', 2670, 12340, NOW() - INTERVAL '1 minute' * 31),
    ('SKU-K0032', NULL, '바로먹는 아보카도 3입 (페루산)', '후숙 완료로 바로 먹는 페루산 아보카도', '청과마켓', 6900, 10, 6210, 'GROUP', 'SALE', 1890, 7230, NOW() - INTERVAL '1 minute' * 32),
    ('SKU-K0033', NULL, '가지 2입', 'KF365 국내산 가지, 볶음·구이에 두루 좋아요', 'KF365', 2900, 6, 2726, 'GROUP', 'SALE', 670, 2340, NOW() - INTERVAL '1 minute' * 33),
    ('SKU-K0034', NULL, '애호박 1개', 'KF365 국내산 애호박, 찌개·전에 딱', 'KF365', 1900, 5, 1805, 'GROUP', 'SALE', 780, 3120, NOW() - INTERVAL '1 minute' * 34),
    ('SKU-K0035', NULL, '깐마늘 200g (26년 햇)', '올해 수확한 햇마늘을 깐 KF365 깐마늘', 'KF365', 4900, 7, 4557, 'GROUP', 'SALE', 1450, 6780, NOW() - INTERVAL '1 minute' * 35),
    ('SKU-K0036', NULL, '구르메 파스타면 6종', '이탈리아산 듀럼밀 데체코 구르메 파스타면', '데체코', 5900, 9, 5369, 'GROUP', 'SALE', 980, 2670, NOW() - INTERVAL '1 minute' * 36),
    ('SKU-K0037', NULL, '암꽃게 간장게장', '상하농원이 담근 짭짤하고 깊은 암꽃게 간장게장', '상하농원', 29900, 12, 26312, 'GROUP', 'SALE', 1670, 3450, NOW() - INTERVAL '1 minute' * 37),
    ('SKU-K0038', NULL, '흑미밥 작은공기 130g*3', '전자레인지로 2분, 간편한 햇반 흑미밥', '햇반', 4200, 5, 3990, 'GROUP', 'SALE', 3450, 21230, NOW() - INTERVAL '1 minute' * 38),
    ('SKU-K0039', NULL, '스콜피온스 엑스트라 핫소스 2종', '강렬한 매운맛의 타바스코 스콜피온스 핫소스', '타바스코', 8900, 6, 8366, 'GROUP', 'SALE', 670, 1890, NOW() - INTERVAL '1 minute' * 39),
    ('SKU-K0040', NULL, '국산 채소믹스 500g', '여러 채소를 한 번에, 손질 필요 없는 채소믹스', '청과마켓', 4900, 8, 4508, 'GROUP', 'SALE', 1230, 4560, NOW() - INTERVAL '1 minute' * 40),
    ('SKU-K0041', NULL, '고추참치 85g x 8캔', '칼칼한 고추맛을 더한 동원 고추참치', '동원', 12900, 10, 11610, 'GROUP', 'SALE', 3120, 18900, NOW() - INTERVAL '1 minute' * 41),
    ('SKU-K0042', NULL, '고기만두', '전주 베테랑이 빚은 육즙 가득 고기만두', '전주 베테랑', 9900, 12, 8712, 'GROUP', 'SALE', 2450, 9870, NOW() - INTERVAL '1 minute' * 42),
    ('SKU-K0043', NULL, '통밀발효종빵 300g', '더브레드블루가 발효종으로 구운 통밀빵', '더브레드블루', 6900, 7, 6417, 'GROUP', 'SALE', 1120, 3450, NOW() - INTERVAL '1 minute' * 43),
    ('SKU-K0044', NULL, '동물복지 치킨 너겟 오리지널', '동물복지 인증 닭고기로 만든 풀무원 너겟', '풀무원', 7900, 9, 7189, 'GROUP', 'SALE', 1890, 6780, NOW() - INTERVAL '1 minute' * 44),
    ('SKU-K0045', NULL, '훈제오리 300g ~', '은은한 훈제향의 KF365 슬라이스 훈제오리', 'KF365', 12900, 15, 10965, 'GROUP', 'SALE', 980, 2890, NOW() - INTERVAL '1 minute' * 45),
    ('SKU-K0046', NULL, '16brix 고당도 상주 샤인머스캣 1.5kg (박스)', '당도만 골라 담은 상주산 씨 없는 샤인머스캣', '청과마켓', 21900, 18, 17958, 'GROUP', 'SALE', 3120, 7890, NOW() - INTERVAL '1 minute' * 46),
    ('SKU-K0047', NULL, '갈비탕', '오랜 시간 우려낸 사미헌 갈비탕', '사미헌', 14900, 10, 13410, 'GROUP', 'SALE', 1780, 4120, NOW() - INTERVAL '1 minute' * 47),
    ('SKU-K0048', NULL, '대추방울토마토 750g', '당도 높은 KF365 대추방울토마토', 'KF365', 6900, 8, 6348, 'GROUP', 'SALE', 2120, 8340, NOW() - INTERVAL '1 minute' * 48),
    ('SKU-K0049', NULL, '엑스트라버진 올리브 오일 500mL', '이탈리아산 올리브만 압착한 올리타리아 오일', '올리타리아', 12900, 12, 11352, 'GROUP', 'SALE', 1450, 3670, NOW() - INTERVAL '1 minute' * 49),
    ('SKU-K0050', NULL, '친환경 베이비 바질 10g', '농약 없이 키운 향긋한 베이비 바질', '허브팜', 3900, 5, 3705, 'GROUP', 'SALE', 560, 1230, NOW() - INTERVAL '1 minute' * 50),
    ('SKU-K0051', NULL, '항공직송 노르웨이 생연어 6종, 택1 (냉장)', '항공으로 직송한 KF365 노르웨이 생연어', 'KF365', 16900, 16, 14196, 'GROUP', 'SALE', 2670, 6780, NOW() - INTERVAL '1 minute' * 51),
    ('SKU-K0052', NULL, '친환경 양파 2종', '바름팜이 친환경 인증받아 키운 양파', '바름팜', 5900, 8, 5428, 'GROUP', 'SALE', 780, 2120, NOW() - INTERVAL '1 minute' * 52),
    ('SKU-K0053', NULL, '한끼 채소 손질 대파 100g', '씻고 다듬을 필요 없이 바로 쓰는 손질 대파', '컬리팜', 2900, 5, 2755, 'GROUP', 'SALE', 1890, 9870, NOW() - INTERVAL '1 minute' * 53),
    ('SKU-K0054', NULL, '떡갈비 345g', '조선호텔 셰프가 만든 육즙 가득 떡갈비', '조선호텔', 11900, 12, 10472, 'GROUP', 'SALE', 1230, 3670, NOW() - INTERVAL '1 minute' * 54),
    ('SKU-K0055', NULL, '체다 슬라이스 치즈 15매입', '고소한 소와나무 체다 슬라이스 치즈', '소와나무', 5900, 7, 5487, 'GROUP', 'SALE', 1670, 5670, NOW() - INTERVAL '1 minute' * 55),
    ('SKU-K0056', NULL, '파슬리 12g', '요리 마무리에 향과 색을 더하는 파슬리', '허브&스파이스마켓', 2900, 5, 2755, 'GROUP', 'SALE', 450, 980, NOW() - INTERVAL '1 minute' * 56);

-- ============================================================
-- 5. product - UNIT 상품 56개 (GROUP 당 정확히 1개)
--    원본 목록에 옵션(용량/중량) 정보가 없어 GROUP 과 동일한 가격으로 UNIT을
--    1개씩만 둔다. sku_code 규칙은 seed_product_service_sample_products.sql 과 동일하게
--    <GROUP sku>-1. short_description 은 UNIT 이라 NULL(컬럼 목록에서 제외).
-- ============================================================
INSERT INTO product (sku_code, parent_id, name, brand, price, discount_rate, sale_price, type, status, like_count, total_sales_count, created_at)
SELECT parent.sku_code || '-1', parent.id, parent.name, parent.brand, parent.price, parent.discount_rate, parent.sale_price, 'UNIT', 'SALE', 0, 0, parent.created_at
FROM product parent
WHERE parent.type = 'GROUP' AND parent.sku_code LIKE 'SKU-K%';

-- ============================================================
-- 6. product_media - GROUP 상품 전부 THUMBNAIL 1장 (56건, UNIT 에는 매핑하지 않음)
--    product_media 는 GROUP 기준으로만 저장한다. 상세 화면에서 UNIT 의
--    이미지가 필요하면 UNIT.parent_id 로 GROUP 을 찾아 이 product_media 를
--    그대로 재사용하면 된다. s3_key 는 원본 URL이 S3 경유가 아니라 NULL로
--    둔다.
--    개발 DB에 id를 직접 지정해 넣은 수동 테스트 행이 남아있으면 시퀀스가
--    그 값을 따라가지 못해 identity 충돌이 날 수 있어, 삽입 전 시퀀스를
--    현재 최댓값 이후로 맞춰준다.
-- ============================================================
SELECT setval('product_media_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM product_media), 1));

INSERT INTO product_media (product_id, media_url, s3_key, media_type, media_role, sequence)
SELECT pr.id, m.media_url, NULL, 'IMAGE', 'THUMBNAIL', 1
FROM (VALUES
    ('SKU-K0001', 'https://img-cf.kurly.com/shop/data/goods/163001/001.jpg'),
    ('SKU-K0002', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/5588e602-4670-4eba-80bd-0d8690493ad3.jpg'),
    ('SKU-K0003', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/8d6ac084-22bd-47c2-8d40-867eb49a181b.jpg'),
    ('SKU-K0004', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/9de31af1-7776-4cd3-890c-72a0b8f2459f.jpg'),
    ('SKU-K0005', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/fb54260a-230c-43a6-9f6d-a9a4f4c38761.jpg'),
    ('SKU-K0006', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1626336015616l0.jpg'),
    ('SKU-K0007', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/a0648262-58dc-463c-a8a6-4b1cb6e0744b.jpg'),
    ('SKU-K0008', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/cd60152f-7d7f-4c60-8d0a-c050fb4eedcb.jpg'),
    ('SKU-K0009', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/39b48630-0359-4f08-aea3-8193aea1fc52.jpg'),
    ('SKU-K0010', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/1e143634-65e2-40e9-bbc8-8580f1b0e084.jpg'),
    ('SKU-K0011', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/afad02a9-f05a-45cf-8dfb-08d7164a753c.jpg'),
    ('SKU-K0012', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/9042a54d-e3a2-4ed1-a4fc-26ddae4f9da3.jpg'),
    ('SKU-K0013', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1646963339667l0.jpg'),
    ('SKU-K0014', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1648209386376l0.jpg?v=0531'),
    ('SKU-K0015', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/be180458-fb74-4717-a859-7dabe16e0fc4.jpg'),
    ('SKU-K0016', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/2e98dcf1-3940-499a-9476-0e1cb1908074.jpg'),
    ('SKU-K0017', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1637924123548l0.jpeg'),
    ('SKU-K0018', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/22702b7a-669f-4e3d-9c2b-4c828993b200.jpg'),
    ('SKU-K0019', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1653373934148l0.jpg'),
    ('SKU-K0020', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/5774eddb-54dc-4438-a997-64b0d7b9fa62.jpg'),
    ('SKU-K0021', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/efd06fe8-35ab-4256-af74-fe93cb633962.jpg'),
    ('SKU-K0022', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/ff4b6c93-6663-462f-92c9-1f70bed9588d.jpg'),
    ('SKU-K0023', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/a68388ae-be01-44f5-92cc-11500e099fda.jpg'),
    ('SKU-K0024', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/b263decf-54e2-451c-8565-4cb2ebb31ac2.jpg'),
    ('SKU-K0025', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/6125a945-e004-4ab4-8d94-66dc531ede6c.jpg'),
    ('SKU-K0026', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/71869545-c930-4ad6-97fc-29956a3cab59.jpg'),
    ('SKU-K0027', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/e5fb3db5-7822-4421-8289-dd941d062473.jpg'),
    ('SKU-K0028', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/58f40f48-f6fb-4dfc-9a6e-8b0482bd54ac.jpg'),
    ('SKU-K0029', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/890d39f8-ad34-4fb6-bf0f-65dd47cfe2f3.jpg'),
    ('SKU-K0030', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/64e9694a-d11f-4d45-90e1-70f7423f5aea.jpg'),
    ('SKU-K0031', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/6969f9b7-49ad-46ec-9b36-9e0cfcf4499f.jpg?v=0531'),
    ('SKU-K0032', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/8a54e97d-4145-43e5-a646-c44c5cc1ecfa.jpg'),
    ('SKU-K0033', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/874fa6f5-9429-4212-b0d7-ced3504323d4.jpg'),
    ('SKU-K0034', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/6a175689-ff66-4838-b04d-cbed2ec3e3be.jpg?v=0531'),
    ('SKU-K0035', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/94bb340b-c044-471d-86b6-a44cb929e77f.jpg'),
    ('SKU-K0036', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/7d197eab-7b3e-4f9a-a360-5bae52f18181.jpeg'),
    ('SKU-K0037', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/411141f3-07a0-4c7a-8ddf-68e4499e04a8.jpg'),
    ('SKU-K0038', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/19dd02c5-01a2-4e4f-ae7a-52e24465b1c1.jpg'),
    ('SKU-K0039', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/24414511-537f-4060-8b00-1e5c8ceee417.jpg'),
    ('SKU-K0040', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/8a813917-6a80-4a23-8bf1-074df5875493.jpg'),
    ('SKU-K0041', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1590468591591l0.jpg'),
    ('SKU-K0042', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1657612696912l0.jpg?v=0531'),
    ('SKU-K0043', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1544756943803l0.jpg'),
    ('SKU-K0044', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/e7a4f936-fcc0-4fd1-8034-c6e6ca97eddd.jpg'),
    ('SKU-K0045', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/23a32590-9613-4fe4-b563-f2d5d9a59b25.jpg'),
    ('SKU-K0046', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1609115764700l0.jpg'),
    ('SKU-K0047', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/c4d41015-d188-4c68-b3e9-36968bf2110a.jpeg'),
    ('SKU-K0048', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/4436fdec-039d-4683-b8f3-7ebf677ad190.jpg'),
    ('SKU-K0049', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1587519777879l0.jpg'),
    ('SKU-K0050', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1639117885993l0.jpg'),
    ('SKU-K0051', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/b7723fd5-1e2a-437b-be5f-61d3e8ca2c9f.jpg'),
    ('SKU-K0052', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1656563327799l0.jpg'),
    ('SKU-K0053', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1567581895960l0.jpg'),
    ('SKU-K0054', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/09a0f576-c2cc-4d00-8ed4-c7d36c7b6967.jpg'),
    ('SKU-K0055', 'https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/product/image/5e61f4ed-f02b-4864-9a6a-6d17bda43f24.jpg'),
    ('SKU-K0056', 'https://img-cf.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/quality/85/src/shop/data/goods/1607930207810l0.jpg')
) AS m(sku_code, media_url)
JOIN product pr ON pr.sku_code = m.sku_code AND pr.type = 'GROUP';

-- ============================================================
-- 6-1. product_media - GROUP 추가 상세사진 (DETAIL, sequence 2부터)
--    위 6번 블록은 상품당 대표 이미지 1장(sequence=1)만 넣는다. 그 외에 실제
--    상세페이지 하단에 들어가는 상세컷이 별도로 있는 상품은 여기에 GROUP
--    기준으로 추가한다. sequence 1은 대표 이미지가 이미 차지하고 있으니
--    2부터 순서대로 붙인다.
-- ============================================================
INSERT INTO product_media (product_id, media_url, s3_key, media_type, media_role, sequence)
SELECT pr.id, m.media_url, NULL, 'IMAGE', 'DETAIL', m.sequence
FROM (VALUES
    ('SKU-K0001', 2, 'https://product-image.kurly.com/hdims/resize/^>1010x/quality/90/src/product/image/b8bd2680-1006-4c08-9367-a625dd8be46f.jpg'),
    ('SKU-K0001', 3, 'https://product-image.kurly.com/hdims/resize/^>1010x/quality/90/src/product/image/840adbf2-d1cf-4c37-aab9-f183218516ad.jpg')
) AS m(sku_code, sequence, media_url)
JOIN product pr ON pr.sku_code = m.sku_code AND pr.type = 'GROUP';

-- ============================================================
-- 7. product_category - STANDARD 리프 매핑 (GROUP 기준, is_main = true, 56건)
--    parent_name + leaf_name 두 값으로 특정한다(seed_product_service_sample_products.sql 과
--    동일한 이유 — 같은 리프 이름이 여러 상위 카테고리에 존재할 수 있어서).
-- ============================================================
INSERT INTO product_category (product_id, category_id, is_main)
SELECT pr.id, leaf.id, true
FROM (VALUES
    ('SKU-K0001', '유제품', '우유·두유'),
    ('SKU-K0002', '국·반찬·메인요리', '국·탕·찌개'),
    ('SKU-K0003', '과일·견과·쌀', '수입과일'),
    ('SKU-K0004', '간식·과자·떡', '떡·한과'),
    ('SKU-K0005', '정육·가공육·달걀', '국내산 소고기'),
    ('SKU-K0006', '간식·과자·떡', '초콜릿·젤리·캔디'),
    ('SKU-K0007', '채소', '브로콜리·파프리카·양배추'),
    ('SKU-K0008', '베이커리', '잼·스프레드'),
    ('SKU-K0009', '국·반찬·메인요리', '메인요리'),
    ('SKU-K0010', '국·반찬·메인요리', '국·탕·찌개'),
    ('SKU-K0011', '면·양념·오일', '햄·통조림·병조림'),
    ('SKU-K0012', '국·반찬·메인요리', '두부·어묵·부침개'),
    ('SKU-K0013', '간편식·밀키트·샐러드', '떡볶이·튀김·순대'),
    ('SKU-K0014', '베이커리', '식빵·모닝빵·베이글'),
    ('SKU-K0015', '수산·해산·건어물', '회·탕류'),
    ('SKU-K0016', '과일·견과·쌀', '제철과일'),
    ('SKU-K0017', '과일·견과·쌀', '수입과일'),
    ('SKU-K0018', '유제품', '요거트·생크림'),
    ('SKU-K0019', '수산·해산·건어물', '해산물·전복·조개류'),
    ('SKU-K0020', '유제품', '우유·두유'),
    ('SKU-K0021', '유제품', '우유·두유'),
    ('SKU-K0022', '유제품', '우유·두유'),
    ('SKU-K0023', '유제품', '우유·두유'),
    ('SKU-K0024', '유제품', '우유·두유'),
    ('SKU-K0025', '유제품', '우유·두유'),
    ('SKU-K0026', '유제품', '우유·두유'),
    ('SKU-K0027', '유제품', '우유·두유'),
    ('SKU-K0028', '유제품', '우유·두유'),
    ('SKU-K0029', '유제품', '우유·두유'),
    ('SKU-K0030', '유제품', '가공치즈'),
    ('SKU-K0031', '정육·가공육·달걀', '달걀·가공란'),
    ('SKU-K0032', '과일·견과·쌀', '수입과일'),
    ('SKU-K0033', '채소', '오이·호박·고추'),
    ('SKU-K0034', '채소', '오이·호박·고추'),
    ('SKU-K0035', '채소', '양파·대파·마늘·배추'),
    ('SKU-K0036', '면·양념·오일', '파스타·면류·조리용 떡'),
    ('SKU-K0037', '수산·해산·건어물', '젓갈·장류'),
    ('SKU-K0038', '간편식·밀키트·샐러드', '도시락·밥류'),
    ('SKU-K0039', '면·양념·오일', '식초·소스·드레싱'),
    ('SKU-K0040', '채소', '냉동·이색·간편채소'),
    ('SKU-K0041', '면·양념·오일', '햄·통조림·병조림'),
    ('SKU-K0042', '간편식·밀키트·샐러드', '치킨·피자·핫도그·만두'),
    ('SKU-K0043', '베이커리', '식빵·모닝빵·베이글'),
    ('SKU-K0044', '정육·가공육·달걀', '닭·오리고기'),
    ('SKU-K0045', '정육·가공육·달걀', '닭·오리고기'),
    ('SKU-K0046', '과일·견과·쌀', '제철과일'),
    ('SKU-K0047', '국·반찬·메인요리', '국·탕·찌개'),
    ('SKU-K0048', '채소', '오이·호박·고추'),
    ('SKU-K0049', '면·양념·오일', '식용유·참기름·오일'),
    ('SKU-K0050', '채소', '친환경'),
    ('SKU-K0051', '수산·해산·건어물', '연어·참치'),
    ('SKU-K0052', '채소', '친환경'),
    ('SKU-K0053', '채소', '양파·대파·마늘·배추'),
    ('SKU-K0054', '정육·가공육·달걀', '돈까스·떡갈비·함박'),
    ('SKU-K0055', '유제품', '가공치즈'),
    ('SKU-K0056', '면·양념·오일', '소금·설탕·향신료')
) AS m(sku_code, parent_name, leaf_name)
JOIN product pr ON pr.sku_code = m.sku_code AND pr.type = 'GROUP'
JOIN category parent ON parent.name = m.parent_name AND parent.parent_id IS NULL AND parent.type = 'STANDARD'
JOIN category leaf ON leaf.name = m.leaf_name AND leaf.parent_id = parent.id AND leaf.type = 'STANDARD';

-- ============================================================
-- 8. product_category - DISPLAY 매핑 (GROUP 기준, is_main = false, 0~2개)
-- ============================================================
INSERT INTO product_category (product_id, category_id, is_main)
SELECT pr.id, c.id, false
FROM (VALUES
    ('SKU-K0001', '많이 보는'), ('SKU-K0001', '베스트 랭킹'),
    ('SKU-K0002', '간편식'),
    ('SKU-K0003', '과일'),
    ('SKU-K0004', '간식'),
    ('SKU-K0005', '정육/달걀'), ('SKU-K0005', '세일'),
    ('SKU-K0006', '간식'),
    ('SKU-K0007', '채소/쌀'), ('SKU-K0007', '단독의 발견'),
    ('SKU-K0008', '베이커리'),
    ('SKU-K0009', '간편식'),
    ('SKU-K0010', '간편식'),
    ('SKU-K0012', '단독의 발견'),
    ('SKU-K0013', '간편식'),
    ('SKU-K0014', '베이커리'),
    ('SKU-K0015', '수산'),
    ('SKU-K0016', '과일'),
    ('SKU-K0017', '과일'), ('SKU-K0017', '단독의 발견'),
    ('SKU-K0018', '맞춤추천'),
    ('SKU-K0019', '수산'),
    ('SKU-K0020', '많이 보는'),
    ('SKU-K0021', '베스트 랭킹'),
    ('SKU-K0026', '멤버스'),
    ('SKU-K0030', '멤버스'),
    ('SKU-K0031', '정육/달걀'), ('SKU-K0031', '단독의 발견'),
    ('SKU-K0033', '채소/쌀'), ('SKU-K0033', '단독의 발견'),
    ('SKU-K0034', '채소/쌀'), ('SKU-K0034', '단독의 발견'),
    ('SKU-K0035', '채소/쌀'), ('SKU-K0035', '단독의 발견'),
    ('SKU-K0036', '맞춤추천'),
    ('SKU-K0037', '수산'), ('SKU-K0037', '세일'),
    ('SKU-K0038', '간편식'),
    ('SKU-K0040', '채소/쌀'),
    ('SKU-K0042', '간편식'),
    ('SKU-K0043', '베이커리'),
    ('SKU-K0044', '정육/달걀'),
    ('SKU-K0045', '정육/달걀'),
    ('SKU-K0046', '과일'), ('SKU-K0046', '베스트 랭킹'),
    ('SKU-K0047', '간편식'),
    ('SKU-K0048', '채소/쌀'), ('SKU-K0048', '단독의 발견'),
    ('SKU-K0049', '맞춤추천'),
    ('SKU-K0050', '채소/쌀'),
    ('SKU-K0051', '수산'), ('SKU-K0051', '단독의 발견'),
    ('SKU-K0052', '채소/쌀'),
    ('SKU-K0053', '채소/쌀'),
    ('SKU-K0054', '정육/달걀'),
    ('SKU-K0055', '베스트 랭킹'),
    ('SKU-K0056', '채소/쌀')
) AS m(sku_code, category_name)
JOIN product pr ON pr.sku_code = m.sku_code AND pr.type = 'GROUP'
JOIN category c ON c.name = m.category_name AND c.type = 'DISPLAY';

-- ============================================================
-- 9. product_spec - UNIT 상품 전부 (product_spec 은 UNIT 에만 매핑)
--    storage_type/packaging_type 근사 규칙은 seed_product_service_sample_products.sql 과 동일:
--      트레이 → FOAM, 박스·포대 → CARDBOARD, 그 외 → PLASTIC.
-- ============================================================
INSERT INTO product_spec (product_id, storage_type, packaging_type, attributes)
SELECT pr.id, s.storage_type, s.packaging_type, s.attributes
FROM (VALUES
    ('SKU-K0001-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "900mL", "포장": "팩"}'),
    ('SKU-K0002-1', 'REFRIGERATED',     'PLASTIC',   '{"조리방법": "냄비 조리", "중량": "2인분", "포장": "파우치"}'),
    ('SKU-K0003-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "필리핀산", "중량": "1.2kg 내외", "포장": "봉지"}'),
    ('SKU-K0004-1', 'ROOM_TEMPERATURE', 'CARDBOARD', '{"원산지": "국내산(쌀)", "중량": "30g x 10", "포장": "박스"}'),
    ('SKU-K0005-1', 'FROZEN',           'CARDBOARD', '{"원산지": "국내산(한우)", "등급": "1+", "포장": "박스"}'),
    ('SKU-K0006-1', 'ROOM_TEMPERATURE', 'CARDBOARD', '{"중량": "36g x 18", "포장": "박스"}'),
    ('SKU-K0007-1', 'REFRIGERATED',     'FOAM',      '{"원산지": "국내산", "중량": "250g", "포장": "트레이"}'),
    ('SKU-K0008-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "140g", "포장": "병"}'),
    ('SKU-K0009-1', 'FROZEN',           'PLASTIC',   '{"조리방법": "냄비 조리", "중량": "2인분", "포장": "파우치"}'),
    ('SKU-K0010-1', 'REFRIGERATED',     'PLASTIC',   '{"조리방법": "냄비 조리", "중량": "2인분", "포장": "파우치"}'),
    ('SKU-K0011-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "150g", "포장": "캔"}'),
    ('SKU-K0012-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "300g", "포장": "팩"}'),
    ('SKU-K0013-1', 'REFRIGERATED',     'PLASTIC',   '{"조리방법": "냄비 조리", "중량": "2~3인분", "포장": "팩"}'),
    ('SKU-K0014-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "420g", "포장": "봉지"}'),
    ('SKU-K0015-1', 'REFRIGERATED',     'FOAM',      '{"원산지": "국내산(양식)", "중량": "200g", "포장": "트레이"}'),
    ('SKU-K0016-1', 'ROOM_TEMPERATURE', 'CARDBOARD', '{"원산지": "국내산", "중량": "1.2kg", "포장": "박스"}'),
    ('SKU-K0017-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "미국산", "당도": "16brix 이상", "중량": "500g", "포장": "팩"}'),
    ('SKU-K0018-1', 'REFRIGERATED',     'PLASTIC',   '{"중량": "400g", "포장": "용기"}'),
    ('SKU-K0019-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "1kg", "포장": "팩"}'),
    ('SKU-K0020-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "1.8L", "포장": "팩"}'),
    ('SKU-K0021-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "1000mL", "포장": "팩"}'),
    ('SKU-K0022-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "750mL", "포장": "팩"}'),
    ('SKU-K0023-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "710mL", "포장": "팩"}'),
    ('SKU-K0024-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "2.3L", "포장": "팩"}'),
    ('SKU-K0025-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "900mL", "포장": "팩"}'),
    ('SKU-K0026-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "750mL", "포장": "팩"}'),
    ('SKU-K0027-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "900mL", "포장": "팩"}'),
    ('SKU-K0028-1', 'REFRIGERATED',     'PLASTIC',   '{"용량": "930mL", "포장": "팩"}'),
    ('SKU-K0029-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"용량": "950mL", "포장": "팩"}'),
    ('SKU-K0030-1', 'REFRIGERATED',     'PLASTIC',   '{"중량": "1kg 내외", "포장": "팩"}'),
    ('SKU-K0031-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "20구", "포장": "팩"}'),
    ('SKU-K0032-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "페루산", "중량": "3입", "포장": "팩"}'),
    ('SKU-K0033-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "2입", "포장": "팩"}'),
    ('SKU-K0034-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "1개", "포장": "팩"}'),
    ('SKU-K0035-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "200g", "포장": "팩"}'),
    ('SKU-K0036-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "이탈리아산", "중량": "500g", "포장": "봉지"}'),
    ('SKU-K0037-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "1kg 내외", "포장": "팩"}'),
    ('SKU-K0038-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "130g x 3", "포장": "용기"}'),
    ('SKU-K0039-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "미국산", "용량": "60mL", "포장": "병"}'),
    ('SKU-K0040-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "500g", "포장": "팩"}'),
    ('SKU-K0041-1', 'ROOM_TEMPERATURE', 'CARDBOARD', '{"중량": "85g x 8", "포장": "박스"}'),
    ('SKU-K0042-1', 'FROZEN',           'PLASTIC',   '{"중량": "700g", "포장": "봉지"}'),
    ('SKU-K0043-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "300g", "포장": "봉지"}'),
    ('SKU-K0044-1', 'FROZEN',           'PLASTIC',   '{"중량": "350g", "포장": "봉지"}'),
    ('SKU-K0045-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "300g", "포장": "팩"}'),
    ('SKU-K0046-1', 'ROOM_TEMPERATURE', 'CARDBOARD', '{"원산지": "국내산(상주)", "당도": "16brix 이상", "중량": "1.5kg", "포장": "박스"}'),
    ('SKU-K0047-1', 'REFRIGERATED',     'PLASTIC',   '{"조리방법": "냄비 조리", "중량": "700g", "포장": "파우치"}'),
    ('SKU-K0048-1', 'REFRIGERATED',     'FOAM',      '{"원산지": "국내산", "중량": "750g", "포장": "트레이"}'),
    ('SKU-K0049-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"원산지": "이탈리아산", "용량": "500mL", "포장": "병"}'),
    ('SKU-K0050-1', 'REFRIGERATED',     'PLASTIC',   '{"중량": "10g", "포장": "팩"}'),
    ('SKU-K0051-1', 'REFRIGERATED',     'FOAM',      '{"원산지": "노르웨이산", "중량": "200g", "포장": "트레이"}'),
    ('SKU-K0052-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "1.5kg", "포장": "망포장"}'),
    ('SKU-K0053-1', 'REFRIGERATED',     'PLASTIC',   '{"원산지": "국내산", "중량": "100g", "포장": "팩"}'),
    ('SKU-K0054-1', 'REFRIGERATED',     'PLASTIC',   '{"중량": "345g", "포장": "팩"}'),
    ('SKU-K0055-1', 'REFRIGERATED',     'PLASTIC',   '{"중량": "15매", "포장": "팩"}'),
    ('SKU-K0056-1', 'ROOM_TEMPERATURE', 'PLASTIC',   '{"중량": "12g", "포장": "병"}')
) AS s(sku_code, storage_type, packaging_type, attributes)
JOIN product pr ON pr.sku_code = s.sku_code AND pr.type = 'UNIT';

-- ============================================================
-- 10. product_inventory - UNIT 상품 전부
--    seed_product_service_sample_products.sql 과 같은 산식: base 50~499, reserved 0~11.
-- ============================================================
INSERT INTO product_inventory (product_id, base_quantity, reserved_quantity, updated_at)
SELECT p.id,
       50 + (p.id * 17) % 450,
       (p.id * 7) % 12,
       NOW()
FROM product p
WHERE p.type = 'UNIT' AND p.sku_code LIKE 'SKU-K%-1';

COMMIT;
