# ADR 0024. 카카오 로그인도 앱이 전달한 ID token을 JWKS로 검증한다

- 상태: 제안
- 날짜: 2026-10-24
- 관련: #456, #452, ADR 0010, ADR 0019, ADR 0020

---

## 문제

ADR 0010은 카카오 로그인을 "앱이 보낸 카카오 access token으로 `GET /v2/user/me`를 호출"하는 방식으로 정했다.
OIDC ID token 검증은 카카오 콘솔에서 OpenID Connect를 켜야 해서 당장 채택하지 않았다.
그 뒤 구글(ADR 0019)과 애플(ADR 0020)은 ID token을 JWKS로 검증하는 방식으로 붙었고, 카카오만 방식이 달라졌다.

카카오 access token은 발급받은 앱과 무관하게 사용자 정보 API에 쓸 수 있다. 그래서 다른 카카오 앱이
사용자에게서 받은 토큰을 우리 로그인에 넣으면 그 사용자로 로그인된다(토큰 치환, #452).
구글·애플은 `aud`로 이를 막는다.

카카오 콘솔에서 OpenID Connect를 켰고, 소셜 로그인은 아직 배포 전이다.

## 결정

### 앱이 카카오 SDK로 받은 ID token을 서버가 검증한다

- 앱은 카카오 SDK 로그인 결과의 ID token을 `POST /api/v1/auth/login/kakao`에 `{"idToken": ...}`으로 보낸다.
- 서버는 카카오 API를 호출하지 않는다. `https://kauth.kakao.com/.well-known/jwks.json`의 공개키로
  RS256 서명을 확인하고 다음을 검증한다.
  - `iss`가 `https://kauth.kakao.com`
  - `aud`가 `kakao.client-ids`(환경변수 `KAKAO_CLIENT_IDS`)에 등록한 앱 키 중 하나.
    네이티브 SDK 로그인은 네이티브 앱 키가, 웹 로그인은 REST API 키가 `aud`로 들어간다.
  - `exp`가 있고 지나지 않음
  - `sub`가 비어 있지 않음
- `sub`(회원번호)를 `provider_id`로, `nickname` 클레임을 이름으로 쓴다. 닉네임에 동의하지 않으면 기본 이름으로 시작한다.
- 공개키는 `NimbusJwtDecoder`가 내려받아 캐시하고, 처음 보는 `kid`가 오면 다시 받는다.
  카카오는 공개키를 너무 자주 받으면 요청을 막을 수 있어 캐시가 필요하다.
- 오류: 잘못된 토큰은 `INVALID_KAKAO_TOKEN`(401), 공개키를 받지 못하면 `KAKAO_UNAVAILABLE`(503).

### access token 방식은 남기지 않는다

소셜 로그인이 배포 전이라 구버전 앱이 없다. 두 방식을 함께 받는 전환 기간을 두지 않고 바로 바꾼다.

## 검토한 대안

### access token을 유지하고 `access_token_info`의 `app_id`를 확인 (#452, PR #455)

채택하지 않았다. 토큰 치환은 막지만 로그인마다 카카오 API를 두 번 부르고, 카카오만 검증 방식이 다르게 남는다.

### 인가 코드를 서버가 교환

채택하지 않았다(ADR 0010과 같은 이유).

### `nonce` 검증

지금은 하지 않는다. 구글·애플과 같다. 재생 공격 방어가 필요해지면 세 제공자를 함께 다룬다.

## 결과

### 장점

- 세 제공자의 검증 방식과 설정(`*_CLIENT_IDS`, `jwk-set-uri`), 오류 구분이 같아진다.
- 다른 앱용 토큰을 `aud`로 막는다.
- 로그인 때 카카오 API 호출이 없어 지연과 카카오 장애 영향이 작다.

### 비용과 주의점

- 운영·개발 서버 `/etc/mapmory.env`에 `KAKAO_CLIENT_IDS`(네이티브 앱 키)를 넣어야 한다.
  비어 있으면 모든 카카오 로그인이 `INVALID_KAKAO_TOKEN`으로 거부된다.
- ID token의 `sub`가 `/v2/user/me`의 회원번호와 같다는 전제다. 다르면 이전 방식으로 가입한 카카오 회원은
  다시 로그인할 때 새 회원이 된다. 배포 전이라 영향은 개발 서버의 테스트 계정뿐이다.
- 앱이 ID token을 보내도록 바뀌어야 한다. 서버만 먼저 배포하면 카카오 로그인이 `400`이 된다.
