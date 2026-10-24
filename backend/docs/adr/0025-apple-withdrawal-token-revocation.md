# ADR 0025. 애플 회원은 탈퇴할 때 다시 인증받아 애플 토큰을 철회한 뒤 탈퇴한다

- 상태: 제안
- 날짜: 2026-10-25
- 관련: #445, ADR 0020, ADR 0021

---

## 문제

앱스토어 심사 지침 5.1.1(v)은 Sign in with Apple을 쓰는 앱이 회원 탈퇴 때 **애플 토큰을 철회**(`POST /auth/revoke`)해
앱과 애플 계정의 연결을 끊도록 요구한다. 지키지 않으면 심사에서 거절될 수 있다.

ADR 0020은 identity token만 검증하므로 서버에 애플 refresh token·access token이 없다. 철회하려면 이 토큰이 필요하다.

## 결정

### 탈퇴할 때 애플 로그인을 다시 받아 그 자리에서 철회한다

```
앱: 탈퇴 → Sign in with Apple 다시 → identityToken, authorizationCode
POST /api/v1/auth/apple/withdrawal  (Authorization: Bearer {access token})
  1. 회원이 활동 중인 애플 회원인지 확인
  2. identity token 검증 → sub가 회원의 provider_id와 같은지 확인
  3. authorization code를 토큰으로 교환(client_id = identity token의 aud) → 철회
  4. 탈퇴(MemberService.withdrawUnlinked)
```

- **본인 확인**: 재인증한 애플 계정(`sub`)이 탈퇴하는 회원과 같아야 한다. 확인하지 않으면 다른 애플 계정으로 재인증해도 탈퇴된다.
- **client_id**: authorization code는 발급된 앱의 client_id로만 교환된다. identity token의 `aud`(번들 ID)를 쓴다.
  운영·Internal 번들이 달라도 앱이 따로 알려 주지 않아도 된다.
- **client_secret**: Sign in with Apple 키(`.p8`)로 서명한 ES256 JWT. `kid` = Key ID, `iss` = Team ID,
  `sub` = client_id, `aud` = `https://appleid.apple.com`. 요청할 때마다 만들고 5분으로 둔다.
- 교환 응답의 refresh token을 철회한다(없으면 access token). 애플은 refresh token을 철회하면 연결을 끊는다.

### 철회가 실패하면 탈퇴하지 않는다

토큰을 저장하지 않으므로 나중에 다시 철회할 수 없다. 사용자가 화면 앞에 있으니 실패를 알리고 다시 시도하게 한다.

| 상황 | 응답 |
|---|---|
| 애플 회원이 아님 | `400 NOT_APPLE_MEMBER` |
| identity token이 잘못됨, 다른 계정, code 만료(5분)·재사용(`invalid_grant`) | `400 APPLE_REAUTHENTICATION_FAILED` |
| 애플 장애, client_secret·client_id 설정 오류, 철회 키 미설정 | `503 APPLE_UNAVAILABLE` (ERROR 알림) |

- 보호된 경로라 재인증 실패를 401로 주지 않는다. 앱은 "그 밖의 401"을 로그아웃으로 처리한다.
- 외부 호출(교환·철회)은 트랜잭션 밖에서 먼저 하고, 성공하면 탈퇴 트랜잭션을 연다.
- 철회 뒤 탈퇴가 실패하면 애플 연결만 끊긴다. 다시 애플로 로그인하면 `sub`가 같아 같은 회원으로 들어온다.

### 애플 회원은 일반 탈퇴로 탈퇴할 수 없다

`DELETE /members/me`로 탈퇴하면 철회를 건너뛴다. 애플 회원이면 `400 APPLE_REAUTHENTICATION_REQUIRED`로 거부한다.
애플 처리(키·애플 API)는 auth가 맡고 탈퇴는 member에 맡기도록 경로를 나눈다. member는 auth를 모른다.

## 검토한 대안

### 로그인할 때 애플 refresh token을 받아 저장

채택하지 않았다. 암호화해 저장할 비밀이 생기고, 이미 가입한 애플 회원은 저장된 토큰이 없어 결국 재인증이 필요하다.
탈퇴는 드문 동작이라 재인증 한 번은 감수할 만하다.

### 탈퇴는 진행하고 철회만 나중에 재시도

토큰을 저장하지 않으면 재시도할 수단이 없다.

### `DELETE /members/me`에 authorization code를 선택 필드로 받기

채택하지 않았다. member가 애플 키와 애플 API를 알게 되고, DELETE 본문은 표준상 의미가 정해져 있지 않다.

## 결과

### 장점

- 서버에 저장하는 애플 비밀이 없다. 기존 애플 회원에도 바로 적용된다.
- 재인증한 계정이 본인인지 확인한다.

### 비용과 주의점

- 운영·개발 서버 `/etc/mapmory.env`에 `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`(`.p8`, 비밀값)를 넣어야 한다.
  비어 있으면 로그인은 되지만 애플 회원 탈퇴가 `503`이다. 키가 잘못됐으면 애플리케이션이 뜨지 않는다.
- 앱은 탈퇴 직전에 Sign in with Apple을 다시 띄우고, 받은 값을 5분 안에 한 번만 보낸다.
- 카카오 연결 끊기는 #469에서 다룬다.
