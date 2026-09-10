| 도메인   | METHOD | bearer | PATH                                   | 기능상세             | Auth        | 소유권확인 |
| ----- | ------ | ------ | -------------------------------------- | ---------------- | ----------- | ----- |
| 인증/인가 | POST   | X      | /api/v1/auth/refresh                   | access token 재발급 | Public      | 불필요   |
| 인증/인가 | POST   | X      | /api/v1/auth/admin/login               | 백오피스 관리자 로그인     | Public      | 불필요   |
| 인증/인가 | POST   | O      | /api/v1/auth/logout                    | 로그아웃 처리          | User, Admin | 불필요   |
| 인증/인가 | POST   | X      | /api/v1/auth/oauth/{provider}          | 소셜 로그인 및 회원가입    | Public      | 불필요   |
| 인증/인가 | GET    | X      | /api/v1/auth/oauth/{provider}/callback | 소셜 로그인 콜백처리      | Public      | 불필요   |

## access token 재발급
## 🔹 Response

**Headers**

```json
Set-Cookie: refresh_token={New_JWT}; Path=/api/v1/auth/refresh; HttpOnly;
Secure; SameSite=Strict; Max-Age=1209600
```

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "토큰이 성공적으로 재발급되었습니다.",
  "data": {
	  "accessToken": "eyJhbGciOiJIUzI1NiIsInR...",
    "expiresIn": 1800
  },
  "error": null,
  "timestamp": "2026-08-23T10:00:00Z"
}
```

**실패**
___
```json
{
  "status": "ERROR",
  "message": "유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.",
  "data": null,
  "error": "UNAUTHORIZED",
  "timestamp": "2026-04-24T12:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|401|`UNAUTHORIZED`|토큰 만료, 위변조, 또는 DB상 `is_revoked = true` 상태|"유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.”|

## 백오피스 관리자 로그인
## 🔹 Response

**Header**

```json
Set-Cookie: refresh_token=a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d;
Path=/api/v1/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=1209600
```

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "관리자로그인이 완료되었습니다.",
  "data": {
	  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
	  "expiresIn": 1800,
    "admin": {
      "adminId": 1001,
      "role": "ADMIN"
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
  "message": "비활성화된 관리자 계정입니다.",
  "data": null,
  "error": "DEACTIVED_ACCOUNT",
  "timestamp": "2026-08-23T12:00:00Z"
}
```

**실패 — 계정 잠금**

```json
{
  "status": "ERROR",
  "message": "연속적인 비밀번호 오류로 요청이 거부 되었습니다. 10분 후 다시 시도해주세요.",
  "data": null,
  "error": "ACCOUNT_LOCKED",
  "timestamp": "2026-08-23T12:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|401|`UNAUTHORIZED`|아이디/비밀번호 불일치|“아이디 또는 비밀번호가 일치하지 않습니다.”|
|403|`FORBIDDEN`|퇴사/제한 처리된 계정의 접근 시도|“비활성화된 관리자 계정입니다.”|
|423|`ACCOUNT_LOCKED`|연속 5회 인증 실패로 일시 잠긴 계정. 10분 경과 시 자동 해제|“연속적인 비밀번호 오류로 요청이 거부 되었습니다. 10분 후 다시 시도해주세요.”|

## 로그아웃 처리
## 🔹 Response

**Headers**

```json
Set-Cookie: refresh_token=; Path=/api/v1/auth/refresh; HttpOnly; Secure;
SameSite=Strict; Max-Age=0
```

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "성공적으로 로그아웃 되었습니다.",
  "data": {},
  "error": null,
  "timestamp": "2026-04-24T10:00:00Z"
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

| **HTTP 상태 코드** | **에러 코드**      | **상황**                           | **응답 메시지**   |
| -------------- | -------------- | -------------------------------- | ------------ |
| 401            | `UNAUTHORIZED` | 이미 로그아웃 하여 세션정보가 존재하지 않는 로그아웃 시도 | “인증이 필요합니다.” |

## 소셜 로그인 및 회원가입
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|Authorization|N|`Bearer {Access Token}`|
|Content-Type|N|`application/json`|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|provider|-|-|소셜 서비스 제공자|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|-|-|-|-|해당없음|

---

## 🔹 Body

```json
{
 "redirectUri": "https..."
}
```

---

## 🔹 Response

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "소셜 로그인 URL",
  "data": {
	  "loginUrl": "<https://kauth.kakao.com/oauth/authorize?client_id=TEST&redirect_uri=...&response_type=code&state=ABC123>",
    "provider": "KAKAO"
  },
  "error": null,
  "timestamp": "2026-04-26T10:00:00Z"
}
```

**실패**

---

```json
{
  "status": "ERROR",
  "message": "지원하지 않는 서비스 제공자입니다.",
  "data": null,
  "error": "Invalid Provider",
  "timestamp": "2026-04-26T00:00:00Z"
}
```

| **HTTP 상태 코드** | **에러 코드**     | **상황**                | **응답 메시지**            |
| -------------- | ------------- | --------------------- | --------------------- |
| 400            | `BAD_REQUEST` | 카카오/네이버 이외의 소셜 제공자 요청 | “지원하지 않는 서비스 제공자입니다.” |

## 소셜 로그인 콜백처리
## 🔹 Request

**Headers**

|이름| 필수 |설명|
|---|----|---|
|Authorization| N  |`Bearer {Access Token}`|
|Content-Type| N  |`application/json`|

**Path Parameters**

|이름|타입|필수|설명|
|---|---|---|---|
|provider|-|-|소셜 서비스 제공자|

**Query Parameters**

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|code|String| | |소셜 제공자 코드|
|state|String| | |CSRF방지용 보안 코드|

---

## 🔹 Body

```json

```


---

## 🔹 Response

**Headers**

```json
Set-Cookie: refresh_token=d2b4c5e6-7a8b-9c0d-1e2f-3a4b5c6d7e8f;
Path=/api/v1/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=1209600
```

**성공 (200 OK/ 201 Created)**

---

```json
{
  "status": "SUCCESS",
  "message": "소셜 로그인이 완료되었습니다.",
  "data": {
	  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
	  "expiresIn": 1800,
	  "user": {
		  "userId": 1234
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
  "message": "유효하지 않은 인가코드입니다.",
  "data": null,
  "error": "INVALID_AUTH_CODE",
  "timestamp": "2026-08-23T10:00:00Z"
}
```

|**HTTP 상태 코드**|**에러 코드**|**상황**|**응답 메시지**|
|---|---|---|---|
|400|`BAD_REQUEST`|필수 파라미터 누락 또는 잘못된 제공자 입력|“잘못된 요청입니다.”|
|401|`UNAUTHORIZED`|인가 코드 위변조 또는 만료|“유효하지 않은 인가코드입니다.”|
|422|`UNPROCESSABLE_ENTITY`|카카오/네이버 필수 동의 항목 누락|“필수 정보 제공에 동의해야 합니다.”|