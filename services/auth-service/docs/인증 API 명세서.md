| 도메인   | METHOD | bearer | PATH                                   | 기능상세             | Auth        | 소유권확인 |
| ----- | ------ | ------ | -------------------------------------- | ---------------- | ----------- | ----- |
| 인증/인가 | POST   | X      | /api/v1/auth/refresh                   | access token 재발급 | Public      | 불필요   |
| 인증/인가 | POST   | X      | /api/v1/auth/admin/login               | 백오피스 관리자 로그인     | Public      | 불필요   |
| 인증/인가 | POST   | O      | /api/v1/auth/logout                    | 로그아웃 처리          | User, Admin | 불필요   |
| 인증/인가 | POST   | X      | /api/v1/auth/oauth/{provider}          | 소셜 로그인 및 회원가입    | Public      | 불필요   |
| 인증/인가 | GET    | X      | /api/v1/auth/oauth/{provider}/callback | 소셜 로그인 콜백처리      | Public      | 불필요   |
| 인증/인가 | GET    | X      | /.well-known/jwks.json                 | 토큰 검증용 공개키 배포     | Public      | 불필요   |

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
	  "accessToken": "eyJhbGciOiJFUzI1NiIsInR...",
    "expiresIn": 1800,
    "userId": 1001
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
 "redirectUri": "https://cloudyim.store/oauth/callback",
 "returnTo": "/checkout"
}
```

|필드|필수|타입|설명|
|---|---|---|---|
|redirectUri|O|String|콜백을 받을 주소. **허용 목록에 있는 값만 받는다.** 임의 값을 허용하면 공격자가 인가 코드를 자기 서버로 받아갈 수 있다|
|returnTo|X|String|로그인 후 되돌아갈 **내부 경로**. 생략하면 기본 경로로 보낸다|

`returnTo`는 검증에 실패하면 **값을 버리고 기본 경로로 보낸다**(요청 자체는 실패하지 않는다).

- `/`로 시작하는 절대 경로만 허용한다
- 퍼센트 디코딩 후 512자를 넘지 않아야 한다
- 외부로 나가는 형태(`//host`, 스킴 포함)는 거부한다 — 열린 리다이렉트 차단

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

---

## 토큰 검증용 공개키 배포
## 🔹 Request

**Headers**

|이름|필수|설명|
|---|---|---|
|—|—|인증이 필요 없다. 공개키는 비밀이 아니다|

---

## 🔹 Response

**Headers**

```json
Cache-Control: max-age=300, public
```

**성공 (200 OK)**

---

```json
{
  "keys": [
    {
      "kty": "EC",
      "crv": "P-256",
      "kid": "3b3f3cc7-d171-477c-ae91-f31983f009f6",
      "x": "RMtP5NUS1MoVA4Gku6uW8ibmVHmBIOrGOD8iK4o4Q6c",
      "y": "nvlRO9FIMvjMb7a1PNQ3PpNGT5cn3hsmLiHIPwv7Dtc",
      "alg": "ES256"
    }
  ]
}
```

|필드|타입|설명|
|---|---|---|
|kty|String|키 종류. `EC` 고정|
|crv|String|곡선. `P-256` 고정|
|kid|String|키 식별자. JWT 헤더의 `kid`와 대조해 검증 키를 고른다|
|x, y|String|공개키 좌표(Base64url)|
|alg|String|서명 알고리즘. `ES256` 고정|

**이 응답만 `ApiResponse`로 감싸지 않는다.** JWKS는 RFC 7517이 정한 표준 형식이라
표준 라이브러리(Nimbus 등)가 그대로 파싱한다. 감싸면 해석하지 못한다.

**개인키는 포함되지 않는다.** 공개 파라미터만 직렬화하며, 운영에서는 개인키가 KMS 안에
있어 프로세스가 들고 있지도 않다(인증인가_설계서 1.3.2).

**검증하는 쪽이 지켜야 할 것**

- 캐시는 5분이다. 키 회전(6개월)에 비해 짧게 잡아 회전이 빠르게 전파되도록 했다
- 회전 중에는 구 키가 함께 실린다. `kid`로 골라야 하며, 키가 하나라고 가정하면 안 된다
- 클러스터 안에서는 인그레스를 거치지 않고 내부 주소로 조회하는 것을 권한다

**실패**

___

조회 자체는 실패하지 않는다. 서비스가 떠 있으면 항상 200이다.
