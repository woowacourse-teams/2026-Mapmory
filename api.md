# Mapmory API 명세

> 기준일: 2026-10-22 · 범위: 인증, 회원, 지역 선택, 장소 검색, 지도 마킹, 여행 기록, 여행 통계, 이미지 첨부, 사용자 생성 태그, 관리자, 서비스 의견, 출시 알림, 그룹 공유(구현 전 설계)

이 문서는 Mapmory API의 기준 계약이다. API 목록의 `구현 전 설계` 항목은 구현에 앞서 합의한 목표 계약이며, 구현이 끝나면 `구현됨`으로 상태를 변경한다.

## 1. 기본 정보

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 요청·응답 형식 | `application/json` |
| 오류 응답 형식 | `application/problem+json` |
| 날짜 형식 | `YYYY-MM-DD` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 페이지네이션 | `page` 기본 0, `size` 기본 20·최대 100 |

`POST /auth/**`, `POST /waitlist`, `/health`와 `/admin/**`를 제외한 앱 API는 유효한 Access Token이 필요하다. `/auth/login/kakao`, `/auth/login/google`, `/auth/login/apple`은 인증 없이 호출하지만, 게스트가 계정을 연결할 때는 게스트 Access Token을 함께 보낸다. 관리자 API(`/admin/**`)는 Access Token 대신 관리자 세션과 ADMIN 권한이 필요하며, `/admin/auth/csrf`와 `/admin/auth/login`만 세션 없이 호출한다. 여행 기록과 태그는 비공개이며 소유자 본인만 접근할 수 있다. 예외로 그룹 앨범에 담긴 기록은 그 그룹 멤버가 앨범 API로 읽을 수 있다. 다른 회원의 리소스 ID를 요청해도 존재 여부를 숨기기 위해 `404`를 반환한다.

### API 목록

| 상태 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 구현됨 | `POST` | `/auth/login/kakao` | 카카오 로그인 |
| 구현됨 | `POST` | `/auth/login/google` | 구글 로그인 |
| 구현됨 | `POST` | `/auth/login/apple` | 애플 로그인 |
| 구현됨 | `POST` | `/auth/login/guest` | 게스트 로그인 |
| 구현됨 | `POST` | `/auth/token/refresh` | Access·Refresh Token 회전 재발급 |
| 구현됨 | `POST` | `/auth/logout` | Refresh Token 폐기 |
| 구현됨 | `GET` | `/members/me` | 내 회원 정보 조회 |
| 구현됨 | `PATCH` | `/members/me/nickname` | 내 닉네임 변경 |
| 구현됨 | `DELETE` | `/members/me` | 회원 탈퇴 |
| 구현됨 | `GET` | `/admin/auth/csrf` | 관리자 CSRF 토큰 발급 |
| 구현됨 | `POST` | `/admin/auth/login` | 관리자 로그인(세션 발급) |
| 구현됨 | `GET` | `/admin/auth/me` | 현재 관리자 조회 |
| 구현됨 | `POST` | `/admin/auth/logout` | 관리자 로그아웃 |
| 구현됨 | `GET` | `/admin/dashboard` | 운영 현황 집계 조회 |
| 구현됨 | `GET` | `/admin/members` | 회원 목록 조회 |
| 구현됨 | `GET` | `/admin/members/{memberId}` | 회원 상세·세션 상태 조회 |
| 구현됨 | `POST` | `/service-feedback` | 앱 서비스 의견 제출 |
| 구현됨 | `GET` | `/admin/service-feedback` | 서비스 의견 목록 조회 |
| 구현됨 | `GET` | `/admin/service-feedback/{feedbackId}` | 서비스 의견 상세 조회 |
| 구현됨 | `PATCH` | `/admin/service-feedback/{feedbackId}/status` | 내부 처리 상태 변경 |
| 구현됨 | `GET` | `/places/search` | 장소 검색(Google Places Autocomplete) |
| 구현됨 | `GET` | `/places/{placeId}` | 장소 선택·추천 지역 조회 |
| 구현됨 | `POST` | `/uploads/presigned-urls` | 이미지 업로드용 Presigned URL 발급 |
| 구현됨 | `POST` | `/travel-records` | 여행 기록 생성 |
| 구현됨 | `GET` | `/travel-records` | 내 여행 기록 목록 조회 |
| 구현됨 | `GET` | `/travel-records/{travelRecordId}` | 내 여행 기록 상세 조회 |
| 구현됨 | `PUT` | `/travel-records/{travelRecordId}` | 내 여행 기록 전체 수정 |
| 구현됨 | `DELETE` | `/travel-records/{travelRecordId}` | 내 여행 기록 삭제 |
| 구현됨 | `GET` | `/travel-records/statistics` | 내 전체 여행 통계 조회 |
| 구현됨 | `GET` | `/travel-records/map-summary/regions/roots` | 루트 Region별 지도 색칠 정보 조회 |
| 구현됨 | `GET` | `/travel-records/map-summary/regions/{regionId}/children` | 직속 하위 Region별 지도 색칠 정보 조회 |
| 구현됨 | `POST` | `/tags` | 내 태그 생성 |
| 구현됨 | `GET` | `/tags` | 내 태그 목록 조회 |
| 구현됨 | `PATCH` | `/tags/{tagId}` | 내 태그 이름 수정 |
| 구현됨 | `DELETE` | `/tags/{tagId}` | 내 태그 삭제 |
| 구현됨 | `POST` | `/waitlist` | 출시 알림 신청 |
| 구현됨 | `POST` | `/groups` | 그룹 만들기 |
| 구현됨 | `GET` | `/groups` | 내 그룹 목록 |
| 구현됨 | `GET` | `/groups/{groupId}` | 그룹 조회(멤버 닉네임) |
| 구현됨 | `PATCH` | `/groups/{groupId}` | 그룹 이름 바꾸기 |
| 구현됨 | `POST` | `/groups/{groupId}/invitations` | 초대 링크·코드 만들기 |
| 구현됨 | `GET` | `/invitations/{tokenOrCode}` | 초대 미리보기 |
| 구현됨 | `POST` | `/invitations/{tokenOrCode}/accept` | 초대 수락 |
| 구현 전 설계 | `DELETE` | `/groups/{groupId}/members/me` | 그룹 나가기 |
| 구현 전 설계 | `POST` | `/groups/{groupId}/albums` | 내 기록으로 앨범 열기 |
| 구현 전 설계 | `POST` | `/groups/{groupId}/albums/{albumId}/entries` | 앨범에 내 기록 붙이기 |
| 구현 전 설계 | `DELETE` | `/groups/{groupId}/albums/{albumId}/entries/{travelRecordId}` | 앨범에서 내 기록 내리기 |
| 구현 전 설계 | `PATCH` | `/groups/{groupId}/albums/{albumId}` | 앨범 이름·지역 바꾸기 |
| 구현 전 설계 | `GET` | `/groups/{groupId}/albums` | 앨범 목록 |
| 구현 전 설계 | `GET` | `/groups/{groupId}/albums/{albumId}` | 앨범 상세(기록 하나처럼) |
| 구현 전 설계 | `PUT` | `/push-tokens` | 알림용 기기 토큰 등록 |
| 구현 전 설계 | `DELETE` | `/push-tokens` | 알림용 기기 토큰 삭제 |

### 공통 응답

성공 응답은 `data`로 감싼다.

```json
{
  "data": {}
}
```

오류 응답은 RFC 9457 Problem Details를 사용한다.

```json
{
  "title": "요청 값이 올바르지 않습니다.",
  "status": 400,
  "detail": "1개의 필드가 유효하지 않습니다.",
  "instance": "/api/v1/travel-records",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "countryCode",
      "detail": "대문자 2자의 ISO-2 형식이어야 합니다."
    }
  ]
}
```

| 상태 | 의미 |
| --- | --- |
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 형식·유효성·업무 규칙 오류 |
| `401 Unauthorized` | 인증 실패 또는 토큰 만료 |
| `403 Forbidden` | 인증됐지만 접근 권한 없음 |
| `404 Not Found` | 리소스 없음 또는 타인 기록 |
| `409 Conflict` | 중복 또는 리소스 상태 충돌 |
| `429 Too Many Requests` | 요청 한도 초과 |

## 2. Auth API

로그인 방식은 네 가지다. 어느 쪽으로 로그인하든 **발급되는 토큰과 이후 사용 방법은 동일하다.**

| 방식 | 용도 | 요청 본문 |
| --- | --- | --- |
| 카카오 로그인 | 계정을 연결한 사용자 | 카카오 ID Token |
| 구글 로그인 | 계정을 연결한 사용자 | 구글 ID Token |
| 애플 로그인 | 계정을 연결한 사용자 | 애플 identity token |
| 게스트 로그인 | 로그인 없이 먼저 사용해보는 사용자 | 없음 |

게스트로 사용하다가 카카오·구글·애플 로그인을 하면 **같은 회원으로 승격**되어 그동안 남긴 기록이 그대로 이어진다.
자세한 조건은 아래 카카오 로그인을 참고한다.

### 토큰 정책

로그인 응답으로 Access Token과 Refresh Token을 함께 받는다. 클라이언트는 둘 다 안전하게 저장한다.

| 토큰 | 만료 | 비고 |
| --- | --- | --- |
| Access Token | 30분 | 보호 API 호출에 사용. 서버에 저장하지 않는다 |
| Refresh Token (회원) | 14일 | 재발급 시 회전한다 |
| Refresh Token (게스트) | 365일 | 게스트는 재로그인 수단이 없어 만료 시 복구가 불가능하다 |

Refresh Token은 **재발급할 때마다 만료가 갱신된다.** 따라서 실제로 만료되는 경우는 그 기간 동안
앱을 한 번도 열지 않은 경우다. 앱 실행 시 재발급을 한 번 호출해두면 세션이 계속 유지된다.

재발급에 성공하면 이전 Refresh Token은 즉시 폐기된다. **응답으로 받은 새 Access·Refresh Token을
모두 저장해야 한다.** 이미 사용한 Refresh Token이 다시 오면 탈취로 간주해 해당 회원의 Refresh Token을
전부 폐기하므로, 재발급 요청이 동시에 두 번 나가지 않도록 해야 한다.

단, **게스트**가 재발급 응답을 받지 못해(앱 종료·네트워크 끊김) 이전 Refresh Token으로 다시 요청하면,
그 사이 새 토큰이 쓰이지 않았다면 경과 시간과 관계없이 정상 재발급한다. 게스트는 다시 로그인할 수단이 없기 때문이다.
소셜 회원은 같은 상황에서도 전부 폐기되므로 다시 로그인해야 한다. (ADR 0023)

보호 API에는 다음 헤더를 전달한다.

```http
Authorization: Bearer {accessToken}
```

### 카카오 로그인

`POST /api/v1/auth/login/kakao`

앱이 카카오 SDK로 받은 **ID Token**을 전달하면, 서버가 카카오 공개키로 서명과 `iss`·`aud`·`exp`를 검증한 뒤
Mapmory 토큰을 발급한다. Access Token이 아니라 ID Token을 보낸다. 카카오 콘솔에서 OpenID Connect가 켜져 있어야
SDK 로그인 결과에 ID Token이 들어온다. 닉네임 동의항목에 동의하면 ID Token의 `nickname`을 이름으로 쓴다.

```json
{
  "idToken": "kakao-id-token"
}
```

게스트로 사용 중이었다면 **게스트 Access Token을 헤더로 함께 보낸다.** 이 경우에만 승격이 일어난다.

```http
Authorization: Bearer {게스트 accessToken}
```

결과는 세 가지다.

| 상황 | 결과 | `isNewMember` | `guestAbandoned` |
| --- | --- | --- | --- |
| 게스트 토큰 있음 · 처음 보는 카카오 계정 | **승격**. 게스트로 남긴 기록이 이어진다 | `false` | `false` |
| 게스트 토큰 있음 · 이미 가입한 카카오 계정 | 기존 회원으로 로그인. **게스트 기록은 이어지지 않는다** | `false` | `true` |
| 게스트 토큰 없음 · 처음 보는 카카오 계정 | 신규 가입 | `true` | `false` |
| 게스트 토큰 없음 · 이미 가입한 카카오 계정 | 기존 회원으로 로그인 | `false` | `false` |

두 번째 경우 게스트는 **버려진 게스트**가 된다. 게스트의 Refresh Token은 폐기되어 그 세션으로는 더 이상 갱신할 수 없고,
남은 Access Token으로 회원 정보 API를 부르면 `401 INACTIVE_MEMBER`다. 게스트로 남긴 기록은 지우지 않고 남겨 둔다.

로그인 전에는 서버가 기존 계정이 있는지 알려줄 수 없다. **클라이언트는 로그인 뒤 `guestAbandoned`가 `true`이면
게스트로 남긴 기록이 이어지지 않았음을 안내한다.**

탈퇴했거나 이미 버려진 게스트의 Access Token을 실어 보내면 게스트가 없는 것으로 보고 위 표의 "게스트 토큰 없음"처럼 처리한다.

**게스트 Access Token이 만료됐으면 `401 EXPIRED_ACCESS_TOKEN`이다.** 토큰을 재발급한 뒤 같은 요청을 다시 보내면 승격된다.
만료된 토큰을 "게스트 토큰 없음"으로 처리하면 승격 대신 새 회원이 생기기 때문이다. 위조됐거나 형식이 잘못된 토큰은
재발급으로 복구할 수 없으므로 "게스트 토큰 없음"처럼 처리한다.

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "mapmory-access-token",
    "refreshToken": "mapmory-refresh-token",
    "isNewMember": true,
    "guestAbandoned": false
  }
}
```

### 구글 로그인

`POST /api/v1/auth/login/google`

앱이 구글 로그인(Android Credential Manager, iOS GoogleSignIn)으로 받은 **ID Token**을 전달하면,
서버가 구글 공개키로 서명과 `iss`·`aud`·`exp`를 검증한 뒤 Mapmory 토큰을 발급한다.
Access Token이 아니라 ID Token을 보낸다.

```json
{
  "idToken": "google-id-token"
}
```

게스트 승격 규칙, 결과 표, 응답 형식은 카카오 로그인과 같다. 게스트로 사용 중이었다면
게스트 Access Token을 `Authorization` 헤더로 함께 보낸다.

같은 사람이라도 카카오 회원과 구글 회원은 별개 회원이다. 계정 병합은 지원하지 않는다.

### 애플 로그인

`POST /api/v1/auth/login/apple`

앱이 Sign in with Apple(`ASAuthorizationAppleIDCredential`)로 받은 **identity token**을 전달하면,
서버가 애플 공개키로 서명과 `iss`·`aud`·`exp`·`sub`를 검증한 뒤 Mapmory 토큰을 발급한다.
`aud`는 iOS 앱 번들 ID여야 한다. authorization code가 아니라 identity token을 보낸다.

```json
{
  "identityToken": "apple-identity-token"
}
```

`identityToken`은 `credential.identityToken`을 UTF-8 문자열로 바꾼 값이다.
애플은 토큰에 이름을 넣지 않으므로 애플 회원은 기본 이름(예: `회원58213`)으로 시작한다.

게스트 승격 규칙, 결과 표, 응답 형식은 카카오 로그인과 같다. 같은 사람이라도 카카오·구글 회원과
애플 회원은 별개 회원이다.

### 게스트 로그인

`POST /api/v1/auth/login/guest`

로그인하지 않고 서비스를 사용하기 위한 회원을 만든다. **요청 본문이 없다.**
식별자는 서버가 발급하므로 클라이언트가 보낼 값은 없다.

호출할 때마다 새 회원이 생성된다. 클라이언트는 발급받은 토큰을 저장해 계속 사용해야 하며,
앱을 실행할 때마다 다시 호출하면 안 된다.

응답 형식은 카카오 로그인과 같고 `isNewMember`는 항상 `true`다.

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "mapmory-access-token",
    "refreshToken": "mapmory-refresh-token",
    "isNewMember": true,
    "guestAbandoned": false
  }
}
```

인증 없이 호출되는 경로이므로 같은 출처에서 반복 호출하면 `429 GUEST_LOGIN_RATE_LIMITED`로 거절한다.
정상 사용자가 걸리지 않을 만큼 한도를 넉넉히 두므로, 토큰을 저장해 재사용하면 마주칠 일이 없다.

게스트의 기록은 **그 기기에만 묶여 있다.** 앱을 지우거나 기기를 바꾸면 복구할 수 없으므로,
계정 연결을 유도하는 안내가 필요하다.

### 토큰 재발급

`POST /api/v1/auth/token/refresh`

새 Access·Refresh Token 쌍을 반환한다. 회전 규칙은 위 토큰 정책을 참고한다. 탈퇴한 회원은 재발급받을 수 없다(`INVALID_REFRESH_TOKEN`).

```json
{
  "refreshToken": "mapmory-refresh-token"
}
```

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "new-mapmory-access-token",
    "refreshToken": "new-mapmory-refresh-token"
  }
}
```

### 로그아웃

`POST /api/v1/auth/logout`

전달한 Refresh Token을 폐기한다. 그 토큰이 이미 재발급에 쓰였다면 그 재발급으로 발급된 Refresh Token까지 폐기한다.
앱은 마지막으로 받은 Refresh Token으로 로그아웃한다.
로그아웃 전에 진행 중인 재발급을 기다리거나 취소한다. 같은 토큰으로 로그아웃 뒤 재발급하면 재사용으로 판단돼
회원의 Refresh Token이 모두 폐기된다.

```json
{
  "refreshToken": "mapmory-refresh-token"
}
```

#### Response `204 No Content`

### Auth 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `401` | `INVALID_ACCESS_TOKEN` | Access Token이 유효하지 않음 |
| `401` | `EXPIRED_ACCESS_TOKEN` | Access Token이 만료됨 |
| `401` | `INVALID_KAKAO_TOKEN` | 카카오 ID Token이 유효하지 않음 (서명·발급자·대상 앱·만료). 서버 `KAKAO_CLIENT_IDS`에 앱 키가 없을 때도 해당 |
| `401` | `INVALID_GOOGLE_TOKEN` | 구글 ID Token이 유효하지 않음 (서명·발급자·대상 앱·만료). Android가 웹 클라이언트 ID가 아닌 Android 클라이언트 ID로 토큰을 받은 경우도 대상 앱 불일치로 여기에 해당 |
| `401` | `INVALID_APPLE_TOKEN` | 애플 identity token이 유효하지 않음 (서명·발급자·대상 앱·만료). 서버 `APPLE_CLIENT_IDS`에 번들 ID가 없을 때도 해당 |
| `401` | `INVALID_REFRESH_TOKEN` | Refresh Token이 유효하지 않거나 폐기됨. 탈퇴한 회원의 토큰도 해당 |
| `401` | `EXPIRED_REFRESH_TOKEN` | Refresh Token이 만료됨 |
| `403` | `ACCESS_DENIED` | 리소스 접근 권한 없음 |
| `429` | `GUEST_LOGIN_RATE_LIMITED` | 게스트 로그인 요청이 한도를 초과함 |
| `503` | `KAKAO_UNAVAILABLE` | 카카오 공개키를 받아오지 못해 검증할 수 없음 |
| `503` | `GOOGLE_UNAVAILABLE` | 구글 공개키를 받아오지 못해 검증할 수 없음 |
| `503` | `APPLE_UNAVAILABLE` | 애플 공개키를 받아오지 못해 검증할 수 없음 |

### 토큰 오류 처리

Access Token 오류와 Refresh Token 오류는 상태가 모두 `401`이지만 **나오는 곳과 `code`가 다르다.** 클라이언트는 `code`로 구분한다.

| 나오는 곳 | `code` | 조건 | 클라이언트 처리 |
| --- | --- | --- | --- |
| 보호 API 전체, 소셜 로그인 | `EXPIRED_ACCESS_TOKEN` | Access Token 만료 | **토큰 재발급 후 원래 요청을 한 번 재시도** |
| 보호 API 전체 | `INVALID_ACCESS_TOKEN` | 위조·형식 오류, **`Authorization` 헤더 없음** | 로그인 화면으로 |
| 회원 API (`/members/me`) | `INACTIVE_MEMBER` | 탈퇴했거나 버려진 게스트의 남은 Access Token | 로그인 화면으로 |
| `POST /auth/token/refresh` | `EXPIRED_REFRESH_TOKEN` | Refresh Token 만료 | 로그인 화면으로 |
| `POST /auth/token/refresh` | `INVALID_REFRESH_TOKEN` | 없는 토큰, 재사용 감지로 전부 폐기, 로그아웃한 토큰, 탈퇴·버려진 게스트 | 로그인 화면으로 |

```text
보호 API ─▶ 401 EXPIRED_ACCESS_TOKEN
            └▶ POST /auth/token/refresh
                 ├─ 200 ─▶ 새 Access·Refresh Token 모두 저장 ─▶ 원래 요청 재시도
                 └─ 401 (EXPIRED_REFRESH_TOKEN / INVALID_REFRESH_TOKEN) ─▶ 저장한 토큰 삭제, 로그인 화면
```

- **재발급은 `EXPIRED_ACCESS_TOKEN`일 때만 호출한다.** "401이면 재발급"으로 구현하면 재발급 API 자신의 401에서 다시 재발급을 불러 무한 반복된다. 재발급 API는 자동 재발급 인터셉터 대상에서 제외한다.
- `INVALID_REFRESH_TOKEN`은 이유를 나누지 않는다. 이유를 알려 주면 탈취한 쪽에 정보를 주게 되고, 어느 경우든 클라이언트가 할 일은 다시 로그인하는 것이다.
- 여러 요청이 동시에 `EXPIRED_ACCESS_TOKEN`을 받아도 **재발급은 한 번만** 호출하고(single-flight, 필수), 나머지 요청은 그 결과를 기다렸다가 재시도한다. 두 번 호출하면 두 번째가 재사용으로 판단되어 전부 폐기될 수 있다.
- `503`(`KAKAO_UNAVAILABLE` 등)과 `429`(`GUEST_LOGIN_RATE_LIMITED`)는 로그인 실패가 아니라 **잠시 후 다시 시도**로 처리한다. 저장한 토큰을 지우지 않는다.
- 오류를 기록하거나 문의를 받을 때 응답 헤더의 **`X-Request-Id`를 함께 남긴다.** 서버 로그를 같은 값으로 찾을 수 있다.
- 재발급 요청에 만료된 Access Token을 헤더로 함께 보내도 된다. 재발급은 인증 없이 호출하는 경로라 막히지 않는다.
- 게스트가 Refresh Token 오류를 받으면 다시 로그인할 수단이 없어 계정을 잃는다. 이때 새 게스트로 시작할지, 안내를 띄울지 화면에서 정해야 한다.

## 3. Upload API

이미지는 API 서버를 거치지 않고 S3에 직접 업로드한다. 서버는 UUID 기반 `objectKey`만 발급하며, DB에는 만료되는 Presigned URL이 아닌 `objectKey`를 저장한다.

`objectKey`의 형식은 서버가 정하며 환경에 따라 접두사가 달라질 수 있다. 클라이언트는 값을 해석하지 말고 받은 그대로 저장하고 전달한다.

### 이미지 업로드 URL 발급

`POST /api/v1/uploads/presigned-urls`

#### Request Body

```json
{
  "files": [
    {
      "fileName": "jeju-trip.jpg",
      "contentType": "image/jpeg",
      "fileSize": 3145728
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `files` | Object[] | 예 | 1개 이상 |
| `files[].fileName` | String | 예 | 로그·확장자 확인용 원본 파일명 |
| `files[].contentType` | String | 예 | 허용 이미지 MIME 타입 |
| `files[].fileSize` | Long | 예 | 바이트 단위 파일 크기 |

허용 형식은 `contentType`으로 정한다. `fileName`에 확장자가 있으면 그 형식의 확장자여야 하고, 없으면 확인하지 않는다. `objectKey`의 확장자는 `fileName`과 관계없이 형식마다 하나로 정해진다.

| `contentType` | `fileName` 확장자 | `objectKey` 확장자 |
| --- | --- | --- |
| `image/jpeg` | `jpg`, `jpeg`, `jpe` | `jpg` |
| `image/png` | `png` | `png` |
| `image/webp` | `webp` | `webp` |
| `image/heic` | `heic`, `heics`, `heif`, `heifs` | `heic` |

#### Response `200 OK`

```json
{
  "data": {
    "uploads": [
      {
        "objectKey": "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg",
        "presignedUrl": "https://...",
        "method": "PUT",
        "contentType": "image/jpeg",
        "expiresIn": 300
      }
    ]
  }
}
```

업로드 후 24시간 안에 여행 기록과 연결되지 않은 객체는 고아 객체로 정리한다.

## 4. Tag API

태그는 회원이 직접 만드는 비공개 리소스다. `연인`, `친구`, `가족`, `라멘맛집`처럼 자유롭게 만들 수 있으며 하나의 여행 기록에 여러 태그를 연결할 수 있다.

`#`은 UI 표현이므로 태그 이름에 저장하지 않는다. 서버는 이름의 앞뒤 공백과 연속 공백을 정리하고 Unicode NFC·소문자를 적용한 `normalizedName`을 중복 판단에 사용한다.

- 이름: 정규화 후 1~30자
- 회원당 최대 10개
- 기록당 최대 5개
- 같은 회원은 정규화 이름이 같은 태그를 중복 생성할 수 없음
- 다른 회원은 같은 이름의 태그를 만들 수 있음

회원당 10개와 기록당 5개는 MVP 사용성을 검증하기 위한 임시 제한이다. 사용자 테스트와 실제 태그 사용 분포를 확인한 뒤 유지·완화 여부를 다시 결정한다.

### 태그 생성

`POST /api/v1/tags`

```json
{
  "name": "라멘맛집"
}
```

#### Response `201 Created`

```json
{
  "data": {
    "id": 12,
    "name": "라멘맛집",
    "createdAt": "2026-08-20T15:30:00",
    "updatedAt": "2026-08-20T15:30:00"
  }
}
```

### 내 태그 목록 조회

`GET /api/v1/tags`

태그는 `createdAt ASC, id ASC`로 정렬한다. 연결된 기록이 없는 태그도 목록에 반환한다. 임시 회원당 제한이 10개이므로 MVP에서는 페이지네이션하지 않는다.

#### Response `200 OK`

```json
{
  "data": [
    {
      "id": 9,
      "name": "연인",
      "createdAt": "2026-08-19T12:00:00",
      "updatedAt": "2026-08-19T12:00:00"
    },
    {
      "id": 12,
      "name": "라멘맛집",
      "createdAt": "2026-08-20T15:30:00",
      "updatedAt": "2026-08-20T15:30:00"
    }
  ]
}
```

### 태그 이름 수정

`PATCH /api/v1/tags/{tagId}`

```json
{
  "name": "서울 라멘맛집"
}
```

성공 시 태그 생성 응답과 같은 객체를 `200 OK`로 반환한다. 이름 변경은 연결된 모든 여행 기록에 즉시 반영된다.

### 태그 삭제

`DELETE /api/v1/tags/{tagId}`

태그와 여행 기록의 연결만 삭제하며 여행 기록 자체는 유지한다.

#### Response `204 No Content`

### Tag 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 이름 형식 또는 길이가 유효하지 않음 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |
| `409` | `TAG_NAME_CONFLICT` | 현재 회원에게 같은 정규화 이름의 태그가 있음 |
| `409` | `TAG_LIMIT_EXCEEDED` | 임시 회원당 태그 10개 제한 초과 |

## 5. Travel Record API

### 지역 선택 규칙

클라이언트는 Region의 DB ID가 아닌 표준 코드 경로를 전달한다. 서버는 `parent_id` 관계로 경로를 검증한 뒤 최종 Region의 ID만 `travel_record.region_id`에 저장한다.

| 기록 대상 | 요청 값 | 저장되는 Region |
| --- | --- | --- |
| 해외 국가 | `countryCode` | `COUNTRY` |
| 대한민국 시·도 | `countryCode`, `provinceCode` | `PROVINCE` |
| 대한민국 시·군·구 | `countryCode`, `provinceCode`, `districtCode` | `DISTRICT` |

- `countryCode = KR`이면 `provinceCode`가 필수다. 대한민국 전체(국가 단위)에는 기록을 붙일 수 없다. 시·도 단위 기록은 통계의 `visitedKoreaDistrictCount`에 세지 않는다.
- 상위 코드 없이 하위 코드만 보내면 `INVALID_REGION_HIERARCHY`다.
- `countryCode`는 대문자 2자리이며, 하위 지역 코드는 입력하는 경우 공백 없이 20자 이하여야 한다.
- `provinceCode`는 선택 국가의 직접 자식 `PROVINCE`여야 한다.
- `districtCode`는 선택 시도의 직접 자식 `DISTRICT`여야 한다.
- 해외에서는 MVP 기준 국가 단위만 허용한다. 해외 국가에 하위 코드를 보내면 `REGION_NOT_FOUND`다.
- 지역 코드를 하나도 보내지 않고 `placeId`를 보내면 서버가 장소 주소로 지역을 추천해 저장한다. 자세한 흐름은 [9. 장소 검색 API](#9-장소-검색-api)를 따른다.
- 코드 접두사로 부모 지역을 추론하지 않는다.

### 여행 기록 생성

`POST /api/v1/travel-records`

#### Request Body — 대한민국 기록

```json
{
  "countryCode": "KR",
  "provinceCode": "49",
  "districtCode": "50110",
  "title": "비 오는 날의 제주시",
  "content": "골목을 걸으며 오래된 가게들을 기록했다.",
  "startDate": "2026-08-11",
  "endDate": null,
  "objectKeys": [
    "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg"
  ],
  "tagIds": [9, 12],
  "placeId": null
}
```

#### Request Body — 해외 국가 기록

```json
{
  "countryCode": "JP",
  "provinceCode": null,
  "districtCode": null,
  "title": "일본 여행",
  "content": "",
  "startDate": "2026-08-11",
  "endDate": null,
  "objectKeys": [],
  "tagIds": [8]
}
```

| 필드 | 타입 | 필수 | 제약조건 |
| --- | --- | --- | --- |
| `countryCode` | String | 조건부 | `placeId`가 없으면 필수. 대문자 2자리이며 존재하는 ISO 3166-1 alpha-2 코드 |
| `provinceCode` | String | 조건부 | `KR`이면 필수, 입력 시 공백 없이 최대 20자 |
| `districtCode` | String | 아니요 | 입력 시 `provinceCode` 필수, 공백 없이 최대 20자 |
| `title` | String | 예 | 공백이 아닌 문자를 포함해야 하며 최대 200자 |
| `content` | String | 아니요 | `null`, 빈 문자열·공백 허용 |
| `startDate` | LocalDate | 예 | `YYYY-MM-DD`, 오늘 또는 과거 |
| `endDate` | LocalDate | 아니요 | 오늘 또는 과거이며 시작일과 같거나 이후 |
| `objectKeys` | String[] | 아니요 | 업로드 완료된 Object Key 목록 |
| `tagIds` | Long[] | 아니요 | 빈 배열 허용, 최대 5개, 중복 불가, 모두 현재 회원 소유 |
| `placeId` | String | 아니요 | `GET /places/search` 후보의 장소 ID, 최대 255자. 지역 코드가 없으면 이 장소로 지역을 정함 |

`objectKeys`는 배열 순서대로 `record_media.sort_order`를 0부터 부여해 저장한다. 값이 없거나 `null`이면 미디어를 생성하지 않는다.

`content`가 없거나 `null`이면 서버는 빈 문자열로 정규화해 저장한다.

여행 날짜는 `Asia/Seoul`의 오늘을 기준으로 검증한다. 시작일과 종료일 모두 미래일 수 없으며 오늘은 허용한다. 종료일을 입력했다면 시작일보다 빠를 수 없다. 생성과 수정에 같은 규칙을 적용한다.

`tagIds`가 없거나 `null`이면 태그를 연결하지 않는다. 새 태그를 입력한 클라이언트는 먼저 `POST /tags`로 태그를 생성한 뒤 반환된 ID를 여행 기록 요청에 포함한다. 여행 기록과 태그 연결은 같은 트랜잭션에서 저장한다.

#### Response `201 Created`

```json
{
  "data": {
    "id": 101
  }
}
```

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `REGION_REQUIRED` | 지역을 고르지 않았고 장소로도 찾지 못함, 또는 한국 기록에 시도가 없음 |
| `404` | `REGION_NOT_FOUND` | 요청한 국가·시도·시군구가 존재하지 않음 |
| `400` | `INVALID_REGION_HIERARCHY` | 요청 지역의 부모 관계가 맞지 않거나, 상위 지역 코드 없이 하위 지역 코드만 옴 |
| `400` | `INVALID_TRAVEL_DATE_RANGE` | 시작일·종료일이 미래이거나 종료일이 시작일보다 빠름 |
| `400` | `INVALID_OBJECT_KEY` | Object Key가 비었거나 너무 김, 요청 안에서 중복됨, 또는 이미 다른 기록에서 사용 중임 |
| `400` | `MEDIA_NOT_UPLOADED` | 이 회원에게 발급된 Object Key가 아니거나, S3에 업로드되지 않음 |
| `503` | `STORAGE_UNAVAILABLE` | S3를 확인할 수 없음. 잠시 후 다시 시도 |
| `400` | `TOO_MANY_TAGS` | 임시 기록당 태그 5개 제한 초과 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |
| `400` | `INVALID_PLACE_ID` | `placeId` 형식이 올바르지 않음 |
| `404` | `PLACE_NOT_FOUND` | 지역 없이 보낸 `placeId`를 제공자에서 찾을 수 없음 |
| `503` | `PLACE_PROVIDER_UNAVAILABLE` | 장소 제공자 장애·키 없음으로 장소를 다시 조회하지 못함 |

### 여행 기록 목록 조회

`GET /api/v1/travel-records`

현재 회원의 기록을 생성일시 내림차순으로 조회한다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `countryCode` | 아니요 | 선택 국가 자체와 `root_id`가 해당 국가인 모든 하위 Region 기록 |
| `provinceCode` | 아니요 | 선택 시도 자체와 `parent_id`가 해당 시도인 시·군·구 기록. `countryCode` 필수 |
| `districtCode` | 아니요 | 선택 시·군·구에 직접 저장된 기록. `countryCode`, `provinceCode` 필수 |
| `tagId` | 아니요 | 현재 회원의 해당 태그가 연결된 기록만 조회 |
| `page` | 아니요 | 기본값 `0`, 0 이상 |
| `size` | 아니요 | 기본값 `20`, 1 이상 100 이하 |

지역 조건과 `tagId`를 함께 전달하면 모든 조건을 `AND`로 결합한다. MVP에서는 태그 하나만 필터링하며 여러 태그의 `ANY`/`ALL` 검색은 제공하지 않는다.

#### 조회 예시

```http
# 내 전체 기록
GET /api/v1/travel-records

# 대한민국과 모든 하위 지역 기록
GET /api/v1/travel-records?countryCode=KR

# 제주특별자치도와 하위 시·군·구 기록
GET /api/v1/travel-records?countryCode=KR&provinceCode=49

# 제주시 기록
GET /api/v1/travel-records?countryCode=KR&provinceCode=49&districtCode=50110

# 라멘맛집 태그가 연결된 내 기록
GET /api/v1/travel-records?tagId=12
```

#### Response `200 OK`

```json
{
  "data": {
    "items": [
      {
        "id": 101,
        "title": "비 오는 날의 제주시",
        "regionName": "제주시",
        "placeName": null,
        "placeAttribution": "Google Maps",
        "placeAttributionUrl": "https://www.google.com/maps",
        "startDate": "2026-08-11",
        "endDate": null,
        "thumbnailUrl": "https://...presigned-get-url",
        "thumbnailUrlExpiresIn": 300,
        "photoCount": 3,
        "tags": [
          { "id": 9, "name": "연인" },
          { "id": 12, "name": "라멘맛집" }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

목록에는 본문과 전체 미디어 목록을 포함하지 않는다. `thumbnailUrl`은 정렬 순서가 가장 앞선 사진의 Presigned GET URL이고 `thumbnailUrlExpiresIn`은 그 만료까지 남은 초다. 사진이 없으면 둘 다 `null`이다.

`photoCount`는 기록에 연결된 사진 수다. 사진이 없으면 `0`이다.

`placeName`, `placeAttribution`, `placeAttributionUrl`은 장소를 연결한 기록에만 값이 있고, 아니면 `null`이다. Google 장소는 약관상 이름을 저장하지 않아 `placeName`이 `null`이다.

`tags`는 `tag.created_at ASC, tag.id ASC` 순서로 반환한다.

#### 검증 및 오류 응답

Access Token에서 식별한 현재 회원의 기록만 반환한다.

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 지역 코드 형식이 올바르지 않음, `page < 0`, `size`가 1 미만 또는 100 초과 |
| `404` | `REGION_NOT_FOUND` | 요청한 국가·시도·시군구 코드가 없음 |
| `400` | `INVALID_REGION_HIERARCHY` | `provinceCode`에 `countryCode`가 없거나 `districtCode`에 `provinceCode`가 없음, 시도가 선택 국가의 직계 자식이 아니거나 시군구가 선택 시도의 직계 자식이 아님 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |

조건에 맞는 기록이 없는 경우는 오류가 아니며 `200 OK`와 빈 `items`를 반환한다.

### 여행 기록 상세 조회

`GET /api/v1/travel-records/{travelRecordId}`

`travelRecordId`는 양의 정수여야 한다. 현재 회원이 소유한 기록만 조회하며, 기록이 없거나 다른 회원의 기록이면 모두 `404 TRAVEL_RECORD_NOT_FOUND`를 반환한다.

#### Response `200 OK`

```json
{
  "data": {
    "id": 101,
    "title": "비 오는 날의 제주시",
    "content": "골목을 걸으며 오래된 가게들을 기록했다.",
    "region": {
      "country": {
        "code": "KR",
        "name": "대한민국"
      },
      "province": {
        "code": "49",
        "name": "제주특별자치도"
      },
      "district": {
        "code": "50110",
        "name": "제주시"
      }
    },
    "startDate": "2026-08-11",
    "endDate": null,
    "objectKeys": [
      "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg"
    ],
    "media": [
      {
        "id": 501,
        "objectKey": "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg",
        "viewUrl": "https://...presigned-get-url",
        "viewUrlExpiresIn": 300,
        "sortOrder": 0
      }
    ],
    "tags": [
      { "id": 9, "name": "연인" },
      { "id": 12, "name": "라멘맛집" }
    ],
    "createdAt": "2026-08-14T10:30:00",
    "updatedAt": "2026-08-15T09:00:00",
    "placeProvider": "GOOGLE",
    "placeId": "ChIJN1t_tDeuEmsRUsoyG83frY4",
    "placeName": null,
    "placeAttribution": "Google Maps",
    "placeAttributionUrl": "https://www.google.com/maps"
  }
}
```

`objectKeys`와 `media`는 `record_media.sort_order` 오름차순으로 반환하며 미디어가 없으면 빈 배열이다. `media[].viewUrl`은 Presigned GET URL이고 `viewUrlExpiresIn`은 만료까지 남은 초다. 국가 단위 기록은 `province`와 `district`가, 시·도 단위 기록은 `district`가 `null`이다.

`place*` 필드는 장소를 연결한 기록에만 값이 있고 아니면 모두 `null`이다. 예전 Geoapify 장소로 저장된 기록은 `placeProvider`가 `GEOAPIFY`로 남는다. `PUT`은 전체 수정이므로 `placeId`를 빼면 기존 장소 연결이 제거된다.

### 여행 기록 수정

`PUT /api/v1/travel-records/{travelRecordId}`

현재 회원이 소유한 여행 기록을 생성 요청과 같은 본문으로 전체 수정한다. `objectKeys`는 수정 후 유지할 전체 미디어 목록이며 배열 순서가 노출 순서가 된다. `tagIds`도 수정 후 연결할 전체 태그 목록이며 `null` 또는 빈 배열이면 기존 태그 연결을 모두 제거한다.

- 기존 Object Key가 요청에도 있으면 미디어를 유지하고 순서만 변경한다.
- 요청에서 빠진 기존 Object Key는 `record_media` 연결을 삭제한다.
- 새 Object Key는 미디어로 추가한다.
- `objectKeys`가 `null`이거나 빈 배열이면 모든 미디어 연결을 삭제한다.
- S3 실제 객체 삭제와 Presigned URL 변환은 후속 작업으로 처리한다.

#### Response `200 OK`

수정된 여행 기록을 상세 조회와 같은 응답 구조로 반환한다.

#### 오류 응답

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 회원 ID, 여행 기록 ID 또는 요청 본문이 올바르지 않음 |
| `400` | `INVALID_OBJECT_KEY` | Object Key가 중복되거나 다른 기록에서 사용 중임 |
| `400` | `MEDIA_NOT_UPLOADED` | 새로 붙인 Object Key가 이 회원에게 발급된 키가 아니거나, S3에 업로드되지 않음 |
| `503` | `STORAGE_UNAVAILABLE` | S3를 확인할 수 없음. 잠시 후 다시 시도 |
| `404` | `TRAVEL_RECORD_NOT_FOUND` | 기록이 없거나 현재 회원의 기록이 아님 |

날짜와 Region 관련 오류는 생성 API와 동일하게 처리한다.

### 여행 기록 삭제

`DELETE /api/v1/travel-records/{travelRecordId}`

현재 회원이 소유한 여행 기록을 삭제한다. 기록이 없거나 다른 회원의 기록이면 존재 여부를 숨기기 위해 동일하게 `404 TRAVEL_RECORD_NOT_FOUND`를 반환한다.

연결된 `record_media` 행은 DB의 `ON DELETE CASCADE`로 함께 삭제한다. S3 실제 객체 삭제는 후속 작업으로 처리한다.

#### Response `204 No Content`

응답 본문은 없다.

#### 오류 응답

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 여행 기록 ID가 양의 정수가 아님 |
| `404` | `TRAVEL_RECORD_NOT_FOUND` | 기록이 없거나 현재 회원의 기록이 아님 |

## 6. 지도 요약 API

지도 요약 응답에는 현재 회원의 기록이 있는 Region만 포함한다. 응답에 없는 Region은 앱에서 `count = 0`, `level = NONE`으로 처리한다.

두 지도 요약 API는 선택 쿼리 파라미터 `tagId`를 받는다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `tagId` | 아니요 | 현재 회원의 해당 태그가 연결된 기록만 집계 |

`tagId`가 없으면 전체 기록을 집계하고, 값이 있으면 태그가 연결된 기록만 집계한다. 지도에 표시할 지역을 태그에 직접 저장하지 않고 `travel_record_tag`와 `travel_record.region_id`를 실시간 집계한다.

### 루트 Region별 지도 색칠 정보 조회

`GET /api/v1/travel-records/map-summary/regions/roots?tagId={tagId}`

#### Response `200 OK`

```json
{
  "data": [
    {
      "regionId": 1,
      "code": "KR",
      "regionType": "COUNTRY",
      "name": "대한민국",
      "count": 12,
      "level": "HIGH"
    }
  ]
}
```

- 기록이 있는 `region_type = COUNTRY` Region을 `code` 오름차순으로 반환한다.
- 현재 회원의 국가 Region 기록과 해당 국가의 모든 하위 Region 기록을 합산한다.
- `regionId`는 후속 하위 Region 지도 요약 요청에 사용한다.
- `code`는 안드로이드 로컬 지도 데이터와 매칭하는 표준 코드다.

| 기록 수 | `level` |
| ---: | --- |
| `0` | `NONE` |
| `1~2` | `LOW` |
| `3~5` | `MEDIUM` |
| `6 이상` | `HIGH` |

### 직속 하위 Region별 지도 색칠 정보 조회

`GET /api/v1/travel-records/map-summary/regions/{regionId}/children?tagId={tagId}`

이전 지도 요약 응답에서 받은 `regionId`를 경로에 전달한다.

#### 요청 예시

```http
GET /api/v1/travel-records/map-summary/regions/1/children?tagId=12
Authorization: Bearer {accessToken}
```

#### Response `200 OK`

```json
{
  "data": [
    {
      "regionId": 15,
      "code": "49",
      "regionType": "PROVINCE",
      "name": "제주특별자치도",
      "count": 5,
      "level": "MEDIUM"
    }
  ]
}
```

- `COUNTRY` ID를 전달하면 기록이 있는 직속 `PROVINCE`를 반환한다.
- `PROVINCE` ID를 전달하면 기록이 있는 직속 `DISTRICT`를 반환한다.
- 각 결과 Region 자신과 모든 하위 Region에 저장된 현재 회원의 기록을 합산한다.
- 정렬 및 색상 단계 규칙은 국가별 조회와 같다.

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `regionId`가 양수가 아님 |
| `404` | `REGION_NOT_FOUND` | 상위 Region이 존재하지 않음 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |

### 태그별 집계 규칙

- 한 기록에 같은 태그를 중복 연결할 수 없으므로 한 지역 집계에서 기록을 한 번만 센다.
- 태그는 존재하지만 연결된 기록이 없으면 빈 배열을 반환한다.
- 기존 `count`와 `level` 계약은 유지한다. 태그 필터 적용 후의 `count`를 현재 `LevelPolicy` 기준으로 `NONE`, `LOW`, `MEDIUM`, `HIGH`로 변환한다.
- 데이터 규모와 응답 시간을 측정하기 전에는 별도 태그–지역 집계 테이블을 만들지 않는다. 원본 관계를 실시간 집계해 기록·태그 변경과 지도 결과의 동기화 문제를 피한다.

## 7. 여행 통계 API

여행 통계는 현재 회원이 작성한 전체 기간의 여행 기록을 기준으로 실시간 집계한다. 별도 통계
테이블을 두지 않으므로 여행 기록과 미디어가 변경되면 다음 조회부터 바로 반영된다.

### 내 전체 여행 통계 조회

`GET /api/v1/travel-records/statistics`

#### Response `200 OK`

```json
{
  "data": {
    "recordCount": 24,
    "mediaCount": 138,
    "visitedCountryCount": 3,
    "visitedKoreaDistrictCount": 8,
    "visitedCountryCodes": ["JP", "KR", "US"],
    "topRegions": [
      {
        "regionId": 10,
        "code": "11",
        "regionType": "PROVINCE",
        "name": "서울특별시",
        "recordCount": 7
      },
      {
        "regionId": 2,
        "code": "JP",
        "regionType": "COUNTRY",
        "name": "일본",
        "recordCount": 4
      }
    ]
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `recordCount` | 현재 회원의 전체 여행 기록 수 |
| `mediaCount` | 현재 회원의 여행 기록에 연결된 전체 미디어 수 |
| `visitedCountryCount` | 기록이 하나 이상 있는 고유 국가 수 |
| `visitedKoreaDistrictCount` | 기록이 하나 이상 있는 대한민국 고유 시·군·구 수 |
| `visitedCountryCodes` | 방문 국가 ISO 코드의 오름차순 목록. 길이는 `visitedCountryCount`와 같음 |
| `topRegions` | 현재 표시 단계로 합산한 기록 수 상위 3개 |

`topRegions`는 `recordCount DESC, regionId ASC`로 정렬한다. 대한민국 `DISTRICT` 기록은 직속
`PROVINCE`로 올려 합산하고, 해외 기록은 `COUNTRY`로 합산한다. 따라서 현재 응답은 대한민국 시·도와
해외 국가가 함께 포함될 수 있다. 기록이 없으면 모든 숫자는 `0`, 목록은 빈 배열이다.

이 집계 단계는 대한민국은 시·군·구, 해외는 국가 단위로 저장하는 현재 MVP 계약에 맞춘 것이다.
해외 행정구역 저장을 지원할 때 필드와 집계 단계, API 버전을 함께 재검토해야 한다. 자세한 결정과
변경 조건은 [ADR 0016](backend/docs/adr/0016-travel-statistics-read-model.md)을 참고한다.

## 8. 관리자 및 서비스 의견 API

관리자 API는 관리자 세션 인증과 ADMIN 권한이 필요하다. 쓰기 요청에는 CSRF 토큰도 필요하다.
앱의 의견 제출 API는 앱 Access Token으로 인증한다.

목록 API는 기본 `page=0`, `size=20`, 최대 `size=100`을 사용한다.
서비스 의견 목록의 배열 필드는 `feedback`이며 페이지 정보는 `page`, `size`, `totalElements`, `totalPages`다.
현재 의견 시각 응답에는 시간대 접미사가 없으며 UTC 저장 계약에 따라 해석한다.
의견 날짜 필터는 한국 날짜를 UTC 시작·종료 구간으로 변환해 적용한다.

### 관리자 인증

관리자 웹은 쿠키 세션(`JSESSIONID`)을 쓴다. 쓰기 요청(`POST`·`PATCH` 등)은 `GET /admin/auth/csrf`로 받은 토큰을 응답의 `headerName` 헤더에 담아 보낸다.

| Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/auth/csrf` | 없음 | `{ "data": { "headerName": "X-CSRF-TOKEN", "token": "..." } }` |
| `POST` | `/api/v1/admin/auth/login` | 없음(CSRF 필요) | 본문 `{ "username", "password" }`(둘 다 필수). 성공 시 세션 ID를 새로 발급하고 `{ "data": { "username" } }` 반환 |
| `GET` | `/api/v1/admin/auth/me` | 관리자 세션 | 현재 관리자 `{ "data": { "username" } }` |
| `POST` | `/api/v1/admin/auth/logout` | 관리자 세션 | 세션을 끊고 `204 No Content` |

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `401` | `ADMIN_LOGIN_FAILED` | 아이디 또는 비밀번호가 틀림 |
| `401` | `ADMIN_SESSION_REQUIRED` | 관리자 세션 없이 관리자 API 호출 |

### 대시보드 집계

`GET /api/v1/admin/dashboard?from=2026-10-02&to=2026-10-08`

기간 파라미터는 선택 사항이다. 둘 다 생략하면 한국 시간 기준 오늘을 포함한 최근 7일을 사용한다.
`from`만 지정하면 `to`는 오늘, `to`만 지정하면 `from`은 해당 날짜를 포함한 최근 7일로 보완한다.
`from`과 `to`는 양 끝을 포함한다. `total`은 조회 시점 누적 수, `InPeriod`는 해당 기간에 생성된 수다.

```json
{
  "data": {
    "period": { "from": "2026-10-02", "to": "2026-10-08" },
    "members": { "total": 1284, "guest": 812, "kakao": 380, "google": 70, "apple": 22, "newInPeriod": 35 },
    "travelRecords": { "total": 5492, "createdInPeriod": 286 }
  }
}
```

회원은 `member.provider`의 `GUEST`, `KAKAO`, `GOOGLE`, `APPLE` 값으로 집계한다. provider가 비어 있는 기존 회원은 `total`에만
포함한다. 기간별 회원·기록 수는 각각 `member.created_at`, `travel_record.created_at` 기준으로 한국 시간
날짜의 시작부터 종료일 다음 날 시작 전까지 집계한다. `from`이 `to`보다 늦으면 `400 INVALID_DASHBOARD_DATE_RANGE`.

### 회원 목록 및 상세

`GET /api/v1/admin/members?page=0&size=20&provider=KAKAO&keyword=M-10842&joinedFrom=2026-10-01&joinedTo=2026-10-08`

모든 필터는 선택 사항이다. `provider`는 `GUEST`, `KAKAO`; `keyword`는 회원 ID, UUID 또는 이름 검색이다.
가입일 양 끝을 포함하고 가입일 내림차순으로 정렬한다.

```json
{
  "data": {
    "items": [{
      "memberId": 10842,
      "uuid": "d4d7e1c4-0daf-4ed4-9dab-00a8acafc5da",
      "name": "김지우",
      "provider": "KAKAO",
      "joinedAt": "2026-10-08T03:32:00Z",
      "travelRecordCount": 12,
      "sessionStatus": "ACTIVE",
      "lastRefreshResult": "SUCCESS",
      "lastRefreshAt": "2026-10-08T05:10:00Z"
    }],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
  }
}
```

`GET /api/v1/admin/members/{memberId}`는 세션 상세를 반환한다. 활성·미만료 Refresh Token이 하나 이상이면
`sessionStatus=ACTIVE`, 아니면 `INACTIVE`다. 이는 앱이 현재 실행 중인지 의미하지 않는다.

```json
{
  "data": {
    "memberId": 10842,
    "uuid": "d4d7e1c4-0daf-4ed4-9dab-00a8acafc5da",
    "name": "김지우",
    "provider": "KAKAO",
    "joinedAt": "2026-10-08T03:32:00Z",
    "travelRecordCount": 12,
    "sessionStatus": "ACTIVE",
    "activeSessionCount": 1,
    "lastRefresh": {
      "result": "FAILURE",
      "occurredAt": "2026-10-08T05:10:00Z",
      "reasonCode": "EXPIRED_REFRESH_TOKEN"
    }
  }
}
```

리프레시 실패 이력에는 원문 토큰을 저장하지 않는다. 회원을 식별할 수 있는 실패만 회원과 연결하고,
알 수 없는 토큰 요청은 특정 회원의 실패 상태로 표시하지 않는다. 실패 이력이 없으면 `lastRefresh`는
`null`이다. 관리자 응답에는 여행 기록 제목·내용·사진을 포함하지 않는다.

### 앱 서비스 의견 제출

`POST /api/v1/service-feedback` — 앱 회원 인증 필요

```json
{
  "category": "FEATURE_REQUEST",
  "content": "여행 기록을 여러 장 한 번에 등록하고 싶어요.",
  "platform": "IOS"
}
```

| 필드 | 규칙 |
| --- | --- |
| `category` | 필수: `BUG_REPORT`, `FEATURE_REQUEST`, `USABILITY`, `OTHER` |
| `content` | 필수, 공백 제거 후 1~2,000자 |
| `platform` | 필수: `IOS`, `ANDROID` |

회원 ID와 접수 시각은 서버가 지정한다. 상태는 `UNCONFIRMED`로 시작하며 클라이언트가 상태를 지정할 수
없다.

`201 Created`:

```json
{
  "data": { "feedbackId": 248, "createdAt": "2026-10-08T05:32:00" }
}
```

사용자에게 접수 확인만 반환한다. 의견 답변·처리 상태 공개 API는 제공하지 않는다.

### 관리자 서비스 의견 조회 및 상태 변경

- `GET /api/v1/admin/service-feedback?page=0&size=20&status=UNCONFIRMED&category=BUG_REPORT&platform=IOS&from=2026-10-02&to=2026-10-08`
- `GET /api/v1/admin/service-feedback/{feedbackId}`
- `PATCH /api/v1/admin/service-feedback/{feedbackId}/status`

목록 필터는 선택 사항이며 최신 접수순으로 반환한다. 목록·상세 항목:

```json
{
  "feedbackId": 248,
  "category": "FEATURE_REQUEST",
  "content": "여행 기록을 여러 장 한 번에 등록하고 싶어요.",
  "platform": "IOS",
  "status": "UNCONFIRMED",
  "createdAt": "2026-10-08T05:32:00"
}
```

상세 응답에는 작성자 개인정보를 포함하지 않는다. 상태 변경 API는 관리자 세션 인증이 필요하다.

```json
{ "status": "IN_PROGRESS" }
```

허용 상태는 `UNCONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `ON_HOLD`, `NOT_IMPLEMENTED`다. 성공하면
변경된 의견을 반환하고 변경 내용과 변경 시각을 감사 로그에 기록한다.

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 날짜, 페이지, enum 또는 요청 필드가 올바르지 않음 |
| `404` | `ADMIN_MEMBER_NOT_FOUND` | 회원 ID가 없음 |
| `404` | `SERVICE_FEEDBACK_NOT_FOUND` | 의견 ID가 없음 |

### 데이터 및 감사 로그

- `member_auth_event`: Refresh 성공·실패, 사유 코드, 발생 시각. 토큰 원문은 저장하지 않음
- `service_feedback`: 회원, 카테고리, 내용, OS, 내부 상태, 접수·수정 시각
- `admin_audit_log`: 의견 상태 변경 대상·변경 내용·시각. 관리자별 행위자는 기록하지 않음

## 9. 장소 검색 API

사진을 찾기 위한 장소 검색이다. Google Places API (New) Autocomplete를 쓴다. 약관, 호출 한도, 서킷 브레이커, 과금 상세는 [장소 검색과 여행 기록 연결](backend/docs/place-search.md)에 정리했다.

### 장소 검색

`GET /api/v1/places/search?query=경복&sessionToken={uuid}`

| 파라미터 | 필수 | 제약조건 |
| --- | --- | --- |
| `query` | 예 | 공백 불가, 최대 100자 |
| `sessionToken` | 예 | 검색 시작 시 만든 UUID. 영문·숫자·`-`·`_` 1~36자 |

#### Response `200 OK`

```json
{
  "data": [
    {
      "placeId": "ChIJN1t_tDeuEmsRUsoyG83frY4",
      "name": "경복궁",
      "address": "대한민국 서울특별시 종로구 사직로 161",
      "attribution": "Google Maps",
      "attributionUrl": "https://www.google.com/maps"
    }
  ]
}
```

후보 목록 근처에 Google Maps 로고나 `Google Maps` 글자를 표시해야 한다.

### 장소 선택

`GET /api/v1/places/{placeId}?sessionToken={uuid}`

검색에 쓴 `sessionToken`을 그대로 보낸다(선택). 선택 후나 검색을 지울 때 클라이언트는 새 토큰을 만든다.

#### Response `200 OK`

```json
{
  "data": {
    "placeId": "ChIJN1t_tDeuEmsRUsoyG83frY4",
    "name": null,
    "countryCode": "KR",
    "suggestedRegion": {
      "country": { "code": "KR", "name": "대한민국" },
      "province": { "code": "11", "name": "서울특별시" },
      "district": { "code": "11010", "name": "종로구" }
    },
    "manualRegionRequired": false,
    "attribution": "Google Maps",
    "attributionUrl": "https://www.google.com/maps"
  }
}
```

- Google 장소는 이름을 저장·반환하지 않아 `name`이 `null`이다. 화면에는 고른 후보의 `name`을 표시한다.
- `suggestedRegion`은 주소 이름으로 맞춘 **추천값**이다. 시·군·구를 못 맞추면 시·도까지만 추천한다. 지역을 전혀 찾지 못하면 `suggestedRegion`이 `null`, `manualRegionRequired`가 `true`이며 사용자가 지역을 직접 고른다.
- 기록 저장 시 `placeId`만 보내면 이 추천 지역을 쓰고, 사용자가 지역을 고쳤다면 지역 코드도 함께 보낸다.

### 호출 한도

- 검색은 회원별 1분에 20회, 선택은 회원별 UTC 하루 100회다. 요청 값 검증보다 먼저 세므로 `400`으로 끝날 요청도 횟수에 들어간다.
- Google 호출은 서버 전체 기준 분당 60회(서버별), 자동완성·상세 각각 UTC 월 9,000회로 제한한다.

### 장소 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `query`·`sessionToken` 형식이 올바르지 않음 |
| `400` | `INVALID_PLACE_ID` | 장소 ID가 영문·숫자·`-`·`_` 1~255자가 아님 |
| `404` | `PLACE_NOT_FOUND` | 제공자에 장소가 없음 |
| `429` | `PLACE_RATE_LIMITED` | 회원 한도 또는 Google 분당 한도 초과 |
| `429` | `PLACE_SEARCH_BUDGET_EXHAUSTED` | 이번 달 자동완성 한도 소진 |
| `503` | `PLACE_PROVIDER_BUDGET_EXHAUSTED` | 이번 달 상세 조회 한도 소진 |
| `503` | `PLACE_PROVIDER_UNAVAILABLE` | Google 장애·서킷 열림·키 없음·한도 저장소 장애 |

## 10. 출시 알림 API

### 출시 알림 신청

`POST /api/v1/waitlist` — 인증 없음(랜딩 페이지용)

```json
{
  "email": "traveler@example.com",
  "privacyConsent": true,
  "ageConfirmed": true
}
```

| 필드 | 타입 | 필수 | 제약조건 |
| --- | --- | --- | --- |
| `email` | String | 예 | 이메일 형식, 최대 254자. 앞뒤 공백 제거·NFKC·소문자로 정규화해 중복을 판단 |
| `privacyConsent` | Boolean | 예 | 개인정보 수집·이용 동의, `true`여야 함 |
| `ageConfirmed` | Boolean | 예 | 만 14세 이상 확인, `true`여야 함 |

#### Response

| 상태 | 본문 | 조건 |
| --- | --- | --- |
| `201 Created` | `{ "data": { "status": "SUBSCRIBED" } }` | 새로 신청 |
| `200 OK` | `{ "data": { "status": "ALREADY_SUBSCRIBED" } }` | 이미 신청한 이메일 |
| `400` | `VALIDATION_ERROR` | 이메일 형식·길이 오류 또는 동의 항목이 `true`가 아님 |

## 11. Member API

로그인한 회원 자신의 정보를 다룬다. 게스트 회원도 같은 API를 쓴다. 응답에 회원 ID는 넣지 않는다.

닉네임은 다른 회원에게 보이는 이름이다. 소셜 제공자가 준 이름과는 따로 저장하며, 제공자 이름은 응답에 넣지 않는다.

- 가입하면(소셜·게스트 모두) 제공자 이름과 관계없이 기본 이름으로 시작한다. `회원` 뒤에 5자리 숫자를 붙인다(예: `회원58213`).
- 로그인·게스트 승격으로는 바뀌지 않는다. 회원이 직접 바꿀 때만 바뀐다.

닉네임 변경 규칙:

- 앞뒤 공백을 지운 뒤 1~20자 (이모지 등은 한 글자로 센다). 20자를 넘으면 자르지 않고 거절
- 공백만 있거나 제어 문자(줄바꿈 등)가 있으면 거절
- 다른 회원과 같은 닉네임도 쓸 수 있음

### 내 회원 정보 조회

`GET /api/v1/members/me`

#### Response `200 OK`

```json
{
  "data": {
    "nickname": "회원58213",
    "provider": "GUEST"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `nickname` | String | 닉네임 |
| `provider` | String | 로그인 방식. `KAKAO`, `GOOGLE`, `APPLE`, `GUEST` |

### 내 닉네임 변경

`PATCH /api/v1/members/me/nickname`

```json
{
  "nickname": "지수"
}
```

#### Response `200 OK`

내 회원 정보 조회와 같은 객체를 반환한다.

```json
{
  "data": {
    "nickname": "지수",
    "provider": "KAKAO"
  }
}
```

### 회원 탈퇴

`DELETE /api/v1/members/me`

게스트 회원도 같은 API로 탈퇴한다.

- 이름·닉네임은 `탈퇴한 회원`으로 바뀌고, 소셜 계정 연결 정보는 지워진다.
- 모든 Refresh Token을 폐기한다. 앱은 저장한 토큰을 지운다.
- 이미 발급된 Access Token은 만료(최대 30분)까지 남지만, 회원 정보 API는 `401 INACTIVE_MEMBER`, 토큰 재발급은 `401 INVALID_REFRESH_TOKEN`이다.
- 같은 소셜 계정으로 다시 로그인하면 **새 회원**으로 가입한다(`isNewMember: true`). 탈퇴 전 기록은 이어지지 않는다.
- 이미 탈퇴했으면 아무것도 하지 않고 `204`를 반환한다.
- 탈퇴한 게스트의 Access Token을 실어 소셜 로그인하면 승격하지 않고 새 회원으로 가입한다.

#### Response `204 No Content`

### Member 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 닉네임이 없거나 길이·형식 규칙에 맞지 않음 |
| `401` | `INVALID_ACCESS_TOKEN` | Access Token이 없거나 유효하지 않음 |
| `401` | `EXPIRED_ACCESS_TOKEN` | Access Token이 만료됨 |
| `401` | `INACTIVE_MEMBER` | Access Token의 회원이 없거나 탈퇴했거나 버려진 게스트임. 저장한 토큰을 지우고 로그인 화면으로. 탈퇴 API는 탈퇴한 회원에게도 `204` |

## 12. Group API

> 상태: T1 그룹(만들기·목록·조회·이름 바꾸기)·T2 초대(만들기·미리보기·수락) 구현됨, 나머지는 구현 전 설계 · 결정과 이유는 [ADR 0022](backend/docs/adr/0022-group-sharing-albums.md)

친구·가족이 같은 여행을 앨범 하나로 함께 본다. **저장은 각자, 보기는 하나로** 한다. 사진은 늘 자기 여행 기록에 저장하고, 앨범은 그 기록들을 id로 묶는다. 앱은 앨범을 기록 하나처럼 보여 준다.

```
지수의 기록 (도쿄, 3/1~3/3) ─┐
                              ├─▶ 앨범 "도쿄" (3/1~3/4, 사진마다 올린 사람 닉네임)
민호의 기록 (도쿄, 3/2~3/4) ─┘
```

공통 규칙:

- 그룹 API는 소셜 로그인 회원만 쓴다. 게스트는 `403 SOCIAL_LOGIN_REQUIRED`다.
- 그룹 멤버가 아니면 그룹이 있어도 `404 GROUP_NOT_FOUND`를 돌려준다.
- 그룹 이름은 앞뒤 공백 제거 후 1~30자, 앨범 이름은 1~200자다. 이름은 멤버 누구나 바꾼다.
- 그룹은 최대 30명이다. 초대는 멤버 누구나 만들고 1일 뒤 만료된다.
- 한 회원은 그룹에 최대 10개까지 속한다. 만들기와 초대 수락 모두 이미 10개면 `409 TOO_MANY_GROUPS`다.
- 한 기록을 여러 앨범에 담을 수 있다. 한 앨범에는 멤버당 기록 하나다.
- 앨범에 담는 기록은 내 기록이어야 한다. 남의 기록 id면 `404 TRAVEL_RECORD_NOT_FOUND`다.

| 태스크 | API |
| --- | --- |
| T1 그룹 | 그룹 만들기, 내 그룹 목록, 그룹 조회, 그룹 이름 바꾸기 |
| T2 초대 | 초대 만들기, 초대 미리보기, 초대 수락 |
| T3 나가기 | 그룹 나가기 |
| T4 앨범 쓰기 | 앨범 열기, 앨범에 기록 붙이기, 앨범에서 내리기, 앨범 바꾸기 |
| T5 앨범 조회 | 앨범 목록, 앨범 상세 |
| T6 알림 | 기기 토큰 등록·삭제, 앨범에 올리면 다른 멤버에게 푸시 |

### 그룹 만들기

`POST /api/v1/groups`

```json
{
  "name": "제주 가족"
}
```

만든 사람이 첫 멤버이자 방장이 된다.

#### Response `201 Created`

그룹 조회와 같은 객체를 반환한다.

### 내 그룹 목록

`GET /api/v1/groups`

내가 들어간 그룹을 들어간 순서(`joinedAt ASC`)로 반환한다.

#### Response `200 OK`

```json
{
  "data": [
    {
      "id": 3,
      "name": "제주 가족",
      "memberCount": 4,
      "albumCount": 2
    }
  ]
}
```

### 그룹 조회

`GET /api/v1/groups/{groupId}`

#### Response `200 OK`

```json
{
  "data": {
    "id": 3,
    "name": "제주 가족",
    "members": [
      { "nickname": "지수", "owner": true, "me": true },
      { "nickname": "민호", "owner": false, "me": false }
    ]
  }
}
```

`members`는 들어온 순서다. 회원 ID는 넣지 않는다.

### 그룹 이름 바꾸기

`PATCH /api/v1/groups/{groupId}`

```json
{
  "name": "제주 가족여행"
}
```

성공 시 그룹 조회와 같은 객체를 `200 OK`로 반환한다.

### 초대 만들기

`POST /api/v1/groups/{groupId}/invitations`

링크용 토큰과 직접 입력용 6자리 코드를 함께 발급한다. 앱은 `token`으로 링크를 만들어 공유창에 넘긴다.

#### Response `201 Created`

```json
{
  "data": {
    "token": "q9Xw2...",
    "code": "4KD7PZ",
    "expiresAt": "2026-10-23T18:00:00"
  }
}
```

### 초대 미리보기

`GET /api/v1/invitations/{tokenOrCode}`

수락 전에 어느 그룹인지 보여 준다. 링크 토큰과 6자리 코드를 모두 받는다(6자리면 코드로 보고 대소문자를 가리지 않는다). 게스트도 호출할 수 있다(로그인을 권하는 화면에 쓴다).

#### Response `200 OK`

```json
{
  "data": {
    "groupName": "제주 가족",
    "memberCount": 4,
    "alreadyMember": false
  }
}
```

### 초대 수락

`POST /api/v1/invitations/{tokenOrCode}/accept`

이미 멤버면 아무것도 바꾸지 않고 성공한다.

#### Response `200 OK`

그룹 조회와 같은 객체를 반환한다.

### 그룹 나가기

`DELETE /api/v1/groups/{groupId}/members/me`

- 내가 담은 앨범 항목이 모두 내려간다. 기록은 남는다.
- 방장이 나가면 가장 먼저 들어온 멤버가 방장이 된다.
- 마지막 멤버가 나가면 그룹과 앨범, 초대를 지운다.

#### Response `204 No Content`

### 앨범 열기 (따로 올리기)

`POST /api/v1/groups/{groupId}/albums`

내 기록 하나로 새 앨범을 연다. 이름과 지역은 비우면 기록의 제목과 지역을 쓴다.

```json
{
  "travelRecordId": 101,
  "name": "도쿄 3월",
  "regionId": 392
}
```

#### Response `201 Created`

앨범 상세와 같은 객체를 반환한다.

### 앨범에 기록 붙이기

`POST /api/v1/groups/{groupId}/albums/{albumId}/entries`

```json
{
  "travelRecordId": 205
}
```

앱은 앨범의 지역과 기간으로 미리 채운 화면에서 `POST /travel-records`로 내 기록을 먼저 만들고, 돌아온 기록 id로 이 API를 부른다. 이미 있는 내 기록을 붙여도 된다. 이 앨범에 내 기록이 이미 있으면 `409 ALBUM_ENTRY_CONFLICT`다.

#### Response `201 Created`

앨범 상세와 같은 객체를 반환한다.

### 앨범에서 내리기

`DELETE /api/v1/groups/{groupId}/albums/{albumId}/entries/{travelRecordId}`

내 기록과 앨범의 연결만 끊는다. 기록은 남는다. 남의 항목은 내릴 수 없다.

#### Response `204 No Content`

### 앨범 바꾸기

`PATCH /api/v1/groups/{groupId}/albums/{albumId}`

```json
{
  "name": "도쿄 봄",
  "regionId": 392
}
```

보낸 필드만 바꾼다. 성공 시 앨범 상세와 같은 객체를 `200 OK`로 반환한다.

### 앨범 목록

`GET /api/v1/groups/{groupId}/albums`

최근에 연 앨범부터 반환한다.

#### Response `200 OK`

```json
{
  "data": [
    {
      "id": 7,
      "name": "도쿄 3월",
      "region": { "code": "JP", "name": "일본" },
      "startDate": "2026-03-01",
      "endDate": "2026-03-04",
      "coverViewUrl": "https://...signed-url",
      "contributorNicknames": ["지수", "민호"]
    }
  ]
}
```

### 앨범 상세

`GET /api/v1/groups/{groupId}/albums/{albumId}`

담긴 기록을 기록 하나처럼 합쳐 보여 준다. 기간은 담긴 기록의 가장 이른 시작일부터 가장 늦은 종료일(없으면 시작일)까지다.

#### Response `200 OK`

```json
{
  "data": {
    "id": 7,
    "name": "도쿄 3월",
    "region": { "code": "JP", "name": "일본" },
    "startDate": "2026-03-01",
    "endDate": "2026-03-04",
    "records": [
      {
        "travelRecordId": 101,
        "nickname": "지수",
        "mine": true,
        "title": "도쿄 첫날",
        "content": "",
        "startDate": "2026-03-01",
        "endDate": "2026-03-03"
      }
    ],
    "media": [
      {
        "travelRecordId": 101,
        "nickname": "지수",
        "viewUrl": "https://...signed-url",
        "viewUrlExpiresIn": 300
      }
    ]
  }
}
```

`media`는 담은 순서대로 기록마다 `sort_order` 순이다. 사진 주소는 여행 기록 상세와 같은 방식으로 서명한다.

### 앨범 알림

그룹원이 앨범을 열거나 기록을 붙이면, 올린 사람을 뺀 같은 그룹 멤버의 기기에 FCM 푸시를 보낸다. 앨범 쓰기가 커밋된 뒤 따로 보내므로 발송 실패가 올리기를 실패시키지 않는다.

| 항목 | 값 |
| --- | --- |
| 제목 | 그룹 이름 (예: `제주 가족`) |
| 본문 | `민호님이 '도쿄 3월'에 사진을 올렸어요` |
| data | `{"type": "ALBUM_UPDATED", "groupId": "3", "albumId": "7"}` — 누르면 앨범 상세로 이동 |

### 기기 토큰 등록

`PUT /api/v1/push-tokens`

```json
{
  "token": "fcm-registration-token",
  "platform": "ANDROID"
}
```

앱은 로그인 뒤와 FCM이 토큰을 새로 줄 때마다 부른다. 같은 토큰이 이미 있으면 현재 회원으로 옮긴다. `platform`은 `ANDROID` 또는 `IOS`다.

#### Response `204 No Content`

### 기기 토큰 삭제

`DELETE /api/v1/push-tokens`

```json
{
  "token": "fcm-registration-token"
}
```

로그아웃 전에 부른다. 없는 토큰이어도 `204`다.

#### Response `204 No Content`

### Group 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 그룹·앨범 이름 길이, 지역 id 형식이 맞지 않음 |
| `401` | `INACTIVE_MEMBER` | 탈퇴한 회원의 남은 access token으로 그룹 API를 부름 |
| `403` | `SOCIAL_LOGIN_REQUIRED` | 게스트가 그룹 API를 부름(초대 미리보기는 제외) |
| `404` | `GROUP_NOT_FOUND` | 그룹이 없거나 내가 멤버가 아님 |
| `404` | `INVITATION_NOT_FOUND` | 토큰·코드가 없거나 만료됨 |
| `404` | `ALBUM_NOT_FOUND` | 앨범이 없거나 다른 그룹의 앨범임 |
| `404` | `ALBUM_ENTRY_NOT_FOUND` | 앨범에 내 그 기록이 없음 |
| `404` | `TRAVEL_RECORD_NOT_FOUND` | 기록이 없거나 내 기록이 아님 |
| `404` | `REGION_NOT_FOUND` | 지역 id가 없음 |
| `409` | `GROUP_FULL` | 그룹이 이미 30명임 |
| `409` | `TOO_MANY_GROUPS` | 내가 이미 그룹 10개에 속해 있어 더 만들거나 들어갈 수 없음 |
| `409` | `ALBUM_ENTRY_CONFLICT` | 이 앨범에 내 기록이 이미 있음 |
| `429` | `INVITATION_RATE_LIMITED` | 초대 미리보기·수락을 합쳐 회원별 1분 10번, 하루 50번을 넘김 |

## 서비스 의견 클라이언트 연동 명세

앱 제출 요청·성공 및 오류 응답 예시는 [앱 서비스 의견 제출 API](client/docs/service-feedback-api.md)를 참고한다. 관리자 목록 응답의 배열 필드는 `feedback`이며, 상세한 현재 계약은 [서비스 의견 API 명세](backend/docs/service-feedback-api.md)에 정리했다.
