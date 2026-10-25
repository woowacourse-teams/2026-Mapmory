# 소셜 로그인 초기 연결 · 2026-10-23

설정의 계정 연결 패널에서 Android는 Google/카카오, iOS는 Google/카카오/Apple을 선택한다. 각 SDK 인증 증명을 공통 `SocialCredential`로 반환한다. 제공자 인증 성공은 맵모리 계정 로그인 성공이 아니며, 현재는 서버 연동 대기 안내를 표시한다. 게스트 세션/기록은 교체하지 않는다.

## 구현

- Android Google: Credential Manager의 명시적 Sign in with Google 요청 → ID token.
- Android 카카오: 카카오톡으로 인증, 사용자가 취소하면 종료. 카카오톡 인증의 다른 실패는 카카오계정으로 대체. 액세스 토큰 반환.
- iOS Google: GoogleSignIn SPM → ID token / serverAuthCode.
- iOS 카카오: KakaoSDKUser SPM, 카카오톡 미설치 시 카카오계정. 액세스 토큰 반환.
- iOS Apple: AuthenticationServices, SHA-256 nonce, ID token / 인가 코드 / 최초 이름. 원본 nonce를 공통 증명에 포함해 추후 서버 검증에 사용한다.
- 이름은 null일 수 있다. 이후 로그인에서 null 이름으로 서버의 기존 이름을 덮어쓰면 안 된다.
- 취소/실패/진행 중 UI와 중복 요청 방지. 화면을 벗어나면 늦은 콜백은 화면 상태를 변경하지 않는다.
- 공통 로그인 계층은 증명을 로그·디스크·맵모리 토큰 저장소에 저장하지 않는다. 제공자 SDK 자체의 세션 저장은 각 SDK가 관리한다.

## 설정값

Android `local.properties`:

```
MAPMORY_GOOGLE_WEB_CLIENT_ID=<서버 검증 대상 Web OAuth client ID>
MAPMORY_KAKAO_NATIVE_APP_KEY=<Kakao native app key>
```

Google Android 클라이언트에는 패키지명 및 디버그/배포 서명을 등록하고, 카카오에는 패키지·키해시와 `kakao{앱키}://oauth` redirect를 등록한다.

iOS 각 빌드 설정/개인 xcconfig에서:

```
MAPMORY_GOOGLE_IOS_CLIENT_ID=<iOS OAuth client ID>
MAPMORY_GOOGLE_WEB_CLIENT_ID=<서버용 Web client ID>
MAPMORY_GOOGLE_REVERSED_CLIENT_ID=<Google redirect URL scheme>
MAPMORY_KAKAO_NATIVE_APP_KEY=<Kakao native app key>
```

Debug/Internal/Release Info.plist에 동일 키와 URL scheme을 연결했다. Apple 로그인 entitlements를 추가했으며 Apple Developer의 해당 App ID와 provisioning profile에도 Sign in with Apple이 활성화되어야 실제 기기에서 실행된다. 제공자 콘솔 설정은 변경하지 않았다.

## 서버 명세가 나오면 연결할 부분

`SocialCredential` → 맵모리 인증 교환 API → 토큰/회원 정보 저장. API 경로와 DTO는 만들지 않았다. 게스트 승격과 기존 계정 로그인 구분, 게스트 기록 처리, 계정별 캐시/업로드 격리, 로그인 후 원래 작업 복귀, 로그아웃/탈퇴는 서버 계약 이후 연결한다. 현재 인증 성공 콜백은 세션을 변경하지 않는다.

## 게스트 세션 조사

증상이 발생한 기기 로그를 확보한 것은 아니다. 다음 코드 경로는 확인하고 수정했다.

1. iOS `SecItemCopyMatching`의 모든 실패를 nil로 반환해 잠금·일시 장애·손상도 토큰 부재로 취급했다. 결과적으로 새 게스트 발급 경로에 들어갈 수 있었다. 실제 미존재(`errSecItemNotFound`)만 nil이며 다른 오류는 전달한다. Android도 손상된 저장값을 부재로 취급하지 않는다.
2. 회전형 refresh 성공 뒤 디스크 저장이 실패하면 메모리에 이전 토큰이 남았다. 새 토큰을 먼저 메모리에 보존하고 다음 요청에서 저장만 재시도한다. 회전된 이전 refresh token을 재사용하지 않는다.
3. 기존 iOS 버전에서 Keychain 쓰기 실패 시 UserDefaults에만 새 토큰을 쓰고, 다음 실행에는 오래된 Keychain 토큰을 우선 읽을 수 있었다. 남아 있는 legacy fallback을 우선 마이그레이션하며 새 plaintext fallback은 생성하지 않는다.
4. 컨테이너 초기화의 저장소 조회 실패로 기록 캐시를 비우지 않는다. `storage_read`/`storage_write` stage로 기존 인증 오류 계측에 전달하며 토큰 내용은 기록하지 않는다.

저장 장치가 계속 실패하거나 저장 전에 프로세스가 종료되면, 메모리에만 있던 회전 토큰은 복구를 보장할 수 없다. 서버에서 이미 폐기한 토큰도 이 변경으로 복구하지 않는다. 서버 환경별 저장소 이름은 그대로 유지했다.

## 검증 범위

인증 관련 JVM 테스트 18개(저장소 읽기 실패, 토큰 회전 후 저장 실패, 동시 401, 컨테이너 구성), Android 컴파일, iOS Simulator 앱 전체 빌드가 통과했다. 제공자 콘솔의 앱 키/서명/리다이렉트와 실제 기기 계정 인증은 별도로 확인해야 한다. 테스트 실행은 실제 계정을 생성하거나 토큰을 변경하지 않는다.

SDK 연결 기준: [Google Android](https://developer.android.com/identity/sign-in/credential-manager-siwg), [Google iOS](https://developers.google.com/identity/sign-in/ios/sign-in), [Kakao Android](https://developers.kakao.com/docs/ko/kakaologin/android), [Kakao iOS](https://developers.kakao.com/docs/ko/kakaologin/ios), [Apple](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple).


## PR 분리 후 확인할 사항

- 게스트 토큰 저장·갱신 수정은 기존 PR #438에서 별도로 관리합니다. 위 조사 내용은 경위 설명이며 이 Draft에는 인증 저장소 수정이 포함되지 않습니다.
- 이 구현은 제공자 인증까지만 다룹니다. 최신 서버 OIDC 계약과의 토큰 형식 대조, 계정 전환 정책, 콘솔 등록값·실기기 검증을 마친 뒤 서버에 연결해야 합니다.
- GA 계측은 그룹·소셜 계측 후속 Draft에서 추가합니다.
