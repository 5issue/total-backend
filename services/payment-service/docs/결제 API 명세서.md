| 도메인 | METHOD | bearer | PATH                                      | 기능상세                 | Auth | 소유권확인 |
| --- | ------ | ------ | ----------------------------------------- | -------------------- | ---- | ----- |
| 결제  | POST   | O      | /api/v1/payments/checkout                 | 결제 승인 요청             | User | 필요    |
| 결제  | POST   | O      | /internal/v1/payments/{payment_id}/cancel | 결제 취소 요청             | User | 필요    |
| 결제  | GET    | O      | /api/v1/payments/{payment_id}/receipt     | 결제 완료 결과 및 승인 영수증 조회 | User | 필요    |

## 결제 승인 요청
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|Y|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|
|Idemopotency-Key|Y|동일한 결제 승인 요청 중복 방지를 위한 UUID|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|-|-|-|해당없음|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|-|-|-|-|해당없음|

---

## 🔹 Body

```json
{
	"orderId": 111,
	"paymentMethod": "CARD",
	"paymentKey": "TOSSTOKEN...",
	"amount": 32000
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|orderId|Long|Y|주문 ID. 이 값으로 주문 서비스에 동기 조회해 금액·소유자·상태를 검증한다|
|paymentMethod|String|Y|결제 수단|
|paymentKey|String|Y|토스페이먼츠가 발급한 결제 승인 인증 토큰|
|amount|Long|Y|**원 단위 정수.** 금액 위변조 검증을 위한 최종 결제금액|

> **`orderSheetToken`은 쓰지 않는다.** 주문-결제 시퀀스 단계 2에서 결제 서비스가
> `GET /internal/v1/orders/{orderId}`로 주문 서비스에 직접 조회해 **금액·소유자 ID·상태·남은
> 타임아웃**을 받아 검증한다. 별도의 검증 토큰을 발급·전달할 이유가 없다.
>
> **금액은 소수점을 받지 않는다.** DECIMAL로 다루면 PG가 소수부를 절사·반올림했을 때 승인 금액과
> 저장 금액이 어긋나 정산 대사가 실패한다. 소수부가 있으면 400으로 거부한다.

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "결제가 성공적으로 승인 및 완료되었습니다.",
  "data": {
	  "paymentCompletedAt" : "2026-08-23T10:00:00Z",
	  "receiptUrl" : "<https://toss.im/receipt/url-dummy>",
	  "paymentStatus": "SUCCESS"
  },
  "error": null,
  "timestamp": "2026-08-23T10:00:30Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "동일한 결제 요청이 처리 중이거나 이미 완료되었습니다.",
  "data": null,
  "error": "DUPLICATE_PAYMENT_REQUEST",
  "timestamp": "2026-08-23T10:00:30Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`BAD_REQUEST`|금액 불일치 (위변조 검증 실패)|“요청된 결제 금액이 실제 주문 금액과 일치하지 않습니다.”|
|401|`UNAUTHORIZED`|로그인 세션 만료 및 인증 토큰 누락|“인증이 필요합니다.”|
|402|`PAYMENT_REQUIRED`|결제 한도 초과 또는 잔액 부족 시|“결제 잔액이 부족하거나 한도를 초과했습니다.|
|409|`CONFLICT`|Idempotency Key 충돌 (중복 결제 시도 차단)|“동일한 결제 요청이 처리 중이거나 이미 완료되었습니다.”|
|500|`INTERNAL_SERVER_ERROR`|PG 승인 처리 중 네트워크 타임아웃 등 에러|“결제 승인 중 오류가 발생했습니다. 다시 시도해주세요.”|

## 결제 취소 요청
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|Y|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|
|Idemopotency-Key|Y|동일한 취소 요청 중복 방지용 UUID|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|payment_id|Long|Y|취소하려는 결제 ID|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|-|-|-|-|해당없음|

---

## 🔹 Body

```json
{
	"cancelReason": "USER_CANCEL"
}
```

|필드|타입|설명|
|---|---|---|
|cancelReason|String|취소 사유|

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "결제가 정상적으로 취소되었습니다.",
  "data": {
	  "paymentId": "12345",
	  "status": "CANCELED",
	  "canceledAt": "2026-08-23T10:30:00Z"
  },
  "error": null,
  "timestamp": "2026-08-23T10:30:00Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "이미 취소되었거나 유효하지 않은 결제 건입니다.",
  "data": null,
  "error": "INVALID_PAYMENT_STATUS",
  "timestamp": "2026-08-23T10:30:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`BAD_REQUEST`|이미 취소된 상태이거나 상품 출고 지시 완료 이후 취소 시도 시|“이미 취소되었거나 유효하지 않은 결제 건입니다.”|
|404|`PAYMENT_NOT_FOUND`|존재하지 않는 결제, **또는 타인 소유 결제**|“존재하지 않는 결제 내역입니다.”|
|409|`CONFLICT`|Idempotency Key 충돌 (취소 처리 중복 진행)|“취소 처리가 진행 중입니다.”|
|500|`INTERNAL_SERVER_ERROR`|PG사 취소 API 호출 실패 및 네트워크 오류|"취소 처리 중 오류가 발생했습니다.”|

## 결제 완료 결과 및 승인 영수증 조회
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|Y|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|payment_id|Long|Y|조회하려는 주문의 ID|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|-|-|-|-|해당없음|

---

## 🔹 Body

```json
{}
```

|필드|타입|설명|
|---|---|---|
|-|-|요청 본문 없음|

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "주문 완료 및 결제 영수증 정보가 조회되었습니다.",
  "data": {
	  "paymentId": 12345,
    "orderId": 111,
    "paymentMethod": "CARD",
    "totalAmount": 50000,
    "paymentCompletedAt": "2026-08-24T10:45:00Z",
    "receiptUrl": "<https://toss.im/receipt/url-example>"
  },
  "error": null,
  "timestamp": "2026-08-23T10:00:00Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "존재하지 않는 결제 내역입니다.",
  "data": null,
  "error": "PAYMENT_NOT_FOUND",
  "timestamp": "2026-08-23T10:00:00Z"
}
```

| **HTTP 상태 코드** | **에러 코드**               | **상황**                                            | **응답 메시지**                     |
| -------------- | ----------------------- | ------------------------------------------------- | ------------------------------ |
| 401            | `UNAUTHORIZED`          | 로그인 세션 만료 및 토큰 없음                                 | “인증이 필요합니다.”                   |
| 404            | `PAYMENT_NOT_FOUND`     | 존재하지 않는 결제, **또는 타인 소유 결제**                       | "존재하지 않는 결제 내역입니다.”            |
| 500            | `INTERNAL_SERVER_ERROR` | 내부 통신 연동 실패(타임아웃 등) 시                             | "주문 상세 정보를 불러오는 중 오류가 발생했습니다.” |

> **타인 소유 결제에 403을 주지 않는 이유**: 403은 "그 ID는 실재하지만 네 것이 아니다"를 알려주는
> 셈이라, ID를 훑어 남의 결제 존재 여부를 파악할 수 있다. 없는 것과 남의 것을 **같은 404**로
> 응답한다(시큐어코딩가이드 BE-06·BE-17). user-service의 배송지와 같은 판단이다.
---

# 부록. 에러 코드 (컨벤션 정렬안)

`common`의 `ErrorCode` 인터페이스를 구현한다. `GlobalErrorCode`로 표현되는 것은 재정의하지 않고
그대로 쓰고, 도메인 고유한 것만 `PaymentErrorCode`에 정의한다(auth·user-service와 동일한 방식).
코드 문자열은 `<도메인약어><HTTP상태>` 형식이다 — `COMMON400`, `AUTH423`, `PAY402`.

## 공통 코드 재사용분

| 에러 코드 | 코드 | 상태 | 사용처 |
| --- | --- | --- | --- |
| `INVALID_INPUT_VALUE` | COMMON400 | 400 | 필수값 누락, 금액에 소수부, 본문 형식 오류 |
| `UNAUTHORIZED` | COMMON401 | 401 | 토큰 누락·만료 (인터셉터가 발생) |
| `INTERNAL_SERVER_ERROR` | COMMON500 | 500 | 처리되지 않은 예외 |

## payment-service 고유 코드

| 에러 코드 | 코드 | 상태 | 메시지 |
| --- | --- | --- | --- |
| `AMOUNT_MISMATCH` | PAY400 | 400 | 요청된 결제 금액이 실제 주문 금액과 일치하지 않습니다. |
| `PAYMENT_REQUIRED` | PAY402 | 402 | 결제 잔액이 부족하거나 한도를 초과했습니다. |
| `PAYMENT_NOT_FOUND` | PAY404 | 404 | 존재하지 않는 결제 내역입니다. |
| `DUPLICATE_PAYMENT_REQUEST` | PAY409 | 409 | 동일한 결제 요청이 처리 중이거나 이미 완료되었습니다. |
| `INVALID_PAYMENT_STATUS` | PAY400 | 400 | 이미 취소되었거나 유효하지 않은 결제 건입니다. |

- **402**는 공통 코드에 없다. PG가 잔액 부족·한도 초과를 돌려준 경우이며, 사용자가 결제 수단을
  바꿔 재시도해야 하는 상황이라 400과 구분한다.
- **`AMOUNT_MISMATCH`를 따로 두는 이유**는 금액 위변조가 검증 실패 중에서도 별도로 추적·경보해야
  하는 사건이기 때문이다. 일반 입력 오류와 같은 코드로 묶으면 지표에서 묻힌다.

## 폐기한 코드

| 초안의 코드 | 처리 | 사유 |
| --- | --- | --- |
| `BAD_REQUEST` (금액 불일치) | → `AMOUNT_MISMATCH` | 무엇이 잘못됐는지 드러나지 않는다 |
| `FORBIDDEN` (타인 결제) | → `PAYMENT_NOT_FOUND` | 403은 리소스 존재를 노출한다 |
| `NOT_FOUND` | → `PAYMENT_NOT_FOUND` | 무엇을 못 찾았는지 드러나지 않는다 |
| `CONFLICT` | → `DUPLICATE_PAYMENT_REQUEST` | 명세 본문의 error 값과 일치시킨다 |
