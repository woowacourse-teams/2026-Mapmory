# ADR 0020. 애플 로그인은 앱이 전달한 identity token을 JWKS로 검증한다

- 상태: 제안
- 날짜: 2026-10-12
- 관련: ADR 0015, ADR 0019

---

## 문제

iOS 앱이 카카오·구글 같은 외부 소셜 로그인을 제공하면 App Store 심사 지침 4.8에 따라
Sign in with Apple(또는 같은 수준의 개인정보 보호 로그인)도 제공해야 한다. 애플 토큰을
어떻게 검증하고, 애플이 토큰에 넣지 않는 이름은 어떻게 받을지 정해야 한다.

## 결정

### 앱이 받은 identity token을 서버가 검증한다 (ADR 0019와 같은 방식)

- iOS 앱은 `ASAuthorizationAppleIDProvider`로 로그인해 `identityToken`을 받고
  `POST /api/v1/auth/login/apple`로 보낸다.
- 서버는 `https://appleid.apple.com/auth/keys`의 공개키로 서명을 확인하고 다음을 검증한다.
  - `iss`가 `https://appleid.apple.com`
  - `aud`가 `apple.client-ids`(환경변수 `APPLE_CLIENT_IDS`)에 등록한 값 중 하나. iOS 네이티브 로그인은 앱 번들 ID
  - `exp`가 있고 지나지 않음
  - `sub`가 비어 있지 않음
- `sub`를 `provider_id`로 쓴다. 같은 개발자 팀 안에서 바뀌지 않는다. 이메일은 "나의 이메일 가리기"로
  중계 주소일 수 있어 쓰지 않는다.
- 공개키 캐시와 장애 처리(`APPLE_UNAVAILABLE`, 503)는 구글과 같다.

### 이름은 받지 않는다

애플은 identity token에 이름을 넣지 않고, 이름(`fullName`)은 처음 인증할 때만 앱에 준다.
앱이 그 값을 서버로 넘기는 방법도 있지만, 한 번 놓치면 다시 받을 수 없고 클라이언트 값을 저장하게 된다.
이름은 사용자가 앱에서 직접 바꾸는 것으로 하고(회원 이름 변경 API), 애플 회원은 다른 제공자에서
이름이 없을 때와 같은 기본 이름(예: `회원58213`)으로 시작한다. 이 방식이면 앱은 `fullName` 범위를
요청하지 않아도 된다.

### 회원 결정과 토큰 발급은 공유한다

`AuthProvider.APPLE`과 `SocialIdentityPort` 구현(`AppleIdentityAdapter`)만 추가한다. 스키마 변경은 없다.
같은 사람의 카카오·구글·애플 회원은 별개 회원이다.

## 검토한 대안

### authorization code를 서버가 교환

채택하지 않았다. `.p8` 키로 client secret(JWT)을 만들어야 하고, 로그인마다 애플 호출이 생긴다.
로그인 자체에는 identity token 검증으로 충분하다. 다만 아래 "남은 일"의 토큰 철회에는 이 방식이 필요하다.

## 결과

### 장점

- 로그인에 애플 API 호출과 비밀 키가 필요 없다. 서버 설정은 번들 ID 하나다.

### 비용과 주의점

- 운영·개발 서버 `/etc/mapmory.env`에 `APPLE_CLIENT_IDS=<번들 ID>`를 넣어야 한다. 비어 있으면
  모든 애플 로그인이 `INVALID_APPLE_TOKEN`으로 거부된다.
- `nonce`는 아직 검증하지 않는다. 탈취한 identity token 재사용 위험은 토큰 만료(`exp`)와
  `aud` 검증으로 줄인다. 앱이 nonce를 보내게 되면 검증을 추가한다.

### 남은 일

- 심사 지침 5.1.1(v)에 따라 회원 탈퇴를 제공해야 하고, 애플 회원이 탈퇴하면 애플 토큰을
  철회(`/auth/revoke`)해야 한다. 철회에는 authorization code 교환과 Team ID·Key ID·`.p8` 키가
  필요하다. 현재 회원 탈퇴 API가 없으므로 탈퇴를 만들 때 함께 다룬다.
