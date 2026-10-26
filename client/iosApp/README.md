# Mapmory iOS 앱

이 디렉터리는 Xcode 앱 진입점입니다. 공통 Compose Multiplatform 코드는 `../shared`에서 framework로 빌드하고, iOS 앱은 이 framework를 연결합니다.

## 초기 설정

1. Xcode 26.4 이상 설치
2. iOS Deployment Target 16.0 설정
3. Xcode에서 `Mapmory.xcodeproj`를 열기
4. 실제 기기 빌드 시 Signing Team과 Bundle Identifier 설정
5. `Mapmory` scheme으로 Simulator 또는 실제 기기 빌드

## Firebase Analytics

- Firebase 프로젝트: `mapmory-analytics-b6a50`
- App Store용 Bundle ID: `com.mapmory.ios`
- 설정 파일: `GoogleService-Info.plist`

Firebase Console에 `com.mapmory.ios` Apple 앱을 등록한 뒤 해당 앱에서 내려받은
`GoogleService-Info.plist`를 이 디렉터리에 둔다. 설정 파일의 `BUNDLE_ID`와 Xcode Release 설정의
`PRODUCT_BUNDLE_IDENTIFIER`가 모두 `com.mapmory.ios`인지 출시 전에 확인한다. 다른 Bundle ID로
등록된 설정 파일의 문자열만 직접 고쳐 사용하지 않는다.

앱은 기능 이용 흐름을 분석하기 위해 Firebase Analytics를 사용하며 광고는 제공하지 않는다. 이벤트 목록과 DebugView 확인 방법은
[`../docs/android-monitoring.md`](../docs/android-monitoring.md)를 참고한다.

## 미디어 권한

- 카메라: `NSCameraUsageDescription`
- 전체 사진 보관함 읽기/쓰기: `NSPhotoLibraryUsageDescription`
- 사진 보관함에 추가만 하기: `NSPhotoLibraryAddUsageDescription`

권한은 앱 실행 시 자동으로 요청하지 않고, 사용자가 카메라나 갤러리 기능을 선택한 시점에
`MediaPermissionManager`를 통해 요청한다. `.limited`는 사용자가 일부 사진만 허용한 상태이므로 전체 허용과
구분해야 한다.

Xcode의 Build Phase가 Gradle `:shared:embedAndSignAppleFrameworkForXcode` 작업을 실행해 공통 Compose UI를
`Shared.framework`로 연결한다.

## 소셜 로그인 설정

Debug/Release/Internal의 공개 카카오 앱 식별자는 `SocialLogin.xcconfig`에서 관리한다. Google iOS Client ID와 URL scheme, 플랫폼별 콘솔 등록 및 서버 audience는 [소셜 제공자 설정](../docs/auth/social-provider-setup.md), [카카오 플랫폼 설정](../docs/auth/kakao-platform-setup.md)을 따른다. Client Secret·사용자 토큰·서명 개인키는 빌드 설정에 넣지 않는다.
