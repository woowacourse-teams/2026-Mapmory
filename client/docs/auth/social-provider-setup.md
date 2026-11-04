# Android 소셜 로그인 개발 설정

2026-10-26 기준 `origin/backend-develop` 2c6445eb의 AuthController, LoginResponse, MemberResponse, GroupSummaryResponse와 대조했다. 이 확인은 소스 계약 확인이며 개발 서버 배포 확인은 아니다.

## 로컬 구현

- 브랜치: `and/feat/social-login`. 통합본은 `and/backup/social-login-integration-20261027`에 보관했다. 기존 `and/feat/group-social-login`의 서버 연동과 세션 복구를 재사용한다. 기존 브랜치는 보존하고 push하지 않는다.
- 그룹 탭에서만 인증을 안내한다. 로그인 패널은 social-auth-bootstrap의 계정 연결 제목, 세로 간격과 outlined 제공자 버튼 구성을 사용한다. 기록 전환 안내와 오류 복구 컨트롤은 유지한다.
- Google/Kakao: POST `/api/v1/auth/login/{provider}` body `{"idToken":"SDK ID token"}`.
- Apple: POST `/api/v1/auth/login/apple` body `{"identityToken":"SDK identity token"}`.
- 게스트 Bearer를 보내 기록 승격을 요청한다. 토큰 저장과 `/members/me` 확인까지 마쳐야 성공 처리한다.
- 기존 계정 로그인 시 `guestAbandoned` 안내, 저장 실패 재개, 취소 분리, refresh 동시 요청 조정, GA 중복 방지는 기존 구현을 유지한다.
- 디버그 기본 API: `https://dev-api.map-mory.com/api/v1`. local.properties의 MAPMORY_DEBUG_API_BASE_URL이 있으면 그 값이 우선한다.

## 1. Google 콘솔 (프로젝트 관리자)

1. Google Cloud Console에서 기존 Mapmory 프로젝트를 선택한다. Google Auth Platform의 Branding/Audience/Clients를 확인한다.
2. Android OAuth 클라이언트에 패키지 `com.mapmory.android`와 아래 debug SHA-1 조합을 등록한다. 기존 항목이 있으면 일치 여부를 확인한다.
3. 같은 프로젝트의 웹 애플리케이션 OAuth Client ID를 확인한다. 앱의 server client ID에는 이 웹 ID를 넣는다. Android Client ID나 Client Secret을 넣지 않는다.
4. 테스트 모드에서 계정 접근이 제한되면 Audience의 테스트 사용자에 검증 계정을 추가한다.
5. 개발 서버 GOOGLE_CLIENT_IDS에 동일한 웹 Client ID가 허용되어 있는지 서버 담당자가 확인한다.

현재 개발 머신 debug SHA-1:

```text
30:34:C0:B5:39:56:45:F5:AB:67:E2:24:0A:68:B9:17:43:F8:50:4E
```

## 2. 카카오 콘솔 (앱 관리자)

1. Kakao Developers에서 기존 Mapmory 앱을 선택한다.
2. 앱 > 플랫폼 키 > 네이티브 앱 키에서 Android 정보에 패키지 `com.mapmory.android`와 아래 키 해시를 등록한다.
3. 카카오 로그인 활성화 및 OpenID Connect 활성화를 확인한다. 현재 서버는 accessToken이 아닌 ID token을 받는다.
4. 네이티브 앱 키를 확인하고 개발 서버 KAKAO_CLIENT_IDS에도 동일 키가 허용되어 있는지 확인한다.
5. 테스트 앱이라면 테스트 계정이 앱의 팀/테스트 권한에 포함되는지 확인한다.

현재 개발 머신 debug 키 해시:

```text
MDTAtTlWRfWrZ+IkCmi5F0P4UE4=
```

## 3. 앱 로컬 설정

`client/local.properties`의 기존 sdk.dir 등은 유지하고 다음 실제 값을 추가한다. 파일은 커밋하지 않는다.

```properties
MAPMORY_GOOGLE_WEB_CLIENT_ID=<웹 OAuth Client ID>
MAPMORY_KAKAO_NATIVE_APP_KEY=<카카오 네이티브 앱 키>
MAPMORY_DEBUG_API_BASE_URL=https://dev-api.map-mory.com/api/v1
```

Client Secret/관리자 키는 모바일 앱에 넣지 않는다. 설정 후 APK를 다시 빌드해야 리소스에 반영된다.

Internal 빌드는 패키지가 `com.mapmory.android.internal`이며 별도 서명 설정을 사용한다. Play 배포는 Play App Signing 인증서가 사용된다. 위 debug 지문을 그대로 쓰지 말고 빌드별 실제 패키지·인증서를 등록한다.

## 4. 서버 담당자 확인

개발 서버에 backend-develop 인증 규격이 배포되어 있고 GOOGLE_CLIENT_IDS/KAKAO_CLIENT_IDS가 설정되어 있어야 한다. 빈 값이면 각각 INVALID_GOOGLE_TOKEN/INVALID_KAKAO_TOKEN으로 거절한다. 쉼표로 여러 audience를 허용할 수 있다. 이 작업에서는 서버 설정이나 배포를 변경하지 않았다.

## 검증

설정 후 Google/카카오 인증 → 맵모리 토큰 교환 → 회원 provider 확인 → 그룹 목록 → 앱 재시작 유지 순서로 확인한다. 신규 소셜 계정의 게스트 승격과 기존 소셜 계정으로 전환은 테스트 기록으로 각각 검증한다. 단위 테스트/모의 UI 성공은 실제 제공자 인증이나 개발 서버 배포 성공을 의미하지 않는다.

## 공식 문서

- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://firebase.google.com/docs/auth/android/google-signin
- https://developers.kakao.com/docs/ko/kakaologin/prerequisite
- https://developers.kakao.com/docs/en/android/getting-started

### 이번 브랜치 검증 결과

- `:shared:jvmTest`: 434 tests, failures 0, errors 0.
- `:androidApp:assembleDebug`, `:androidApp:assembleDebugAndroidTest`: 성공.
- Android API 35 Small_Tablet의 `GroupSocialLoginTest`: 모의 인증 UI 테스트 4개 통과.
- Google/Kakao 설정값이 없어 실제 제공자 로그인 및 개발 서버 토큰 교환은 미검증이다.
