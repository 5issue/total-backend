
| 도메인 | METHOD | bearer | PATH                                            | 기능상세                   | Auth        | 소유권확인 |
| --- | ------ | ------ | ----------------------------------------------- | ---------------------- | ----------- | ----- |
| 회원  | GET    | O      | /api/v1/users/me/profile                        | 기본 주문자 정보 및 배송 요청사항 조회 | User        | 불필요   |
| 회원  | GET    | O      | /api/v1/users/me/addresses                      | 배송지 목록 조회              | User        | 불필요   |
| 회원  | POST   | O      | /api/v1/users/me/addresses                      | 신규 배송지 등록              | User        | 불필요  |
| 회원  | PATCH  | O      | /api/v1/users/me/addresses/{address_id}/default | 특정 배송지를 기본 배송지로 설정     | User        | 필요    |
| 회원  | POST   | X      | /internal/v1/users/sync-profile                 | 로그인 후 회원 프로필 생성        | User, Admin | 불필요   |


## 기본 주문자 정보 및 배송 요청사항 조회

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "주문 프로필 정보가 조회되었습니다.",
  "data": {
	  "name":"홍길동",
    "defaultAddress": {
      "addressId": 105,
      "addressName": "우리집",
      "recipientName": "홍길동",
      "zipCode": "06234",
      "address": "서울시 강남구 테헤란로 123",
      "addressDetail": "101동 202호",
      "accessMethod": "공동현관 비밀번호 (1234#)"
    }
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
  "message": "인증이 필요합니다.",
  "data": null,
  "error": "UNAUTHORIZED",
  "timestamp": "2026-04-24T12:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|401|`UNAUTHORIZED`|로그인 세션 만료 및 토큰 누락|“인증이 필요합니다.”|

## 배송지 목록 조회
## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "배송지 목록 조회가 완료되었습니다.",
  "data": {
	  "addresses": [
      {
        "addressId": 105,
        "addressName": "우리집",
        "recipientName": "홍길동",
        "phone": "010-1234-5678",
        "zipCode": "06234",
        "address": "서울시 강남구 테헤란로 123",
        "addressDetail": "101동 202호",
        "isDefault": true,
        "accessMethod": "공동현관 비밀번호 (1234#)"
      },
      {
        "addressId": 120,
        "addressName": "회사",
        "recipientName": "홍길동",
        "phone": "010-1234-5678",
        "zipCode": "06234",
        "address": "서울시 강남구 테헤란로 123",
        "addressDetail": "101동 201호",
        "isDefault": false,
        "accessMethod": "공동현관 비밀번호 (1234#)"
      }
    ]
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
  "message": "인증이 필요합니다.",
  "data": null,
  "error": "UNAUTHORIZED",
  "timestamp": "2026-04-24T12:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|401|`UNAUTHORIZED`|인증 토큰 누락/만료|“인증이 필요합니다.”|

## 신규 배송지 등록
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|Y|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|

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
	"addressName": "집",
	"recipientName": "홍길동",
	"phone": "010-0000-0000",
	"zipCode": "00000",
	"address": "경기도...",
	"addressDetail": "101호",
	"isDefault": true,
	"accessMethod": "공동현관 비밀번호 (1234#)"
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|addressName|String|Y|배송지 이름 (최대 50자)|
|recipientName|String|Y|수취인 이름 (최대 50자)|
|phone|String|Y|수취인 연락처. 숫자·하이픈 9~20자 (유선전화 허용)|
|zipCode|String|Y|우편번호. 5자리 숫자|
|address|String|Y|주소 (최대 255자)|
|addressDetail|String|N|주소상세 (최대 255자)|
|isDefault|Boolean|N|기본 배송지 여부. 생략 시 `false`|
|accessMethod|String|N|출입정보 (최대 255자)|

> **첫 배송지는 `isDefault`와 무관하게 기본 배송지가 된다.** 그렇지 않으면 주문 프로필의
> `defaultAddress`가 계속 `null`로 남는다.
>
> `isDefault`를 `true`로 보내면 기존 기본 배송지는 자동 해제된다. 기본 배송지는 회원당 한 건이다.

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "신규 배송지가 등록되었습니다.",
  "data": {
	  "addressId": 110,
	  "success": true
  },
  "error": null,
  "timestamp": "2026-04-24T10:00:00Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "addressName: 배송지 이름은 필수입니다.",
  "data": null,
  "error": "INVALID_INPUT_VALUE",
  "timestamp": "2026-04-24T12:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`INVALID_INPUT_VALUE`|필수 입력값 누락·형식 오류|검증 실패 필드와 사유 (공통 핸들러가 생성)|
|401|`UNAUTHORIZED`|인증 실패|“인증이 필요합니다.”|

## 특정 배송지를 기본 배송지로 설정
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|Y|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|address_id|Long|Y|기본 배송지로 설정할 배송지 ID|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|||||해당없음|

---

## 🔹 Body

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "기본 배송지가 변경되었습니다.",
  "data": {
	  "addressId": 110,
	  "isDefault": true
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
  "message": "존재하지 않는 배송지입니다.",
  "data": null,
  "error": "ADDRESS_NOT_FOUND",
  "timestamp": "2026-04-24T12:00:00Z"
}
```

| **HTTP 상태 코드** | **에러 코드**            | **상황**                                    | **응답 메시지**        |
| -------------- | -------------------- | ----------------------------------------- | ----------------- |
| 401            | `UNAUTHORIZED`       | 토큰 누락·만료                                  | “인증이 필요합니다.”      |
| 404            | `ADDRESS_NOT_FOUND`  | 존재하지 않는 배송지, **또는 타인 소유 배송지**             | “존재하지 않는 배송지입니다.” |

> **타인 소유 배송지에 403을 주지 않는 이유**: 403은 "그 ID는 실재하지만 네 것이 아니다"를 알려주는 셈이라,
> ID를 훑어 남의 배송지 존재 여부를 파악할 수 있다. 없는 것과 남의 것을 **같은 404**로 응답해 구분되지 않게 한다
> (시큐어코딩가이드 BE-06·BE-17).

## 로그인 후 회원 프로필 생성
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|N|`Bearer {Access Token}`|
|Content-Type|Y|`application/json`|

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
  "provider": "KAKAO",
  "providerId": "social_provider_id"
}
```

|필드|타입|설명|
|---|---|---|
|provider|String|소셜 제공자|
|providerId|String|소셜 서버로부터 획득한 고유 식별자|

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "회원 프로필 동기화가 완료되었습니다.",
  "data": {
    "userId": 10023,
    "provider": "KAKAO",
    "providerId": "1234567890",
    "role": "USER",
    "status": "ACTIVE",
    "isNewUser": true
  },
  "error": null,
  "timestamp": "2026-09-03T12:22:08Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "providerId: 소셜 식별자는 필수입니다.",
  "data": null,
  "error": "INVALID_INPUT_VALUE",
  "timestamp": "2026-09-03T12:22:08Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`INVALID_INPUT_VALUE`|`provider`·`providerId` 누락, 지원하지 않는 provider, 본문 형식 오류|검증 실패 사유 (공통 핸들러가 생성)|
|500|`INTERNAL_SERVER_ERROR`|서버 에러 발생|“서버 내부 오류가 발생했습니다.”|

> **403은 두지 않는다.** 이 엔드포인트는 사용자 토큰 없이 호출되며 접근 통제는
> NetworkPolicy가 담당한다(설계서 3.2·3.3). 애플리케이션이 IP/팟을 판별하지 않으므로
> 403을 응답할 근거가 없다. 인그레스에서 `/internal/**`을 차단하는 것으로 대신한다.
>
> **503은 현재 미구현이다.** DB 연동 장애는 지금 500으로 나간다. 별도 코드가 필요하면
> `DataAccessResourceFailureException` 핸들러를 `common`에 추가해야 하며, 전 서비스에
> 영향이 있으므로 팀 합의가 필요하다.

---

# 부록. 에러 코드 (컨벤션 정렬안)

## 규칙

`common`의 `ErrorCode` 인터페이스(`getStatus`/`getCode`/`getMessage`)를 구현한다.
`GlobalErrorCode`로 표현되는 것은 **재정의하지 않고 그대로 쓰고**, 도메인 고유한 것만
`UserErrorCode`에 정의한다(auth-service의 `AuthErrorCode`와 동일한 방식).
코드 문자열은 `<도메인약어><HTTP상태>` 형식이다 — `COMMON400`, `AUTH423`, `USER404`.

## 공통 코드 재사용분

| 에러 코드 | 코드 | 상태 | 사용처 |
| --- | --- | --- | --- |
| `INVALID_INPUT_VALUE` | COMMON400 | 400 | 배송지 등록 검증 실패, sync-profile 본문 오류 |
| `UNAUTHORIZED` | COMMON401 | 401 | 토큰 누락·만료 (인터셉터가 발생) |
| `INTERNAL_SERVER_ERROR` | COMMON500 | 500 | 처리되지 않은 예외 |

400·401·500은 **컨트롤러가 직접 다루지 않는다.** 각각 `MethodArgumentNotValidException`
핸들러, `AuthenticationInterceptor`, 최종 핸들러가 `GlobalExceptionHandler`에서 처리한다.

## user-service 고유 코드

| 에러 코드 | 코드 | 상태 | 메시지 |
| --- | --- | --- | --- |
| `ADDRESS_NOT_FOUND` | USER404 | 404 | 존재하지 않는 배송지입니다. |

```java
package com.kurly.user.exception;

/**
 * user-service 전용 에러 코드. 공통 {@code GlobalErrorCode}로 표현되지 않는 것만 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    /**
     * 배송지를 찾지 못함. 타인 소유 배송지도 이 코드로 응답해
     * ID 열거로 남의 배송지 존재 여부를 알아낼 수 없게 한다.
     * 공통 RESOURCE_NOT_FOUND 대신 두는 이유는 메시지를 배송지 문맥으로 특정하기 위함이다.
     */
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 배송지입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

## 폐기한 코드

| 명세 초안의 코드 | 처리 | 사유 |
| --- | --- | --- |
| `MISSED_ESSENTIAL_FIELD` | → `INVALID_INPUT_VALUE` | 공통 검증 핸들러가 이미 이 코드로 응답한다. 별도 코드를 두면 같은 상황에 두 코드가 나간다 |
| `BAD_REQUEST` | → `INVALID_INPUT_VALUE` | 위와 동일 |
| `NOT_FOUND` | → `ADDRESS_NOT_FOUND` | 무엇을 못 찾았는지 드러나지 않는다 |
| `FORBIDDEN` (타인 배송지) | → `ADDRESS_NOT_FOUND` | 리소스 존재 여부 노출 |
| `FORBIDDEN` (sync-profile) | 삭제 | 애플리케이션이 호출자 IP/팟을 판별하지 않는다 |
| `SERVICE_UNAVAILABLE` | 보류 | 미구현. 도입하려면 `common` 핸들러 추가가 필요해 팀 합의 대상 |
