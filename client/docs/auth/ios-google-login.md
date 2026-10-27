# iOS Google 로그인 설정

## 2026-10-27 원인

사용자가 Google 버튼에서 설정 준비 중 안내가 나온다고 보고했다. Google Cloud의 iOS OAuth 클라이언트는 아직 생성하지 않았다고 확인했다. Xcode의 SocialLogin.xcconfig에는 카카오 키만 있었으며 Google iOS/Web Client ID와 callback scheme 값은 정의되지 않았다. Android의 local.properties는 Xcode에 적용되지 않는다. 기존 GoogleService-Info.plist에도 CLIENT_ID/REVERSED_CLIENT_ID는 없다.

## Google Cloud에서 할 일

1. 기존 웹 클라이언트 `601411744421-fdic8ohcmsifv36dbpmk2shf0up1hplj.apps.googleusercontent.com`을 만든 프로젝트를 선택한다.
2. Google Auth Platform → Clients → Create client → Application type: iOS.
3. 이름은 `Mapmory iOS`, Bundle ID는 `com.mapmory.ios`. 콘솔에서 요구하면 App Store ID `6807056166`, Team ID `22YUT65LMV`를 확인해 입력한다.
4. 생성된 iOS Client ID를 복사한다. 웹/Android Client ID를 대신 넣지 않는다.
5. Internal 앱도 사용할 때는 `com.mapmory.ios.internal`용 iOS 클라이언트를 별도로 만든다.
6. Audience가 Testing이면 로그인할 계정이 테스트 사용자로 허용되는지 확인한다.

현재 앱은 Firebase Authentication을 사용하지 않고 GoogleSignIn의 ID token을 맵모리 서버로 전달한다. Firebase Analytics용 plist를 바꾸거나 Firebase Auth를 추가할 필요는 없다.

## 앱에 반영

`client/iosApp/SocialLogin.xcconfig`에서 아래 공개 식별자를 설정한다.

- `MAPMORY_GOOGLE_WEB_CLIENT_ID`: 이미 확인한 웹 Client ID를 반영했다. 서버 GOOGLE_CLIENT_IDS의 audience와 일치해야 한다.
- `MAPMORY_GOOGLE_IOS_CLIENT_ID_PREFIX`: 새 iOS Client ID에서 `.apps.googleusercontent.com`을 제외한 앞부분.
- `MAPMORY_GOOGLE_INTERNAL_IOS_CLIENT_ID_PREFIX`: Internal용 iOS Client ID의 앞부분.

예를 들어 iOS Client ID가 `123-abc.apps.googleusercontent.com`이면 prefix에 `123-abc`를 넣는다. 전체 Client ID와 `com.googleusercontent.apps.123-abc` callback scheme은 같은 값에서 자동 조립된다. Debug/Release는 일반 앱 값을, Internal은 별도 값을 사용한다. 없는 클라이언트에 임의 값을 넣지 않는다. 설정 후 앱을 다시 빌드·설치한다.

공개 Client ID는 버전 관리할 수 있다. Client Secret·토큰·계정 비밀번호는 넣지 않는다.

## 앱 처리

SDK를 호출하기 전에 iOS/Web ID 형식과 중복, 실제 Info.plist의 callback scheme을 검증한다. 누락·불일치면 사용자에게 설정 미완료 안내를 표시하고 인증을 시작하지 않는다. Google SDK의 callback scheme 누락에 따른 Objective-C 예외를 예방한다.

Xcode Console의 SocialLogin 카테고리에는 고정된 원인 코드만 남긴다: `ios_client_missing_or_invalid`, `web_client_missing_or_invalid`, `ios_and_web_clients_identical`, `google_callback_scheme_missing`. ID token, SDK userInfo, 원본 오류 메시지는 기록하지 않는다. 클라이언트 형식 검사만으로 콘솔의 앱 종류·번들 등록을 검증할 수는 없다.

Google 취소는 해당 SDK 오류 도메인과 취소 코드를 함께 확인한다. 성공은 SDK 오류가 없고 ID token이 비어 있지 않을 때만 전달한다. 서버 토큰 저장·회원 확인 이후 맵모리 로그인 성공으로 처리하는 경계는 유지한다.

## 검증

2026-10-27: 설정 검증 16개, Xcode Debug iOS Simulator 전체 앱 빌드 통과. Xcode가 해석한 Debug/Release/Internal 빌드 설정에 테스트 prefix를 주입해 각 Info.plist의 클라이언트·callback scheme·웹 audience 조합을 확인했다. 테스트 prefix는 파일에 저장하거나 실제 인증에 사용하지 않았다. 실제 Debug 앱 번들에는 웹 ID가 반영되어 있으며 미발급 iOS ID는 사전 검증에서 거절된다. Release/Internal 전체 앱 빌드 및 실계정 인증을 검증한 것은 아니다.

설정 회귀 테스트:

```sh
swiftc iosApp/IosSocialLoginLauncher.swift iosApp/tests/GoogleLoginConfigurationTests.swift -o /tmp/mapmory-google-config-tests
/tmp/mapmory-google-config-tests
```

이후 사용자가 생성한 iOS Client ID `601411744421-eps3fftej7393f7ss42feqhb3iucth0j.apps.googleusercontent.com`을 Debug/Release 설정에 반영했다. callback scheme은 `com.googleusercontent.apps.601411744421-eps3fftej7393f7ss42feqhb3iucth0j`이다. Internal용 클라이언트는 아직 미설정이다.

Xcode 빌드 성공과 실제 Google 로그인 성공은 별개다. iPhone에서 Google 인증창 → 서버 교환 → 재시작 유지 → 로그아웃 → 재로그인 복원을 확인해야 한다. Internal과 TestFlight는 각각 해당 번들·서버 설정으로 확인한다.

공식 문서: [iOS 설정](https://developers.google.com/identity/sign-in/ios/start-integrating), [백엔드 인증](https://developers.google.com/identity/sign-in/ios/backend-auth).
