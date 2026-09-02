# 주문 도메인 API 명세

## 엔드포인트 목록

### 1. 외부 API (User)
| 도메인 | Method | Path | 기능 설명 | 중요도 | Auth | 소유권 확인 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 주문 | `GET` | `/api/v1/carts` | 장바구니 조회 | 상 | User | 필요 |
| 주문 | `PUT` | `/api/v1/carts/delivery-address` | 장바구니 배송지 변경 및 배송 약속 재조회 | 상 | User | 필요 |
| 주문 | `POST` | `/api/v1/orders/checkout` | 주문서 생성 및 논리 재고 예약 | 상 | User | 필요 |
| 주문 | `POST` | `/api/v1/orders/place-order` | 주문 결제 요청 및 주문 확정 | 상 | User | 필요 |
| 주문 | `POST` | `/api/v1/orders/{orderId}/cancel` | 고객 전체 주문 취소 신청 | 상 | User | 필요 |
| 주문 | `POST` | `/api/v1/orders/{orderId}/returns` | 전체 주문 반품 신청 | 상 | User | 필요 |
| 주문 | `GET` | `/api/v1/orders/cancellations-returns` | 취소·반품 내역 조회 | 상 | User | 필요 |
| 주문 | `GET` | `/api/v1/orders/{orderId}/returns/preview` | 반품 접수 정보 조회 | 상 | User | 필요 |
| 주문 | `GET` | `/api/v1/orders` | 내 주문 목록 조회 | 상 | User | 필요 |
| 주문 | `GET` | `/api/v1/orders/{orderId}` | 주문 상세 조회 | 상 | User | 필요 |

### 2. 내부 API (Internal)
| 도메인 | Method | Path | 기능 설명 | 중요도 | Auth | 소유권 확인 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 주문 | `GET` | `/internal/v1/orders/{orderId}/items` | 재고 확정용 주문 품목 조회 | 상 | Internal | 불필요 |
| 주문 | `GET` | `/internal/v1/orders/{orderId}` | 결제 검증용 주문 조회 | 상 | Internal | 불필요 |
---

## 1. 장바구니 조회

> 로그인 사용자의 장바구니 상품을 배송 유형·온도대별로 묶어 조회하고 주문 예상금액을 계산. 조회 단계에서는 논리 재고를 예약하지 않음.

### Path

```
[GET] /api/v1/carts
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

없음

#### Query Parameters

없음

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "장바구니 조회에 성공했습니다.",
  "data": {
    "selectedAddress": {
      "addressId": 8,
      "addressName": "우리집",
      "recipientName": "홍길동",
      "address": "서울특별시 강남구 테헤란로 123",
      "detailAddress": "101동 1001호"
    },
    "groups": [
      {
        "deliveryType": "DAWN",
        "temperatureType": "CHILLED",
        "seller": null,
        "items": [
          {
            "cartItemId": 101,
            "productId": 10,
            "skuId": 1001,
            "title": "샐러드",
            "thumbnailUrl": "https://cdn.example.com/products/10.jpg",
            "unitPrice": 16000,
            "quantity": 2,
            "maxQuantity": 10,
            "available": true
          }
        ],
        "groupItemAmount": 32000,
        "groupDeliveryFee": 0
      },
      {
        "deliveryType": "SELLER",
        "temperatureType": "ROOM",
        "seller": {
          "sellerId": 31,
          "sellerName": "맛있는농장"
        },
        "items": [],
        "groupItemAmount": 0,
        "groupDeliveryFee": 3000
      }
    ],
    "amountSummary": {
      "totalItemAmount": 32000,
      "discountAmount": 0,
      "deliveryFee": 0,
      "paymentAmount": 32000
    }
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 실패 |
| 502 | PRODUCT_SERVICE_UNAVAILABLE | "상품 정보를 불러오는 중 오류가 발생했습니다." | 상품 서비스(batch-summary) 동기 조회 실패 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `carts`, `cart_items`
* **동기 연동:**
  * Member: `users.id`, `delivery_addresses` (기본/선택 배송지 스냅샷 조회)
  * Product/WMS: `POST /internal/v1/products/batch-summary` (단가, 썸네일, 노출명, 판매자 정보, 가용 재고 확인)
  * TAM/권역 엔진: `deliveryType` 판별 (`DAWN` / `PARCEL`)
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * 장바구니의 `maxQuantity`와 `available`은 단순 안내값이며 재고 선점을 보장하지 않음.
  * 공급 주체(`seller`) 및 보관 온도대(`storage_type`: `ROOM`, `CHILLED`, `FROZEN`) 기준으로 그룹 분할.
  * 장바구니 DB에는 금액을 저장하지 않으며, 실시간 `sale_price × quantity` 연산을 통해 `amountSummary` 동적 생성.



---

## 2. 장바구니 배송지 변경 및 배송 약속 재조회

> 장바구니에서 선택한 배송지로 배송 가능 여부와 배송 약속을 다시 계산. 주문 생성 전 화면용이며 주문 데이터는 생성하지 않음.

### Path

```
[PUT] /api/v1/carts/delivery-address
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |
| Content-Type | Y | application/json |

#### Path Parameters

없음

#### Query Parameters

없음

#### Body

```json
{
  "addressId": 8
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| addressId | Integer | Y | 회원 서비스 배송지 ID |

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "배송 약속 재조회에 성공했습니다.",
  "data": {
    "selectedAddress": {
      "addressId": 8,
      "addressName": "우리집",
      "recipientName": "홍길동",
      "recipientPhone": "010-1234-5678",
      "address": "서울특별시 강남구 테헤란로 123",
      "detailAddress": "101동 1001호"
    },
    "deliverable": true,
    "deliveryType": "DAWN",
    "cutoffAt": "2026-08-26T23:00:00+09:00",
    "expectedDeliveryAt": "2026-08-27T07:00:00+09:00"
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_ADDRESS_ID | "배송지 ID가 올바르지 않습니다." | addressId 누락 또는 올바르지 않은 값 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 403 | ADDRESS_ACCESS_DENIED | "해당 배송지를 사용할 권한이 없습니다." | 배송지 소유권 불일치 |
| 404 | ADDRESS_NOT_FOUND | "배송지 정보를 찾을 수 없습니다." | 회원 서비스에 존재하지 않는 addressId |
| 422 | DELIVERY_NOT_AVAILABLE | "배송이 불가능한 지역입니다." | TAM 주소 매핑 규칙 상 배송 불가 권역 |
| 502 | MEMBER_SERVICE_UNAVAILABLE | "회원 배송지 정보를 조회할 수 없습니다." | 회원 서비스(배송지 상세 조회) 동기 호출 실패 |
| 502 | OMS_UNAVAILABLE | "배송 가능 여부를 확인할 수 없습니다." | OMS/TAM 응답 실패 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `carts`, `cart_items`
* **동기 연동:** Member 서비스(배송지 상세 및 소유권 검증) 후 `POST /internal/v1/oms/delivery-promises`
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * 프론트엔드로부터 전달받은 `addressId`의 소유권을 회원 서비스에서 동기 검증.
  * OMS/TAM 배송 약속을 동기 조회한 뒤, 검증된 `address_id`를 `carts` 테이블에 저장 및 배송 약속 함께 반환.



---

## 3. 주문서 생성 및 논리 재고 예약

> 선택한 장바구니 상품으로 CHECKOUT_CREATED 주문서를 생성하고 상품 서비스를 통해 재고를 15분간 선점. 유저당 활성 주문서는 1개만 유지되며 15분 내 결제 미진행 시 무효화(EXPIRED).

### Path

```
[POST] /api/v1/orders/checkout
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |
| Idempotency-Key | Y | 중복 명령 방지용 고유 키 |
| Content-Type | Y | application/json |

#### Path Parameters

없음

#### Query Parameters

없음

#### Body

```json
{
  "cartItemIds": [
    101,
    102
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| cartItemIds | Array | Y | 주문할 장바구니 항목 ID 목록 |

### Response

#### 성공 (201 Created)

```json
{
  "status": "SUCCESS",
  "message": "주문서 생성 성공",
  "data": {
    "orderId": 501,
    "orderNo": "O202608260001",
    "reservationToken": "rsv_xxx",
    "expiresAt": "2026-08-26T15:15:00+09:00",
    "orderer": {
      "name": "홍길동",
      "phone": "010-1234-5678",
      "email": "hong@example.com"
    },
    "deliveryAddress": {
      "addressId": 8,
      "addressName": "우리집",
      "recipientName": "홍길동",
      "recipientPhone": "010-1234-5678",
      "address": "서울특별시 강남구 테헤란로 123",
      "detailAddress": "101동 1001호"
    },
    "deliveryPromise": {
      "deliverable": true,
      "deliveryType": "DAWN",
      "cutoffAt": "2026-08-26T23:00:00+09:00",
      "expectedDeliveryAt": "2026-08-27T07:00:00+09:00"
    },
    "groups": [
      {
        "deliveryType": "DAWN",
        "temperatureType": "CHILLED",
        "items": [
          {
            "orderItemId": 1,
            "productId": 10,
            "skuId": 1001,
            "title": "샐러드",
            "thumbnailUrl": "https://cdn.example.com/products/10.jpg",
            "unitPrice": 16000,
            "quantity": 2,
            "totalPrice": 32000
          }
        ]
      }
    ],
    "amountSummary": {
      "totalItemAmount": 32000,
      "discountAmount": 0,
      "deliveryFee": 0,
      "paymentAmount": 32000
    }
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_CART_ITEMS | "주문할 장바구니 항목이 올바르지 않습니다." | cartItemIds가 비어있거나 소유하지 않은 장바구니 항목 요청 |
| 400 | DELIVERY_ADDRESS_NOT_SET | "배송지를 먼저 설정해 주세요." | 장바구니에 선택/기본 배송지가 설정되지 않은 상태로 진입 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 409 | STOCK_EXHAUSTED | "선택한 상품의 재고가 부족합니다." | 상품 서비스 15분 논리 재고 선점 실패 (잔여 재고 부족) |
| 409 | DUPLICATE_CHECKOUT_REQUEST | "이미 처리 중이거나 완료된 주문 요청입니다." | 동일 Idempotency-Key로 이미 처리 중이거나 완료된 요청 |
| 502 | PRODUCT_SERVICE_UNAVAILABLE | "재고 확인 서비스와의 통신에 실패했습니다." | 상품 서비스(재고 선점 API) 연동 실패 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`, `order_delivery_info`, `idempotency_keys`
* **동기 연동:**
  * `POST /internal/v1/oms/delivery-promises`
  * `POST /internal/v1/products/inventory/hold`
  * `POST /internal/v1/products/inventory/release`
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * `carts`의 `address_id`를 기준으로 회원 서비스에서 최신 주소를 동기 조회하여 `order_delivery_info` 스냅샷으로 불변 저장.
  * 상품 서비스에 15분 TTL 논리 재고 선점 요청 후 응답받은 `reservationToken`과 `expiresAt`을 `orders`에 저장.
  * 유저당 활성 가주문서는 1건만 허용하며, 재진입 시 기존 `CHECKOUT_CREATED` 주문서의 선점 만료 시각 기준으로 분기:
  * 선점 유효 (현재 시각 < 만료 시각): 상품 서비스 선점 해제 동기 호출 후 기존 주문서 `EXPIRED` 처리.
  * 선점 만료 (현재 시각 ≥ 만료 시각): 해제 호출 없이 기존 주문서만 `EXPIRED` 처리.
  * 동시 변경 방지를 위해 `orders` 레코드 비관적 락(`FOR UPDATE`) 적용.

---

## 4. 주문 결제 요청 및 주문 확정

> 주문서를 결제 진행 상태(PAYMENT_PENDING)로 전이하여 만료 대상에서 격리하고, 결제 서비스(Payment)의 PG 결제창 진입을 준비.

### Path

```
[POST] /api/v1/orders/place-order
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |
| Idempotency-Key | Y | 중복 명령 방지용 고유 키 |
| Content-Type | Y | application/json |

#### Path Parameters

없음

#### Query Parameters

없음

#### Body

```json
{
  "orderId": 1423
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Integer | Y | 주문 ID |

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "주문 결제 요청 성공",
  "data": {
    "orderId": 501,
    "orderNo": "O202608260001",
    "status": "PAYMENT_PENDING"
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_ORDER_STATUS | "결제를 진행할 수 없는 주문 상태입니다." | CHECKOUT_CREATED가 아닌 상태에서 결제 요청 진입 |
| 403 | ORDER_OWNERSHIP_MISMATCH | "주문 소유권이 일치하지 않습니다." | 요청 사용자와 주문서 소유자 불일치 |
| 404 | ORDER_NOT_FOUND | "주문 정보를 찾을 수 없습니다." | 주문서 미존재 또는 만료 |
| 409 | ORDER_ALREADY_PAID | "이미 결제가 완료된 주문입니다." | 이미 결제 완료된 주문 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_outbox`
* **동기 연동:** 결제 서비스 전용 `GET /internal/v1/orders/{orderId}` 제공
* **비동기 연동 (RabbitMQ):**
  * Subscribe: `payment.order.completed`, `payment.order.failed`, `product.inventory.exhausted`, `payment.order.refunded`
  * Publish: `sales.order.created` (Transactional Outbox 패턴)
* **도메인 규칙:**
  * 주문 상태가 `CHECKOUT_CREATED`일 때만 `PAYMENT_PENDING`으로 전이하여 만료 배치 대상에서 격리.
  * 본 API는 상태 전이만 수행하며, 결제창 호출 및 재고 TTL 검증은 결제 서비스가 담당.
  * 최종 주문 확정(`PAID`)은 `payment.order.completed` 메시지 소비 후 비동기 처리하며, Outbox를 통해 OMS로 `sales.order.created` 이벤트 발행.

---

## 5. 고객 전체 주문 취소 신청

> 출고 지시 전 주문 전체를 취소하고 필요시 WMS 예약 해제와 결제 전액 취소를 수행.

### Path

```
[POST] /api/v1/orders/{orderId}/cancel
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |
| Idempotency-Key | Y | 중복 명령 방지용 고유 키 |
| Content-Type | Y | application/json |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Integer | Y | 취소할 주문 ID |

#### Query Parameters

없음

#### Body

```json
{
  "reasonCode": "CNL01",
  "reasonDetail": null
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| reasonCode | String | Y | 취소 사유 코드 (CNL01~CNL05, CNL99) |
| reasonDetail | String | N | 상세 사유 (CNL99 선택 시 필수) |

*취소 사유 코드 규격:*

* `CNL01`: 단순 변심 (상세 사유 선택)
* `CNL02`: 주문 실수 (상세 사유 선택)
* `CNL03`: 배송지 변경을 위한 재주문 (상세 사유 선택)
* `CNL04`: 결제수단 변경을 위한 재주문 (상세 사유 선택)
* `CNL05`: 배송 지연 (상세 사유 선택)
* `CNL99`: 기타 (상세 사유 필수)

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "전체 주문 취소가 접수되었습니다.",
  "data": {
    "cancellationId": 81,
    "orderId": 501,
    "cancellationStatus": "REQUESTED",
    "refundAmount": 32000,
    "cancelledAt": "2026-08-26T16:00:00+09:00"
  },
  "error": null,
  "timestamp": "2026-08-26T07:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_CANCEL_REASON_CODE | "올바르지 않은 취소 사유 코드입니다." | 정의되지 않은 취소 사유 코드 전달 |
| 400 | INVALID_CANCEL_REASON_DETAIL | "기타 사유 선택 시 상세 사유를 입력해야 합니다." | CNL99 선택 시 reasonDetail 누락 또는 공백 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 403 | ORDER_ACCESS_DENIED | "해당 주문을 취소할 권한이 없습니다." | 요청 사용자와 주문 소유자(member_id) 불일치 |
| 404 | ORDER_NOT_FOUND | "주문 정보를 찾을 수 없습니다." | 대상 orderId가 DB에 존재하지 않음 |
| 409 | INVALID_ORDER_STATUS_FOR_CANCEL | "결제가 완료된 주문만 취소를 신청할 수 있습니다." | PAID 상태가 아닌 주문에 취소 요청 |
| 409 | CANCEL_RESTRICTED | "이미 출고 처리가 시작되어 취소할 수 없습니다. 배송 완료 후 반품을 신청해 주세요." | OMS 출고 지시(RELEASE_INSTRUCTED) 완료 또는 배송 진행 중 |
| 409 | ALREADY_CANCELLED | "이미 취소 접수되었거나 처리가 완료된 주문입니다." | 이미 취소 접수(REQUESTED), 처리 중 또는 완료된 주문 |
| 409 | DUPLICATE_CANCEL_REQUEST | "이미 처리 중인 취소 요청입니다." | 동일 Idempotency-Key로 이미 처리 중이거나 완료된 요청 |
| 502 | OMS_SERVICE_UNAVAILABLE | "출고 상태를 확인하는 중 오류가 발생했습니다." | OMS 동기 호출 실패 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_claims`, `order_outbox`
* **동기 연동:** `GET /internal/v1/oms/orders/{orderId}/cancel-eligibility` (OMS 출고 지시 여부 동기 검증)
* **비동기 연동 (RabbitMQ):**
  * Publish: `order.cancel.requested` (취소 접수 후 OMS 재고 해제 및 취소 처리 위임)
  * Subscribe: `payment.order.refunded` (결제 환불 완료 수신 시 주문 상태 REFUNDED 갱신)
* **도메인 규칙:**
  * 전체 취소만 지원 (부분 취소 불가).
  * 결제 완료(`PAID`) 상태 및 OMS 출고 지시(`RELEASE_INSTRUCTED`) 이전 상태만 접수 가능.



---

## 6. 전체 주문 반품 신청

> 배송 완료 주문 전체에 대한 반품을 신청. 온도대·사유 정책에 따라 회수 여부와 환불 처리를 결정.

### Path

```
[POST] /api/v1/orders/{orderId}/returns
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |
| Idempotency-Key | Y | 중복 신청 방지용 고유 키 |
| Content-Type | Y | application/json |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Integer | Y | 반품 대상 주문 ID |

#### Query Parameters

없음

#### Body

```json
{
  "reasonCode": "RTN01",
  "reasonDetail": "수령 시 상품이 파손되어 있었습니다."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| reasonCode | String | Y | 반품 사유 코드 (RTN01~RTN08) |
| reasonDetail | String | N | 상세 사유 (최대 500자) |

*반품 사유 코드 정책:*

* `RTN01`: 단순 변심 (냉장·냉동 상품 신청 불가)
* `RTN02`: 상품 불량
* `RTN03`: 상품 파손
* `RTN04`: 냉해·해동
* `RTN05`: 오배송
* `RTN06`: 상품 누락
* `RTN07`: 상품 품절
* `RTN08`: 상품정보 상이

### Response

#### 성공 (201 Created)

```json
{
  "status": "SUCCESS",
  "message": "전체 주문 반품이 접수되었습니다.",
  "data": {
    "returnId": 72,
    "orderId": 501,
    "returnStatus": "REQUESTED",
    "refundStatus": "PENDING",
    "expectedRefundAmount": 32000,
    "requestedAt": "2026-08-27T10:00:00+09:00"
  },
  "error": null,
  "timestamp": "2026-08-27T01:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_RETURN_REASON_CODE | "올바르지 않은 반품 사유 코드입니다." | 정의되지 않은 반품 사유 코드 전달 |
| 400 | RETURN_REASON_DETAIL_TOO_LONG | "상세 사유는 최대 500자까지 입력 가능합니다." | reasonDetail이 500자를 초과한 경우 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 403 | ORDER_ACCESS_DENIED | "해당 주문에 대한 반품 권한이 없습니다." | 요청 사용자와 주문 소유자(member_id) 불일치 |
| 404 | ORDER_NOT_FOUND | "주문 정보를 찾을 수 없습니다." | 대상 orderId가 DB에 존재하지 않음 |
| 409 | RETURN_ALREADY_REQUESTED | "이미 접수되었거나 처리 중인 반품 신청이 있습니다." | 이미 반품 접수(REQUESTED) 또는 처리 중인 주문 |
| 409 | DUPLICATE_RETURN_REQUEST | "이미 처리 중인 반품 요청입니다." | 동일 Idempotency-Key로 이미 처리 중이거나 완료된 요청 |
| 422 | INVALID_ORDER_STATUS_FOR_RETURN | "배송이 완료된 주문만 반품을 신청할 수 있습니다." | DELIVERED 상태가 아닌 주문 |
| 422 | RETURN_PERIOD_EXPIRED | "반품 신청 가능 기간이 지났습니다." | 배송 완료 후 7일 초과 |
| 422 | FRESH_FOOD_RETURN_RESTRICTED | "신선식품(냉장/냉동)은 단순 변심으로 인한 반품이 불가능합니다." | 냉장/냉동 상품 포함 주문에 단순 변심(RTN01) 신청 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`, `order_claims`, `order_outbox`
* **동기 연동:** 없음
* **비동기 연동 (RabbitMQ):**
  * Publish: `order.return.requested` (반품 접수 이벤트 발행)
  * Subscribe: `payment.order.refunded` (환불 완료 수신 시 주문 및 클레임 상태 REFUNDED/COMPLETED 최종 갱신)
* **도메인 규칙:**
  * 전체 반품만 지원 (부분 반품 불가).
  * 배송 완료(`DELIVERED`) 후 7일 이내 건만 접수 허용.
  * 주문 품목 중 신선식품(`CHILLED`, `FROZEN`)이 1건이라도 포함된 경우 단순 변심(`RTN01`) 반품 차단.
  
---

## 7. 취소·반품 내역 조회

> 로그인 사용자의 주문 취소와 반품 신청 내역, 처리 진행도 및 시스템 환불 결과를 통합 조회.

### Path

```
[GET] /api/v1/orders/cancellations-returns
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

없음

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| requestType | String | N | - | CANCEL 또는 RETURN |
| requestStatus | String | N | - | 취소·반품 진행상태 |
| page | Integer | N | 1 | 페이지 번호 (1 이상) |
| size | Integer | N | 20 | 페이지 크기 (1~100) |

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "취소·반품 내역을 조회했습니다.",
  "data": {
    "total": 2,
    "page": 1,
    "size": 20,
    "histories": [
      {
        "requestType": "CANCEL",
        "requestId": 81,
        "orderId": 501,
        "orderNo": "O202608260001",
        "requestStatus": "COMPLETED",
        "refundStatus": "COMPLETED",
        "refundAmount": 41000,
        "requestedAt": "2026-08-26T16:00:00+09:00",
        "completedAt": "2026-08-26T16:05:00+09:00",
        "items": [
          {
            "orderItemId": 1001,
            "productId": 10,
            "title": "샐러드",
            "thumbnailUrl": "https://cdn.example.com/products/10.jpg",
            "deliveryType": "DAWN",
            "unitPrice": 16000,
            "quantity": 2,
            "totalPrice": 32000
          },
          {
            "orderItemId": 1002,
            "productId": 11,
            "title": "유기농 우유",
            "thumbnailUrl": "https://cdn.example.com/products/11.jpg",
            "deliveryType": "DAWN",
            "unitPrice": 9000,
            "quantity": 1,
            "totalPrice": 9000
          }
        ]
      },
      {
        "requestType": "RETURN",
        "requestId": 72,
        "orderId": 502,
        "orderNo": "O202608260002",
        "requestStatus": "INSPECTING",
        "refundStatus": "PENDING",
        "refundAmount": null,
        "requestedAt": "2026-08-27T10:00:00+09:00",
        "completedAt": null,
        "items": [
          {
            "orderItemId": 1003,
            "productId": 12,
            "title": "냉동 만두",
            "thumbnailUrl": "https://cdn.example.com/products/12.jpg",
            "deliveryType": "DAWN",
            "unitPrice": 12000,
            "quantity": 1,
            "totalPrice": 12000
          }
        ]
      }
    ]
  },
  "error": null,
  "timestamp": "2026-08-27T01:05:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST_TYPE | "올바르지 않은 요청 유형입니다. (CANCEL, RETURN)" | requestType에 정의 외 값 전달 |
| 400 | INVALID_REQUEST_STATUS | "올바르지 않은 진행 상태값입니다." | 유효하지 않은 requestStatus 상태값 전달 |
| 400 | INVALID_PAGE_PARAMETER | "페이지 번호 및 크기 파라미터가 올바르지 않습니다." | page < 1 또는 size < 1 / size > 100 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`, `order_claims`
* **동기 연동:** 없음
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * 신청 일시(`requestedAt`) 기준 최신순(DESC) 정렬.
  * 주문 당시의 상품 스냅샷(`title`, `thumbnailUrl`, `unitPrice`, `quantity`, `deliveryType`)을 `items` 배열에 포함하여 반환.

---

## 8. 반품 접수 정보 조회

> 배송 완료 주문의 반품 가능 여부, 선택 가능한 사유와 예상 환불정보를 조회해 반품 접수 화면을 구성.

### Path

```
[GET] /api/v1/orders/{orderId}/returns/preview
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Integer | Y | 주문 ID |

#### Query Parameters

없음

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "반품 접수 정보를 조회했습니다.",
  "data": {
    "orderId": 501,
    "returnable": true,
    "temperaturePolicy": "CHILLED",
    "reasonOptions": [
      {
        "code": "RTN01",
        "displayName": "상품 파손",
        "attachmentRequired": true
      }
    ],
    "refundPreview": {
      "paymentAmount": 32000,
      "deductionAmount": 0,
      "expectedRefundAmount": 32000
    },
    "returnPolicy": {
      "collectionRequired": false,
      "guideMessage": "상품 사진 확인 후 자체 폐기 또는 회수 여부가 결정됩니다."
    }
  },
  "error": null,
  "timestamp": "2026-08-27T01:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 403 | ORDER_ACCESS_DENIED | "해당 주문에 접근할 권한이 없습니다." | 요청 사용자와 주문 소유자(member_id) 불일치 |
| 404 | ORDER_NOT_FOUND | "주문 정보를 찾을 수 없습니다." | 대상 orderId가 DB에 존재하지 않음 |
| 409 | RETURN_ALREADY_REQUESTED | "이미 반품 신청이 접수된 주문입니다." | 이미 반품 접수되었거나 처리 중인 주문 |
| 422 | INVALID_ORDER_STATUS_FOR_RETURN | "배송 완료된 주문만 반품 신청 정보를 조회할 수 있습니다." | DELIVERED 상태가 아닌 주문 |
| 422 | RETURN_PERIOD_EXPIRED | "반품 신청 가능 기간이 지난 주문입니다." | 배송 완료 후 7일 초과 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`, `order_claims`
* **동기 연동:** 없음
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * 화면 구성을 위한 단순 조회 API로 주문 및 클레임 데이터를 변경하지 않음.
  * 주문 품목 중 냉장(`CHILLED`) 또는 냉동(`FROZEN`) 상품 포함 시 `reasonOptions`에서 `RTN01`(단순 변심) 제외.
  * 결제 총액(`payment_amount`)을 기준으로 차감액과 예상 환불금액 동적 산출.

---

## 9. 내 주문 목록 조회

> 조회기간과 상품명 기준으로 로그인 사용자의 정상 결제 주문 목록을 페이징 조회.

### Path

```
[GET] /api/v1/orders
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

없음

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| range | String | N | 3M | 검색 범위 (3M, 6M, 1Y, 3Y) |
| productName | String | N | - | 상품명 검색어 (최대 100자) |
| page | Integer | N | 1 | 페이지 번호 (1 이상) |
| size | Integer | N | 20 | 페이지 크기 (1~100) |

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "주문 목록 조회에 성공했습니다.",
  "data": {
    "total": 1,
    "page": 1,
    "size": 20,
    "orders": [
      {
        "orderId": 501,
        "orderNo": "O202608260001",
        "orderedAt": "2026-08-26T15:10:00+09:00",
        "orderStatus": "PAID",
        "fulfillmentStatus": "PROCESSING",
        "deliveryStatus": "IN_TRANSIT",
        "expectedDeliveryAt": "2026-08-27T07:00:00+09:00",
        "deliveredAt": null,
        "totalPrice": 41000,
        "totalQuantity": 3,
        "items": [
          {
            "orderItemId": 1001,
            "productId": 10,
            "skuId": 1001,
            "deliveryType": "DAWN",
            "title": "샐러드",
            "thumbnailUrl": "https://cdn.example.com/products/10.jpg",
            "unitPrice": 16000,
            "quantity": 2,
            "totalPrice": 32000
          },
          {
            "orderItemId": 1002,
            "productId": 11,
            "skuId": 1101,
            "deliveryType": "DAWN",
            "title": "유기농 우유",
            "thumbnailUrl": "https://cdn.example.com/products/11.jpg",
            "unitPrice": 9000,
            "quantity": 1,
            "totalPrice": 9000
          }
        ]
      }
    ]
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_DATE_RANGE | "조회 기간 설정이 올바르지 않습니다." | range에 정의되지 않은 값 전달 |
| 400 | INVALID_PAGE_PARAMETER | "페이지 번호 및 크기 파라미터가 올바르지 않습니다." | page < 1 또는 size < 1 / size > 100 |
| 400 | INVALID_SEARCH_KEYWORD | "검색어는 최대 100자까지 입력 가능합니다." | productName 검색어가 100자 초과한 경우 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`
* **동기 연동:** 없음
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * 단순 가주문서 상태인 `CHECKOUT_CREATED` 및 `PAYMENT_PENDING`은 목록에서 제외하며 결제 완료(`PAID`) 이후 주문만 조회.
  * `orderedAt` 기준 내림차순(DESC) 정렬.
  * `productName` 검색 시 `order_items.title`에 대한 부분 일치(LIKE) 검색 수행.

---

## 10. 주문 상세 조회

> 주문 상품, 금액, 배송지, 요청사항, 물류·배송 상태와 취소 가능 여부를 통합 조회.

### Path

```
[GET] /api/v1/orders/{orderId}
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Integer | Y | 주문 ID (1 이상) |

#### Query Parameters

없음

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "status": "SUCCESS",
  "message": "주문 상세 조회에 성공했습니다.",
  "data": {
    "orderInfo": {
      "orderId": 501,
      "orderNo": "O202608260001",
      "orderedAt": "2026-08-26T15:10:00+09:00",
      "orderStatus": "PAID",
      "sender": {
        "name": "홍길동",
        "phoneNumber": "010-1234-5678"
      }
    },
    "items": [
      {
        "orderItemId": 1,
        "productId": 10,
        "skuId": 1001,
        "thumbnailUrl": "https://cdn.example.com/products/10.jpg",
        "title": "샐러드",
        "deliveryType": "DAWN",
        "unitPrice": 16000,
        "quantity": 2,
        "totalPrice": 32000
      }
    ],
    "deliveryInfo": {
      "fulfillmentStatus": "PROCESSING",
      "deliveryStatus": "IN_TRANSIT",
      "expectedDeliveryAt": "2026-08-27T07:00:00+09:00",
      "deliveredAt": null,
      "receiver": {
        "name": "김고객",
        "phoneNumber": "010-9876-5432"
      },
      "address": {
        "postalCode": "06236",
        "roadAddress": "서울시 강남구 ...",
        "detailAddress": "101동 101호"
      },
      "pickupType": "DOOR",
      "accessMethod": "PASSWORD",
      "packingType": "PAPER",
      "deliveryMessage": "문 앞에 놓아주세요"
    },
    "paymentSummary": {
      "paymentId": 9001,
      "paymentAmount": 32000,
      "paymentStatus": "PAID"
    },
    "selfCancelable": true
  },
  "error": null,
  "timestamp": "2026-08-26T10:00:00Z"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Response Message | Description |
| --- | --- | --- | --- |
| 400 | INVALID_ORDER_ID | "올바르지 않은 주문 ID입니다." | orderId가 1 미만이거나 숫자 형식이 아님 |
| 401 | UNAUTHORIZED | "로그인이 필요합니다." | 인증 토큰 누락 또는 유효하지 않은 토큰 |
| 403 | ORDER_ACCESS_DENIED | "해당 주문에 접근할 권한이 없습니다." | 요청 사용자와 주문 소유자(member_id) 불일치 |
| 404 | ORDER_NOT_FOUND | "주문 정보를 찾을 수 없습니다." | 대상 orderId 미존재 또는 미확정 가주문서 상태 |
| 500 | INTERNAL_SERVER_ERROR | "서버 오류가 발생했습니다." | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`, `order_delivery_info`
* **동기 연동:** 필요 시 `GET /internal/v1/oms/orders/{orderId}/cancel-eligibility`
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:**
  * `CHECKOUT_CREATED`, `PAYMENT_PENDING` 임시 가주문서는 조회 대상에서 제외 (404 반환).
  * `orderStatus == PAID`이고 물류 상태(`fulfillmentStatus`)가 출고 지시(`RELEASE_INSTRUCTED`) 이전 단계일 때만 `selfCancelable: true` 응답.
  * 주문 당시 저장된 수령인 및 배송 요청사항 불변 스냅샷 데이터 유지 반환.

---

## 11. 재고 확정용 주문 품목 조회 (내부용)

> 상품 서비스가 재고 선점 만료 후 실재고 차감을 위해 주문 품목과 수량을 조회.

### Path

```
[GET] /internal/v1/orders/{orderId}/items
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Long | Y | 주문 ID |

#### Query Parameters

없음

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "orderId": 501,
  "items": [
    {
      "productId": 10,
      "quantity": 2
    }
  ]
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Description |
| --- | --- | --- |
| 404 | ORDER_NOT_FOUND | 주문이 없는 경우 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`, `order_items`
* **동기 연동:** 내부 호출 주체: 상품 서비스
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:** 상품 서비스의 물리 재고 확정(Deduction) 처리를 위한 품목 ID 및 수량 데이터 제공.

---

## 12. 결제 검증용 주문 조회 (내부용)

> 결제 서비스가 결제 전 주문 금액, 소유자, 상태와 재고 선점 정보를 조회.

### Path

```
[GET] /internal/v1/orders/{orderId}
```

### Request

#### Headers

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | Y | Bearer {Access Token} |

#### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| orderId | Long | Y | 주문 ID |

#### Query Parameters

없음

#### Body

없음

### Response

#### 성공 (200 OK)

```json
{
  "orderId": 501,
  "userId": 1001,
  "amount": 32000,
  "status": "PAYMENT_PENDING",
  "reservationToken": "rsv_xxx"
}
```

#### 실패 (Error Codes)

| HTTP Status | Error Code | Description |
| --- | --- | --- |
| 404 | ORDER_NOT_FOUND | 주문이 없거나 만료된 경우 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류 |

### Integration & Business Policies

* **관련 테이블:** `orders`
* **동기 연동:** 내부 호출 주체: 결제 서비스
* **비동기 연동 (RabbitMQ):** 없음
* **도메인 규칙:** 결제 모듈에서 PG사 승인 전 주문 유효성, 총 금액 일치 여부 및 재고 선점 토큰 검증을 위한 데이터 제공.