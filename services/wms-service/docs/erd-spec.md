# WMS 서비스 ERD 명세

창고 관리 시스템(WMS)의 데이터 모델 정의서입니다. 입고(Inbound) → 적치/보충(StockMovement) → 재고 현행화(Inventory) → 출고(Outbound)의 물류 흐름을 LOT/유통기한(FEFO) 기준으로 관리합니다.

- DBMS: PostgreSQL (로컬 포트 `5437`, service number `5`)
- 이벤트 발행: Transactional Outbox → RabbitMQ (이 저장소의 다른 서비스들과 동일하게 Kafka가 아니라 RabbitMQ만 쓴다 — `common` 모듈이 `spring-boot-starter-amqp`를 제공)
- 재고 정합성: 낱개(EA) 단위로 현행화, 현장 작업은 파레트/박스 단위로 수행 후 환산

## 설계 원칙

- **상품 도메인 결합도 최소화**: 상품 도메인의 Product를 직접 참조하지 않고 `WmsProduct` 스냅샷을 둔다. `WmsProduct.id`는 상품 서비스의 Product UNIT ID와 동일한 값을 사용한다.
- **단위 환산**: 현장은 `PALLET`/`BOX(CARTON)` 단위로 검수·이동하고, 재고(`Inventory`)에는 항상 낱개(EA)로 반영한다. 환산 계수는 `WmsProduct.box_unit_qty`, `WmsProduct.pallet_box_qty`를 사용한다.
- **FEFO(First Expired, First Out)**: 출고 할당 및 재고 이동 시 `expired_date ASC` 기준으로 LOT을 선택한다. LOT 식별은 `(product_id, lot_no, expired_date, lpn_code)` 조합으로 한다.
- **로케이션 계층**: 버퍼(임시 도크) -> 보관존(파레트 랙) → 피킹존(선반 빈)  를 하나의 `Location` 테이블에서 `location_type`으로 구분한다.

## ER 다이어그램

```mermaid
erDiagram
    WAREHOUSE   ||--o{ LOCATION        : "has"
    WAREHOUSE   ||--o{ INBOUND_ORDER   : "receives"
    WAREHOUSE   ||--o{ OUTBOUND_ORDER  : "ships"
    WAREHOUSE   ||--o{ INVENTORY       : "holds"
    WAREHOUSE   ||--o{ STOCK_MOVEMENT  : "operates"

    WMS_PRODUCT ||--o{ INBOUND_ITEM    : "referenced by"
    WMS_PRODUCT ||--o{ OUTBOUND_ITEM   : "referenced by"
    WMS_PRODUCT ||--o{ INVENTORY       : "referenced by"
    WMS_PRODUCT ||--o{ STOCK_MOVEMENT  : "referenced by"

    INBOUND_ORDER  ||--o{ INBOUND_ITEM  : "contains"
    OUTBOUND_ORDER ||--o{ OUTBOUND_ITEM : "contains"

    LOCATION ||--o{ INVENTORY      : "stores"
    LOCATION ||--o{ INBOUND_ITEM   : "target of"
    LOCATION ||--o{ OUTBOUND_ITEM  : "picked from"
    LOCATION ||--o{ STOCK_MOVEMENT : "from / to"

    WAREHOUSE      ||--o{ TASK : "assigns"
    OUTBOUND_ORDER ||--o{ TASK : "generates"
    OUTBOUND_ITEM  ||--|| TASK : "picked via"

    WAREHOUSE {
        Long    id PK
        String  code UK
        String  name
        String  address
        Boolean is_active
        Enum[]  regions
    }
    WMS_PRODUCT {
        Long    id PK
        String  sku_code
        String  barcode
        String  name
        Enum    storage_type
        Integer box_unit_qty
        Integer pallet_box_qty
        Integer safety_stock
    }
    LOCATION {
        Long    id PK
        Long    warehouse_id FK
        Enum    location_type
        Enum    storage_type
        Enum    zone
        String  aisle
        String  rack
        Integer level
        String  bin
        Enum    status
    }
    INBOUND_ORDER {
        Long     id PK
        Long     warehouse_id FK
        String   po_number
        String   supplier_name
        Enum     status
        DateTime expected_date
        DateTime completed_date
    }
    INBOUND_ITEM {
        Long    id PK
        Long    inbound_order_id FK
        Long    product_id FK
        Enum    inbound_unit
        Integer ordered_quantity
        Integer inspect_quantity
        Integer total_base_quantity
        String  lpn_code
        String  lot_no
        Date    expired_date
        Long    target_location_id FK
        Enum    status
        Long    version
    }
    INVENTORY {
        Long     id PK
        Long     warehouse_id FK
        Long     location_id FK
        Long     product_id FK
        String   lpn_code
        String   lot_no
        Date     expired_date
        Integer  quantity
        Integer  reserved_quantity
        DateTime updated_at
    }
    STOCK_MOVEMENT {
        Long     id PK
        Long     warehouse_id FK
        Long     product_id FK
        Long     inbound_item_id FK
        String   lpn_code
        String   lot_no
        Date     expired_date
        Long     from_location_id FK
        Long     to_location_id FK
        Enum     movement_unit
        Integer  unit_quantity
        Integer  quantity
        Enum     movement_type
        Enum     status
        DateTime created_at
        DateTime completed_at
    }
    OUTBOUND_ORDER {
        Long     id PK
        Long     order_id
        Long     warehouse_id FK
        Enum     status
        DateTime created_at
    }
    OUTBOUND_ITEM {
        Long    id PK
        Long    outbound_order_id FK
        Long    product_id FK
        String  lpn_code
        String  lot_no
        Date    expired_date
        Long    location_id FK
        Integer ordered_quantity
        Integer picked_quantity
        Enum    status
    }
    WMS_OUTBOX {
        Long     id PK
        String   event_id UK
        String   exchange
        String   routing_key
        String   type_id
        Text     payload
        Enum     status
        DateTime created_at
        DateTime published_at
    }
    TASK {
        Long     id PK
        String   task_no UK
        Enum     status
        Long     warehouse_id FK
        Long     outbound_order_id FK
        Long     outbound_item_id FK_UK
        Long     worker_id
        DateTime started_at
        DateTime completed_at
        DateTime created_at
        DateTime updated_at
    }
```

---

## 1. WmsProduct — WMS용 상품 스냅샷

상품 도메인과 결합도를 낮추고, 입고 검수 및 재고 관리에 필요한 물리적 규격(바코드, 박스/파레트 단위)을 관리하기 위한 WMS 전용 마스터.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 상품 ID (상품 도메인 Product UNIT ID와 동일한 값) |
| `sku_code` | String(50) | UK | 고유 상품 코드 |
| `barcode` | String(50) | | 입고 검수 시 스캔용 바코드 |
| `name` | String | | 상품명 |
| `storage_type` | Enum | | 보관 유형 (`REFRIGERATED` 냉장 / `FROZEN` 냉동 / `ROOM_TEMPERATURE` 상온) |
| `box_unit_qty` | Integer | | 박스당 낱개 수 (예: 10) |
| `pallet_box_qty` | Integer | | 파레트당 박스 수 (예: 40) |
| `safety_stock` | Integer | | 안전 재고 (낱개 기준) |

**포장 단위 계층**

- 일반: `파레트 → 박스 → 낱개`, 파레트당 낱개 = `pallet_box_qty × box_unit_qty`
- 박스 없이 파레트에 직접 적재되는 상품(예: 쌀): `box_unit_qty = 1`로 설정하여 환산식을 일관되게 유지한다. (`pallet_box_qty`가 파레트당 낱개 수 역할)

---

## 2. Warehouse — 물류 센터

물류 센터 기본 정보 관리.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 물류 센터 식별자 |
| `code` | String(50) | UK | 센터 코드 (예: `ICN_01`) |
| `name` | String | | 센터 이름 (예: 김포 물류센터) |
| `address` | String | | 주소 |
| `is_active` | Boolean | | 운영 여부 |

**담당 권역(regions)**: OMS가 배송지 권역별로 창고를 조회/배정할 수 있도록 `warehouse.regions`(PostgreSQL 배열 컬럼 `VARCHAR(20)[]`, `V10__add_warehouse_region.sql`)로 관리한다. 별도 테이블이 아니라 배열 컬럼인 이유는 **한 창고가 여러 권역을 동시에 담당할 수 있어서**다(예: 김포물류센터가 경기서부+수도권을 같이 커버) — PostgreSQL/Hibernate가 네이티브 배열 타입을 지원해 별도 조인 테이블 없이도 표현 가능하다. 원소는 광역자치단체(시/도) 17개를 그대로 쓰지 않고 물류 배정에 의미 있는 단위로 묶은 8개 고정값(`com.kurly.wms.domain.enums.Region`) — 경기도는 이 회사 물류센터가 여러 곳 몰려 있어(김포/평택/안산) 동/서로 쪼갰고, 나머지는 배송 권역 단위로 크게 묶었다(수도권/충청권/강원권/영남권/호남권/제주). `com.kurly.wms.infrastructure.entity.Warehouse.regions: List<Region>`을 `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Enumerated(EnumType.STRING)`으로 매핑하고, 배열 안의 모든 값이 8개 중 하나인지는 `chk_warehouse_regions` CHECK 제약(`<@` 연산자로 부분집합 검증)이 DB 레벨에서 강제한다. 아직 배정 안 된 창고는 빈 배열일 수 있다.

`GET /internal/v1/wms/warehouses?region=&isActive=`로 그 권역을 담당 목록에 포함하는 창고를 전부 조회한다(반대로 여러 창고가 같은 권역을 공유할 수도 있음 — 예: 컬리나우 4곳 전부 SEOUL_METRO). `WarehouseInternalController`→`WarehouseQueryService`→`WarehouseJpaRepository.search()`로 구현돼 있다. Hibernate HQL의 `array_contains(array, element)` 함수로 먼저 시도했다가 enum 파라미터를 `bytea[]`로 잘못 바인딩하는 문제(`character varying[] @> bytea[]` 에러)를 만나 네이티브 쿼리 + PostgreSQL `= ANY(...)` 연산자로 우회했다 — `region`은 Java enum이 아니라 문자열(enum name)로 바인딩한다.

---

## 3. Location — 로케이션 계층 구조

파레트 보관 구역(`PALLET_RACK`), 소분/선반 피킹 구역(`SHELF_BIN`), 임시 도크/버퍼(`BUFFER`)를 계층적으로 관리.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 로케이션 식별자 |
| `warehouse_id` | Long | FK → Warehouse | 물류 센터 ID |
| `location_type` | Enum | | 로케이션 유형 (`PALLET_RACK` / `SHELF_BIN` / `BUFFER`) |
| `storage_type` | Enum | | 보관 유형 (`REFRIGERATED` / `FROZEN` / `ROOM_TEMPERATURE`) |
| `zone` | Enum | | 기능적 구역 (`BUFFER` / `PICKING` / `STORAGE`). `location_type`과 지금은 1:1로 겹치지만(STORAGE=PALLET_RACK, PICKING=SHELF_BIN, BUFFER=BUFFER), 한 구역에 여러 location_type이 섞이는 시점부터 별도 컬럼의 의미가 생겨 지금부터 분리해 둠 |
| `aisle` | String(20) | | 통로 번호 (기존 `rack` 보완). 예: `A01`(A01 열), `B02`(B02 열) |
| `rack` | String(20) | | 베이 번호 (기둥 사이 연 번호). 예: `03`(3번째 기둥 구역), `01`(1번째 기둥 구역) |
| `level` | Integer | | 층수 (파레트 랙 단수 관리용) |
| `bin` | String(20) | | 보관존(파레트): `01`, `02` / 피킹존(바구니): `F01`~`F09` (선반 내 3×3 격자 바구니) |
| `status` | Enum | | 상태 (`ACTIVE` 사용중 / `LOCKED` 점검·실사중). 점유 여부는 이 상태로 표현하지 않음 |

**주소 체계 유니크 제약**: `(warehouse_id, aisle, rack, level, bin)` 조합 UK.

**미결 사항**

- 파레트 단위와 선반 단위의 주소 체계를 명확히 확정해야 함.
- 하나의 랙에 여러 선반이 존재할 수 있음 → 선반 로케이션 세분화 필요.
- 원래 파레트 1개 자리에 선반이 여러 개 들어가는 구조인지 확인 필요.

---

## 4. InboundOrder — 입고 전표 (SCM 연계)

SCM 서비스로부터 전달받는 발주 및 입고 예정 정보.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 입고 전표 식별자 |
| `warehouse_id` | Long | FK → Warehouse | 입고될 물류 센터 ID |
| `po_number` | String(50) | NOT NULL | SCM 발주 번호 (PO 단위 조회·추적용, `V2` 마이그레이션에서 추가) |
| `supplier_name` | String | | 공급사/벤더명 (SCM 연계) |
| `status` | Enum | | 전표 상태 (`EXPECTED` 입고예정 / `INSPECTING` 검수중 / `COMPLETED` 입고완료 / `CANCELED` 취소) |
| `expected_date` | DateTime | | 입고 예정 일시 |
| `completed_date` | DateTime | | 실제 입고 완료 일시 |

**미결 사항**: 임시 도크/버퍼(`BUFFER`)에 하차만 된 상태를 별도 상태값으로 추가할지 검토.

---

## 5. InboundItem — 입고 상세 품목 (단위 환산 및 LOT 포함)

현장 작업 단위(파레트/박스)로 검수하고, 최종 낱개(EA)로 환산하여 LOT/유통기한과 함께 기록하는 상세 내역.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 입고 상세 식별자 |
| `inbound_order_id` | Long | FK → InboundOrder | 입고 전표 ID |
| `product_id` | Long | FK → WmsProduct | 상품 ID |
| `inbound_unit` | Enum | | 입고 작업 단위 (`PALLET` / `CARTON` / `BOX`) |
| `ordered_quantity` | Integer | | 발주/예정 수량 (입고 단위 기준) |
| `inspect_quantity` | Integer | | 실제 검수 합격 수량 (입고 단위 기준) |
| `total_base_quantity` | Integer | | 재고 반영용 최종 낱개(EA) 수량. `inspect_quantity × (WmsProduct 환산 계수)` |
| `lpn_code` | String(50) | | 물류용 바코드 (License Plate Number) |
| `lot_no` | String(50) | | LOT 번호 |
| `expired_date` | Date | | 유통기한 (FEFO의 핵심 기준) |
| `target_location_id` | Long | FK → Location | 적치할 목표 로케이션 ID |
| `status` | Enum | | 상세 상태 (`PENDING` / `INSPECTED` 검수완료 / `PUT_AWAY` 적치완료) |
| `version` | Long | NOT NULL, DEFAULT 0 (V6) | JPA `@Version` 낙관적 락. 검수/적치처럼 같은 행을 읽어 상태를 전이시키는 흐름에서 동시 요청(중복 클릭, 재시도)이 서로 덮어쓰는 걸 막는다 |

> `unit_per_quantity`(단위당 낱개 수) 컬럼은 제거. 환산 계수는 `WmsProduct`에서 조회한다.
> - `inbound_unit = PALLET` → `× pallet_box_qty × box_unit_qty`
> - `inbound_unit = CARTON`/`BOX` → `× box_unit_qty`

---

## 6. Inventory — 실시간 재고 현행화 (LOT 및 FEFO 지원)

특정 창고·로케이션·상품, 그리고 유통기한(LOT)별로 분리된 실시간 가용/예약 재고 상태.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 재고 식별자 |
| `warehouse_id` | Long | FK → Warehouse | 물류 센터 ID |
| `location_id` | Long | FK → Location | 로케이션 ID |
| `product_id` | Long | FK → WmsProduct | 상품 ID |
| `lpn_code` | String(50) | | 물류용 바코드 |
| `lot_no` | String(50) | | LOT 번호 |
| `expired_date` | Date | | 유통기한 (FEFO 정렬/조회 기준) |
| `quantity` | Integer | `CHECK (quantity >= 0)` | 가용 재고 수량 (낱개 기준) |
| `reserved_quantity` | Integer | `CHECK (reserved_quantity >= 0)` | 주문 선점 예약 수량 |
| `updated_at` | DateTime | | 최종 재고 변동 일시 |

**UK 제약**: `(warehouse_id, location_id, product_id, lot_no, expired_date, lpn_code)`, `NULLS NOT DISTINCT`(V7) — 표준 유니크 제약은 `lpn_code`가 NULL인 행끼리 서로 다른 값으로 취급해 유일성이 보장되지 않았다(LPN 없이 관리하는 게 기본값이라 실질적으로 이 조합의 유일성이 전혀 없었던 셈). 동시에 같은 조합을 "없음"으로 보고 각자 새 행을 만드는 find-or-create 경합의 마지막 방어선이다.

**동시성**: `receiveIntoBuffer`/`moveInventory`가 이 조합으로 조회할 때 `PESSIMISTIC_WRITE`로 잠가 lost update를 막는다. `remove()`는 재고보다 큰 수량을 빼려 하면 `BusinessException(INSUFFICIENT_INVENTORY)`으로 막는다(DB의 `chk_inventory_quantity`가 마지막 방어선).

**조회 시**: 상품 전체 가용 재고 = `SUM(quantity - reserved_quantity)` (해당 상품의 전 로케이션 합산).

---

## 7. StockMovement — 재고 이동 및 물류 작업 지시

입고 후 적치(`PUT_AWAY`), 피킹존 재고 보충(`REPLENISHMENT`), 단순 로케이션 이동(`RELOCATION`) 등 창고 내 모든 물리적 이동을 관리하는 작업 지시 테이블.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 이동/작업 지시 식별자 |
| `warehouse_id` | Long | FK → Warehouse | 물류 센터 ID |
| `product_id` | Long | FK → WmsProduct | 상품 ID |
| `inbound_item_id` | Long | FK → InboundItem, NULL 허용 (V4) | PUT_AWAY가 입고 검수 건에서 비롯된 경우에만 채워짐. put-away/confirm이 "이 검수 건의 대기 중인 작업 지시"를 로케이션 매칭 없이 직접 찾기 위한 참조 — 작업자가 추천과 다른 로케이션에 실제로 적치할 수 있어 location 기준 매칭은 신뢰할 수 없다. REPLENISHMENT/RELOCATION은 NULL |
| `lpn_code` | String(50) | | 물류용 바코드 |
| `lot_no` | String(50) | | 대상 LOT 번호 (FEFO 고려) |
| `expired_date` | Date | | 유통기한 (FEFO 정렬/조회 기준) |
| `from_location_id` | Long | FK → Location | 출발지 로케이션 (예: 임시 버퍼, 파레트 랙) |
| `to_location_id` | Long | FK → Location | 목적지 로케이션 (예: 파레트 랙, 피킹 선반) |
| `movement_unit` | Enum | | 현장 작업 단위 (`PALLET` / `BOX` / `EA`) |
| `unit_quantity` | Integer | | 작업 단위 기준 수량 (예: `2` = 2 파레트) |
| `quantity` | Integer | | 이동할 낱개(EA) 수량 |
| `movement_type` | Enum | | 이동 유형 (`PUT_AWAY` 입고적치 / `REPLENISHMENT` 피킹존보충 / `RELOCATION` 단순이동) |
| `status` | Enum | | 작업 상태 (`PENDING` 지시생성 / `IN_PROGRESS` 작업중 / `COMPLETED` 완료 / `CANCELED` 취소) |
| `created_at` | DateTime | | 작업 지시 생성 일시 |
| `completed_at` | DateTime | | 작업 완료 일시 |

**동시성**: put-away/confirm과 put-away/recommendation이 stockMovementId로 이 행을 조회할 때 `PESSIMISTIC_WRITE`로 잠근다 — 같은 작업 지시를 동시에 완료/재배정하려는 요청은 먼저 락을 쥔 쪽이 끝날 때까지 대기했다가, 이미 바뀐 status를 보고 재고 이동 전에 즉시 실패한다.

---

## 8. OutboundOrder — 출고 전표 (OMS 연계)

OMS(주문 관리 시스템)로부터 전달받은 출고 지시 전표.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 출고 전표 식별자 |
| `order_id` | Long | | 프론트오피스 주문 ID (OMS/주문 도메인 연계) |
| `warehouse_id` | Long | FK → Warehouse | 출고 담당 물류 센터 ID |
| `status` | Enum | | 전표 상태. 하위 OutboundItem들의 상태로부터 도출된다 — 아래 "상태 전이" 참고 |
| `created_at` | DateTime | | 출고 지시 생성 일시 |

**상태 전이**

| 상태값 | 의미 | 전이 조건 |
| --- | --- | --- |
| `PENDING_REPLENISHMENT` | 할당 진행 중 | 포함된 OutboundItem 중 하나라도 `ALLOCATED`가 아닌 경우(`PENDING_REPLENISHMENT` 또는 `UNALLOCATED`) |
| `ALLOCATED` | 출고 지시 가능 (할당 완료) | 포함된 모든 OutboundItem이 `ALLOCATED` 상태로 완료된 경우 |
| `PICKING` | 피킹 작업 중 | 현장에서 피킹 지시서 발행 및 피킹 시작 시 |
| `PACKING` | 포장/검수 중 | 피킹 완료 후 패킹 존으로 이동 시 |
| `COMPLETED` | 출고 완료 | 송장 부착 및 상차 완료 시 |
| `CANCELED` | 주문 취소 | 취소 처리 시 |
| `FAILED` | 재고 확보 실패 | 품목 중 하나라도 `UNALLOCATED`으로 `wms.outbound.allocation-timeout`(기본 2시간) 이상 머무르면 `OutboundOrderFailureScheduler`가 전표 전체를 실패 처리. 품목 하나가 실패해도 전표 전체를 실패시킨다(부분 출고는 미지원). 이때 전표의 다른 ALLOCATED 품목이 쥐고 있던 피킹존 재고 예약도 함께 해제하고, PENDING_REPLENISHMENT 품목도 같이 FAILED로 정리한다 — 아래 "실패 처리 시 재고 정리" 참고 |

**FEFO 하드 할당**: `order.inventory.confirm` 이벤트 소비 시(`OutboundOrderService.createFromOrderEvent`) 품목별로 다음 순서로 재고를 확보한다.

1. 피킹존(`Zone.PICKING`) 가용 재고를 유통기한 오름차순으로 소진해 즉시 할당(`ALLOCATED`). 한 품목이 여러 LOT/로케이션에 걸쳐 분할 할당될 수 있다(OutboundItem이 여러 행으로 나뉨).
2. 그래도 부족하면 보관존(`Zone.STORAGE`)에서 예약(`Inventory.reserve()`) + 피킹존으로의 보충 지시(StockMovement, `REPLENISHMENT`)를 트리거 — 예약이 걸린 만큼은 `PENDING_REPLENISHMENT` OutboundItem(location/lotNo/expiredDate=null)으로 남는다.
3. 보관존조차 부족하거나(전체/일부), 이 상품이 피킹존에 한 번도 배치된 적이 없어 보충 지시의 목적지를 정할 수 없으면, 그 남은 만큼은 `UNALLOCATED` OutboundItem으로 남는다 — 어떤 StockMovement도 걸려 있지 않은 상태다.

OutboundOrder 자체는 별도 필드로 계산하지 않고, 생성된 OutboundItem들의 실제 상태로부터 그대로 도출한다(단일 진실 공급원은 OutboundItem) — 하나라도 `ALLOCATED`가 아니면 전표도 `hold()`로 `PENDING_REPLENISHMENT` 전환.

**재시도/최종 할당**: `StockMovementQueryService.confirm()`이 물리 이동 완료 시 발행하는 Spring 애플리케이션 이벤트를, `OutboundReallocationListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 구독해 `OutboundOrderService`의 대응 메서드를 호출한다. 둘 다 같은 상품을 여러 전표가 동시에 기다릴 수 있어 전표 생성 시각 오름차순(FIFO)으로 처리하고, 한 이동으로 다 못 채우면 커버된 만큼/못 채운 만큼 두 행으로 쪼갠다.
- `REPLENISHMENT` 완료 → `ReplenishmentCompletedEvent(warehouseId, productId, locationId, lotNo, expiredDate, quantity)` — 이동이 실제로 어디로/어떤 LOT으로 도착했는지까지 실어서, `finalizeReplenishment()`가 다시 FEFO 조회 없이 그대로 채워 `PENDING_REPLENISHMENT` OutboundItem을 `ALLOCATED`로 완성한다. 이걸로 전표의 모든 품목이 ALLOCATED가 되면 전표도 `allocate()`로 되돌린다.
- `PUT_AWAY` 완료 → `PutAwayCompletedEvent(warehouseId, productId)` — 보관존 재고가 새로 늘었다는 신호. `retryUnallocated()`가 `UNALLOCATED` OutboundItem에 대해 `triggerReplenishment()`(보관존 예약 시도)를 다시 실행한다.

**할당 완료 신호(OutboundAllocatedEvent)**: `OutboundOrder`가 `ALLOCATED`가 되는 순간(포함된 모든 OutboundItem이 `ALLOCATED`) `OutboundOrderService`가 `OutboundAllocatedEvent(outboundOrderId)`를 발행한다 — 주문 생성 시점에 바로 전부 할당되는 경로(`createFromOrderEvent`)와, 처음엔 일부만 할당돼 대기하다가 나중에 보충이 끝나 마지막 품목까지 채워지는 경로(`finalizeReplenishment`) 둘 다에서 발행하므로 어느 쪽으로 ALLOCATED가 되든 신호는 정확히 한 번 온다. 이 이벤트를 `OutboundReallocationListener`가 구독해 품목별 피킹 `Task`를 자동 생성한다(11절 참고).

두 소비자 메서드 모두 `@Transactional(propagation = REQUIRES_NEW)`가 필요하다 — `AFTER_COMMIT` 콜백은 원래 트랜잭션이 이미 커밋된 뒤(물리 커넥션은 해제됐지만 동기화 컨텍스트는 아직 열려 있는 애매한 시점)에 실행되므로, 기본(`REQUIRED`) 전파로는 참여할 트랜잭션이 없어 잠금 조회(`PESSIMISTIC_WRITE`)가 `No active transaction`으로 실패한다(`OutboxPublishService.publishOne()`과 동일 패턴).

`OutboundOrderFailureScheduler`(`wms.outbound.failure-check-interval-ms`, 기본 1시간마다)가 `wms.outbound.allocation-timeout`(기본 2시간)을 넘겨서도 `UNALLOCATED` 품목이 남아 있는 전표를 찾아 품목/전표를 전부 `FAILED`로 전환한다. 실패 시 order-service로의 보상(compensating) 이벤트 발행은 아직 하지 않음(WMS 내부 상태만 변경) — 필요해지면 후속 구현.

**실패 처리 시 재고 정리**: 같은 전표 안에 이미 `ALLOCATED`/`PENDING_REPLENISHMENT`인 다른 품목이 있을 수 있어(예: 2개 품목 중 1개만 재고가 없는 경우), `failStuckOrders()`는 UNALLOCATED 품목만이 아니라 전표의 모든 품목을 상태와 무관하게 훑는다.
- `ALLOCATED` 품목: 피킹존 `Inventory`에서 `reservedQuantity`를 명시적으로 `release()`한다 — 안 풀어주면 실제로 출고되지 않을 재고가 영원히 예약된 채로 남아 다른 주문이 못 쓴다.
- `PENDING_REPLENISHMENT` 품목: 보관존 쪽 예약은 따로 되돌리지 않는다 — 이미 생성된 REPLENISHMENT `StockMovement`가 실제로 완료되면 `moveInventory()`가 보관존 예약을 알아서 해제·차감하고, 도착한 피킹존 재고는 `finalizeReplenishment()`가 다시 FIFO 조회할 때 이 품목이 더 이상 `PENDING_REPLENISHMENT`가 아니므로(FAILED로 바뀌었으므로) 자연스럽게 다음으로 대기 중인 다른 전표에 재배정된다. 따로 취소 로직이 필요 없다.
- 모든 품목을 상태와 무관하게 `FAILED`로 남기는 이유: 그냥 두면 `PENDING_REPLENISHMENT`로 남은 품목이 나중에 `finalizeReplenishment()`에 다시 걸려 이미 죽은 전표에 실제 재고를 배정해버릴 수 있다.

**출고 완료(실물 재고 차감)**: `POST /api/v1/wms/outbounds/complete`(`OutboundOrderService.completeShipment()`)가 마지막 단계다. `PACKING` 중인 전표만 대상이며(그 외 상태면 `409 WMS4098`), `findWithPessimisticLockById()`로 전표를 잠근 채 확인하므로 성공하면 곧장 `COMPLETED`가 돼 재호출은 그냥 409로 막힌다(별도 idempotency 키 불필요). 지금까지는 피킹 시점에 예약(`reservedQuantity`)만 걸려 있었을 뿐 `Inventory.quantity`는 그대로였는데, 이 단계에서 `PICKED` 품목마다 실제 재고를 찾아 `release()` → `remove()`로 물리적으로 차감한다(`StockMovementQueryService.moveInventory()`와 동일 순서). 이어서 `OutboxService.recordOutboundCompleted()`로 `wms.outbound.completed` 이벤트를 같은 트랜잭션에 기록한다(`wms.inbound.completed`와 동일한 아웃박스 패턴) — 주문(orderId) 단위 완료 신호이며, 품목별 상세는 `{productId, quantity}`만 담는다(주문 상세 `orderItemId` 단위 매칭은 아직 `OutboundItem`에 대응 컬럼이 없어 미지원, 9절 참고). 패킹재/보냉재 추천, TMS 송장 발급(`PackingRecommendationRequest`/`ShippingConfirmRequest`)은 이 API와 별개로 여전히 placeholder 상태다.

---

## 9. OutboundItem — 출고 상세 품목 (FEFO 재고 할당)

주문된 상품을 FEFO(`ORDER BY expired_date ASC`) 방식으로 어떤 재고(LOT/로케이션)에서 차감할지 지정하는 상세 내역. 한 주문 품목이 여러 LOT에 걸쳐 할당되거나 할당/보충예약/미확보로 나뉘면 `outbound_order_id`+`product_id`가 같은 행이 여러 개 생길 수 있다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 출고 상세 식별자 |
| `outbound_order_id` | Long | FK → OutboundOrder | 출고 전표 ID |
| `product_id` | Long | FK → WmsProduct | 상품 ID |
| `lpn_code` | String(50) | | 물류용 바코드 |
| `lot_no` | String(50) | | FEFO에 의해 할당된 대상 LOT 번호. 미할당이면 null |
| `expired_date` | Date | | 할당된 재고의 유통기한. 미할당이면 null |
| `location_id` | Long | FK → Location | 피킹할 출발지 로케이션 (주로 `SHELF_BIN`). 미할당이면 null |
| `ordered_quantity` | Integer | | 이 행에 배정된 수량 (EA) |
| `picked_quantity` | Integer | | 실제 피킹 완료 수량 (EA) |
| `status` | Enum | | 상세 상태. `location_id`가 채워지는 시점에 엔티티 생성자가 자동으로 ALLOCATED로 판단해 채운다(별도 컬럼이지만 location_id와 항상 일관됨). location이 없을 때의 세부 상태(UNALLOCATED/PENDING_REPLENISHMENT)는 호출부가 명시한다 |

**상태 전이**

| 상태값 | 의미 | 설명 |
| --- | --- | --- |
| `UNALLOCATED` | 미확보 | 피킹존은 물론 보관존에서도 예약조차 못한 상태. StockMovement가 전혀 없다. `PutAwayCompletedEvent`로 재시도 대상이며, 장시간 지속되면 `FAILED`로 전환 |
| `PENDING_REPLENISHMENT` | 보충 예약됨 | 보관존 재고 예약 + 보충 지시(StockMovement, REPLENISHMENT)까지는 생성됨. 물리 이동 완료(`ReplenishmentCompletedEvent`)만 기다리면 됨 |
| `ALLOCATED` | 할당 완료 | 피킹존 재고(location_id, lot_no) 매핑 및 reserved_quantity 증가 완료 |
| `PICKED` | 피킹 완료 | 작업자가 로케이션에서 실물 피킹을 완료함 |
| `SHORTAGE` | 결품 | 물리적으로 재고가 없어 피킹 불가 처리됨(피킹 시점에 발견되는 것으로, UNALLOCATED과는 발생 시점이 다르다) |
| `FAILED` | 재고 확보 실패 | `UNALLOCATED`으로 장시간(기본 2시간) 남아 있어 `OutboundOrderFailureScheduler`가 최종 실패 처리 |

---

## 10. WmsOutbox — Transactional Outbox 패턴

비즈니스 로직과 RabbitMQ 이벤트 발행의 원자성(Atomicity) 보장. 원래 erd-spec에는 범용
`aggregate_type`/`aggregate_id`/`event_type` 설계(Kafka 전제)로 있었으나 실제 발행자가
하나도 없었고, 이 저장소는 RabbitMQ만 쓰기 때문에 product-service가 이미 검증한
RabbitMQ 발행용 스키마(`product_outbox`)를 그대로 재사용하는 편이 낫다고 보고 교체했다
(`V5__replace_outbox_with_wms_outbox.sql`, 2026-09-16). 테이블명도 `wms_outbox`로 바뀌었다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 이벤트 식별자 |
| `event_id` | String(36) | UK | 이벤트 UUID (페이로드에도 같은 값이 실림) |
| `exchange` | String(100) | | 발행 대상 익스체인지 (예: `wms.topic.exchange`) |
| `routing_key` | String(200) | | 라우팅 키 (예: `wms.inbound.completed`) |
| `type_id` | String(300) | | 페이로드 클래스의 FQCN. RabbitMQ `__TypeId__` 헤더 값으로 그대로 실린다 |
| `payload` | Text | | 이벤트 JSON 문자열 |
| `status` | Enum | | 발행 상태 (`PENDING` / `PUBLISHED`) |
| `created_at` | DateTime | | 생성 일시 |
| `published_at` | DateTime | | 발행 완료 일시 (브로커 ACK 수신 시점) |

---

## 11. Task — 출고 피킹 작업 지시

현장 작업자에게 배정되는 피킹 전용 작업 단위. `OutboundOrder`가 `ALLOCATED`가 될 때 발행하는 `OutboundAllocatedEvent`를 `OutboundReallocationListener`가 구독해 `TaskService.createPickingTasks()`로 품목(OutboundItem) 단위로 자동 생성한다. `V11__create_task.sql`로 생성(`task` 테이블).

**Task는 피킹 전용이다 — 보관존→피킹존 보충은 다루지 않는다.** 처음엔 `task_type`(PICKING/REPLENISHMENT)으로 둘 다 다루려 했으나, 보충은 이미 `StockMovement`가 자체 상태(PENDING/IN_PROGRESS/COMPLETED/CANCELED)와 확정 API(`StockMovementQueryService.confirm()`)를 갖고 있어 Task로 한 번 더 감싸면 `StockMovement.status`와 `Task.status` 두 곳이 서로 어긋날 여지가 생긴다는 문제가 있어 제외했다. 그래서 `outbound_order_id`/`outbound_item_id`는 항상 채워지는 필수 컬럼이다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | Long | PK | 작업 지시 식별자 |
| `task_no` | String(64) | UK | 사람이 읽는 피킹 작업 번호(예: `TSK-PICK-20260924-1`). `task_no_seq`(전역 시퀀스)로 채번 — 일자별로 1부터 리셋되는 카운터는 아니다(구현하려면 별도 테이블/락이 더 필요해서 우선 단순하게 시작) |
| `status` | Enum | | 작업 상태 (`PENDING` / `IN_PROGRESS` / `COMPLETED` / `CANCELED`). api-spec의 `WmsService.Common.TaskStatus`와 1:1 대응 |
| `warehouse_id` | Long | FK → Warehouse, NOT NULL | 대상 창고 ID |
| `outbound_order_id` | Long | FK → OutboundOrder, NOT NULL | 연관 출고 전표 ID |
| `outbound_item_id` | Long | FK → OutboundItem, NOT NULL, UK | 연관 출고 상세 ID — 정확히 어느 로케이션/LOT/수량을 피킹할지는 이 품목을 보고 판단한다(Task 자체엔 location/lotNo/quantity를 중복 저장하지 않음). UNIQUE라 품목 하나에 Task가 중복 생성될 수 없다 |
| `worker_id` | Long | (FK 없음) | 현장 작업자 식별자. `Worker` 엔티티/테이블이 아직 없어 FK를 걸지 못한다 |
| `started_at` | DateTime | | 작업 시작 시각 |
| `completed_at` | DateTime | | 작업 완료 시각 |
| `created_at` / `updated_at` | DateTime | NOT NULL | 생성/수정 일시 |

**설계 결정 — Task 단위는 OutboundOrder가 아니라 OutboundItem**: 처음 제안된 스키마는 `outbound_order_id`만 있었는데, 한 주문이 FEFO로 여러 LOT/로케이션에 걸쳐 분할 할당되면(흔한 경우) Task 레코드만 봐서는 정확히 무엇을 어디서 피킹해야 하는지 알 수 없는 문제가 있었다 — api-spec에 이미 있던 `PickingTaskResponse`도 `outboundItemId` 중심으로 설계돼 있어, 확인을 거쳐 `outbound_item_id`를 추가했다. 즉 한 주문이 3개 LOT으로 분할 할당되면 Task도 3건 생긴다.

**작업자 배정/시작/완료 API**: `TaskClientController`(`/api/v1/wms/tasks`)로 구현 완료. Worker 엔티티가 아직 없어 별도 "배정" API를 두지 않고, `POST /start` 요청에 `workerId`를 실어 보내 배정과 시작을 한 번에 처리한다(`TaskService.start()`). PENDING이 아니면 `409 WMS4096`. `POST /complete`(`TaskService.complete()`)는 이번 버전엔 부분 피킹(shortage)을 지원하지 않고 항상 `orderedQuantity` 전체를 피킹한 것으로 기록한다 — IN_PROGRESS가 아니면 `409 WMS4096`, 시작한 워커와 다르면 `409 WMS4097`(`TASK_WORKER_MISMATCH`). 동시성은 `StockMovementQueryService`와 동일하게 `findWithPessimisticLockById()`로 Task 행을 잠근 뒤 상태를 확인하는 방식이라, 두 작업자가 같은 Task를 동시에 `start` 해도 한쪽만 성공한다.

**OutboundOrder 상태 롤업**: `start()`는 전표가 아직 `ALLOCATED`면 `startPicking()`을 호출해 `PICKING`으로 전이한다(전표의 첫 피킹 시작 신호). `complete()`는 해당 전표의 모든 OutboundItem이 `PICKED`가 됐는지(`existsByOutboundOrderIdAndStatusNot`) 확인해, 맞으면 `startPacking()`으로 `PACKING`까지 넘긴다 — 실제 포장 API는 아직 없어 우선 전표 상태만 앞서 넘겨둔 것이다. 두 롤업 모두 이미 같은 상태로 재호출돼도 안전(멱등)하므로 OutboundOrder에 별도 잠금을 걸지 않는다.

**재고 실물 차감은 이 단계에서 하지 않는다**: `complete()`는 Task/OutboundItem/OutboundOrder 상태만 바꾸고 `Inventory.quantity`/`reserved_quantity`는 그대로 둔다 — "진짜 quantity 차감"은 8절의 "출고 완료(실물 재고 차감)" API(`POST /api/v1/wms/outbounds/complete`, 패킹 검수 후 실제로 창고를 떠나는 시점)의 몫이다.

---

## Enum 정의 요약

| Enum | 값 | 사용 테이블 |
| --- | --- | --- |
| `StorageType` | `REFRIGERATED`, `FROZEN`, `ROOM_TEMPERATURE` | WmsProduct, Location |
| `LocationType` | `PALLET_RACK`, `SHELF_BIN`, `BUFFER` | Location |
| `LocationStatus` | `ACTIVE`, `LOCKED` | Location |
| `Zone` | `BUFFER`, `PICKING`, `STORAGE` | Location |
| `InboundOrderStatus` | `EXPECTED`, `INSPECTING`, `COMPLETED`, `CANCELED` | InboundOrder |
| `InboundUnit` | `PALLET`, `CARTON`, `BOX` | InboundItem |
| `InboundItemStatus` | `PENDING`, `INSPECTED`, `PUT_AWAY` | InboundItem |
| `MovementUnit` | `PALLET`, `BOX`, `EA` | StockMovement |
| `MovementType` | `PUT_AWAY`, `REPLENISHMENT`, `RELOCATION` | StockMovement |
| `MovementStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELED` | StockMovement |
| `OutboundOrderStatus` | `PENDING_REPLENISHMENT`, `ALLOCATED`, `PICKING`, `PACKING`, `COMPLETED`, `CANCELED`, `FAILED` | OutboundOrder |
| `OutboundItemStatus` | `UNALLOCATED`, `PENDING_REPLENISHMENT`, `ALLOCATED`, `PICKED`, `SHORTAGE`, `FAILED` | OutboundItem |
| `OutboxStatus` | `PENDING`, `PUBLISHED` | WmsOutbox |
| `TaskStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELED` | Task (api-spec `WmsService.Common.TaskStatus`와 1:1) |
| `Region` | `SEOUL_METRO`, `GYEONGGI_EAST`, `GYEONGGI_WEST`, `CHUNGCHEONG`, `GANGWON`, `YEONGNAM`, `HONAM`, `JEJU` | Warehouse (`regions` 배열 컬럼의 원소) |

---

## 논의 필요 사항 (Open Questions)

1. **전체 재고 집계**: 동일 상품 재고가 로케이션별로 분산 관리됨. 전체 재고 조회 시 로케이션별 재고를 합산(`SUM(quantity - reserved_quantity)`)해서 응답하는 방식으로 확정 필요. 별도 상품 단위 집계 테이블/캐시를 둘지 여부 검토.
2. **재고 선점(예약) 시점과 대상**: 특정 마감 시각까지 주문을 모았다가 피킹을 시작하는 구조. 마감 전에는 실물 재고가 확정되지 않는데 `reserved_quantity`를 언제 증가시킬지 확정 필요. 선점 시 보관존 재고까지 포함해 차감 대상으로 볼지, 피킹존 가용 재고만 대상으로 볼지 결정 필요.
3. **마감 전 주문 보관 위치**: 마감 전 주문을 MQ(Kafka)에 적재해 두는지, WMS 내부 임시 테이블에 쌓는지 결정 필요. (재처리·조회 요구사항에 따라 달라짐)
4. **로케이션 배정 전략**: 보관존(STORAGE) 입고 적치는 동적 배정으로 1차 구현 완료 — `InboundOrderService.recommendStorageLocation()`이 같은 warehouse/storageType의 PALLET_RACK 중 유효 재고 없음 + 진행 중인 이동 지시 미선점인 로케이션을 aisle/rack/level/bin 오름차순으로 1건 추천하고, 없으면 `WMS409`(`NO_AVAILABLE_LOCATION`)를 던진다. 상품별 지정 로케이션(고정 슬롯) 방식과의 하이브리드 여부, 그리고 피킹존(PICKING) 보충(REPLENISHMENT) 배정 전략은 여전히 미결 — `OutboundOrderService.triggerReplenishment()`는 임시로 "이 상품이 이미 배치돼 있는(그리고 보관 유형이 상품과 일치하는) 피킹존 로케이션을 재사용"만 하고, 한 번도 배치된 적 없는(또는 일치하는 보관 유형으로는 배치된 적 없는) 상품은 보충 지시 자체를 만들지 못한 채 전표를 UNALLOCATED로 남긴다. 실제로는 현재 피킹존 재고를 채우는 흐름이 전혀 없어(입고 적치는 항상 STORAGE로만 감) 이 분기가 상시 발생할 것 — 초기 피킹존 재고를 어떻게 채울지(수동 시딩? 별도 배치?) 결정 필요. (해결됨) 테스트 중 발견했던 storage_type 불일치 문제는 `InventoryJpaRepository.findFirstByWarehouseIdAndProductIdAndLocation_ZoneAndLocation_StorageType()`으로 조회 자체에 `l.storageType = 상품.storageType` 조건을 추가해 막았다 — 잘못 배치된(보관 유형이 다른) 피킹 로케이션은 애초에 후보에서 제외되므로, 확정 불가능한 REPLENISHMENT 이동 지시가 생성될 일이 없다.
5. **InboundOrder 상태 확장**: 버퍼 하차 완료 상태를 별도 상태로 추가할지.
6. **재할당/재시도 소비자**: `OutboundReallocationListener`(`@TransactionalEventListener(AFTER_COMMIT)`) → `OutboundOrderService.retryUnallocated()`/`finalizeReplenishment()`로 구현 완료. FIFO(전표 생성 시각 오름차순)로 처리하며 부분 커버리지 시 분할까지 실제 서버로 검증함(8절 "재시도/최종 할당" 참고). 남은 논의: 여러 REPLENISHMENT/PUT_AWAY가 동시다발적으로 발생할 때의 동시성(같은 상품에 대해 두 리스너가 겹쳐 실행될 가능성 — 현재는 낙관적/비관적 락 어느 쪽도 OutboundItem 레벨엔 없음, Inventory 레벨 락으로 간접 보호되는 정도).
7. **실패 시 order-service 보상 이벤트**: `OutboundOrderFailureScheduler`가 장시간 UNALLOCATED인 전표를 FAILED로 전환하지만, 지금은 WMS 내부 상태만 바꿀 뿐 order-service에 알리지 않는다(사용자 확인: 우선 내부 상태만, 계약 확정되면 추후 구현). 결제/환불 플로우와 연계하려면 별도 이벤트 계약이 필요.
8. (해결됨) **Warehouse.region 값 체계 및 조회 API**: 처음엔 시/도 단위 17개 광역자치단체로 만들었다가, 최종적으로 물류 배정에 의미 있는 8개 값(`SEOUL_METRO`/`GYEONGGI_EAST`/`GYEONGGI_WEST`/`CHUNGCHEONG`/`GANGWON`/`YEONGNAM`/`HONAM`/`JEJU`)으로 재정의했다 — 경기도만 물류센터가 몰려 있어(김포/평택/안산) 동/서로 쪼개고 나머지는 크게 묶었다. 저장 방식도 두 번 바뀌었다: `warehouse.region` 단일 컬럼 → "한 창고가 여러 권역을 담당할 수 있지 않냐"는 지적으로 `warehouse_region` 조인 테이블 → "테이블까지 필요 없지 않냐"는 재지적으로 PostgreSQL 배열 컬럼(`warehouse.regions VARCHAR(20)[]`, `V10__add_warehouse_region.sql`)으로 최종 정리. `GET /internal/v1/wms/warehouses`(region/isActive 쿼리 파라미터) Java 구현도 완료 — `WarehouseInternalController`→`WarehouseQueryService`→`WarehouseJpaRepository.search()`(네이티브 쿼리 + `= ANY(...)`). `seed_wms_warehouse.sql`에 실제 8개 창고의 지리적 인접성을 참고해 매핑(김포=경기서부+수도권, 평택=경기서부+충청권, 창원=영남권, 안산=경기서부, 컬리나우 4곳=수도권 단독) — 여러 창고가 같은 권역을 공유할 수도 있어(경기서부 3곳, 수도권 5곳), 그중 하나를 고르는 기준(거리/부하 분산 등)은 여전히 미결이다. 이 API는 후보 목록을 그대로 반환할 뿐 하나로 좁혀주지 않는다.
9. (해결됨) **OutboundAllocatedEvent 소비자(피킹 Task 자동 생성) + 작업자 배정/시작/완료 API + 출고 완료 API**: `OutboundReallocationListener.onOutboundAllocated()` → `TaskService.createPickingTasks()`로 Task 자동 생성 구현 완료(FEFO 분할 할당된 주문은 그만큼 여러 Task 생성, uk_task_outbound_item_id로 중복 방지). `TaskClientController`(`GET /api/v1/wms/tasks`, `POST /start`, `POST /complete`)로 조회/시작/완료 API도 구현 완료(11절 참고) — 별도 "배정" API 없이 `start` 요청의 `workerId`로 배정을 겸한다. 마지막으로 `POST /api/v1/wms/outbounds/complete`(`OutboundOrderService.completeShipment()`)로 실물 재고 차감(`Inventory.release()`+`remove()`) + `wms.outbound.completed` 이벤트 발행(아웃박스 패턴)까지 구현 완료(8절 참고) — 원래 5단계 플로우가 여기서 전부 끝났다. 이번 버전은 부분 피킹(shortage)을 지원하지 않고 항상 전체 수량을 피킹/차감한 것으로 기록한다. 아직 없는 것: (a) `task_no` 일자별 리셋 카운터(지금은 전역 시퀀스 기반 단순 채번), (b) 부분 피킹/SHORTAGE 지원, (c) `wms.outbound.completed`의 order-service 쪽 실제 소비자(계약만 정의, 컨슈머 팀 확인 필요), (d) 패킹재/보냉재 추천·TMS 송장 발급(별개 관심사로 placeholder 유지).
10. **Worker 엔티티 부재**: `Task.worker_id`가 아직 FK를 걸 `Worker` 테이블이 없어 순수 정수 컬럼으로만 존재한다(11절 참고) — `workers.api.tsp`/`workers.dto.tsp`가 이미 있는데도(main.tsp의 "Workers" 태그) 그동안 Java 엔티티가 만들어지지 않았다. `TaskStartRequest.workerId`/`TaskCompleteRequest.workerId`도 지금은 유효성 검증(창고 소속 확인, 실존 여부 등) 없이 그대로 받아들인다 — Worker 엔티티가 생기면 정리할 필요가 있음.
