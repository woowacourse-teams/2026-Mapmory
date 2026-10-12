# ADR 0019. 구글 로그인은 앱이 전달한 ID token을 JWKS로 검증한다

- 상태: 제안
- 날짜: 2026-10-06
- 관련: ADR 0010, ADR 0015

---

## 문제

카카오 계정이 없거나 카카오 연동을 꺼리는 사용자를 위해 두 번째 로그인 수단으로 구글을
지원한다. 카카오와 같은 "앱이 토큰을 받아 서버에 넘기는" 흐름을 쓸지, 구글은 어떤 토큰을
어떻게 검증할지 정해야 한다.

## 결정

### 앱이 구글 SDK로 받은 ID token을 서버가 검증한다

- Android는 Credential Manager(`GetGoogleIdOption`), iOS는 GoogleSignIn SDK로 로그인해
  ID token을 받고 `POST /api/v1/auth/login/google`로 보낸다.
- 서버는 구글 사용자 정보 API를 호출하지 않는다. ID token은 구글이 서명한 JWT이므로
  `https://www.googleapis.com/oauth2/v3/certs`의 공개키로 서명을 확인하고 다음을 검증한다.
  - `iss`가 `accounts.google.com` 또는 `https://accounts.google.com`
  - `aud`가 `google.client-ids`(환경변수 `GOOGLE_CLIENT_IDS`)에 등록한 클라이언트 ID 중 하나
  - `exp`가 지나지 않음
- 공개키는 Spring Security의 `NimbusJwtDecoder`가 내려받아 캐시하고, 처음 보는 `kid`가 오면
  다시 받는다. 로그인마다 구글을 호출하지 않는다.
- `sub`를 `provider_id`로 쓴다. 이메일은 바뀔 수 있고 동의 항목을 늘리므로 쓰지 않는다.

### 회원 결정과 토큰 발급은 카카오와 공유한다

ADR 0015의 3단계 중 [1] 신원 확인만 구글용으로 추가하고, [2] 회원 결정(게스트 승격 포함)과
[3] 토큰 발급은 그대로 재사용한다. `AuthProvider.GOOGLE`이 추가될 뿐 스키마 변경은 없다.
같은 사람의 카카오 회원과 구글 회원은 별개 회원이며, 계정 병합은 하지 않는다.

## 검토한 대안

### 구글 access token으로 userinfo API 호출 (카카오 방식)

채택하지 않았다. 로그인마다 외부 호출이 생기고, access token은 다른 앱용으로 발급된
것인지(`aud`) 확인하기 어렵다. 구글은 백엔드 인증에 ID token 검증을 권장한다.

### 인가 코드를 서버가 교환

채택하지 않았다(ADR 0010과 같은 이유). client secret 보관과 리다이렉트 처리가 필요하고,
네이티브 SDK 흐름보다 구현이 크다.

### google-api-client의 GoogleIdTokenVerifier

채택하지 않았다. 같은 일을 하지만 의존성이 크다. 이미 쓰는 Spring Security의
`spring-security-oauth2-jose`(Nimbus)로 충분하다.

## 결과

### 장점

- 로그인 시 구글 API 호출이 없어 지연과 외부 장애 영향이 작다.
- 다른 앱용 토큰 재사용을 `aud` 검증으로 막는다.

### 비용과 주의점

- 구글 클라이언트 ID(Android/iOS/웹)를 운영 환경변수 `GOOGLE_CLIENT_IDS`에 넣어야 한다.
  비어 있으면 모든 구글 로그인이 `INVALID_GOOGLE_TOKEN`으로 거부된다.
- Android Credential Manager는 `serverClientId`로 지정한 **웹** 클라이언트 ID를 `aud`로
  넣는다. Android 클라이언트 ID만 등록하면 검증에 실패한다.
- 공개키를 받아오지 못하면 `GOOGLE_UNAVAILABLE`(503)로 응답한다.
