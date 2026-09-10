
| 도메인 | METHOD | bearer | PATH                                            | 기능상세                   | Auth        | 소유권확인 |
| --- | ------ | ------ | ----------------------------------------------- | ---------------------- | ----------- | ----- |
| 회원  | GET    | O      | /api/v1/users/me/profile                        | 기본 주문자 정보 및 배송 요청사항 조회 | User        | 불필요   |
| 회원  | GET    | O      | /api/v1/users/me/addresses                      | 배송지 목록 조회              | User        | 불필요   |
| 회원  | POST   | O      | /api/v1/users/me/addresses                      | 신규 배송지 등록              | User        | 불필요  |
| 회원  | PATCH  | O      | /api/v1/users/me/addresses/{address_id}/default | 특정 배송지를 기본 배송지로 설정     | User        | 필요    |
| 회원  | POST   | X      | /internal/v1/users/sync-profile                 | 로그인 후 회원 프로필 생성        | User, Admin | 불필요   |





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
  "message": "필수 파라미터가 누락되었습니다.",
  "data": null,
  "error": "BAD_REQUEST",
  "timestamp": "2026-09-03T12:22:08Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`BAD_REQUEST`|입력값 누락(`provider` 또는 `provider_id` 누락)|“필수 파라미터가 누락되었습니다.”|
|403|`FORBIDDEN`|비정상적인 IP/팟 접근 시도|“접근 권한이 없습니다.”|
|500|`INTERNAL_SERVER_ERROR`|서버 에러 발생|“서버 내부 오류가 발생했습니다.”|
|503|`SERVICE_UNAVAILABLE`|서비스(User DB) 연동 장애 타임아웃|“시스템 점검 또는 지연 중입니다.”|
