> 2026-10-26 통합본의 검증 기록이다. 2026-10-27 로그인 PR 분리 이후에는 그룹 샘플 화면·도메인·계측 테스트가 별도 PR로 이동하므로 전체 테스트 수가 다르다.

# 그룹 탭 소셜 로그인 검증 (2026-10-26)

작업 브랜치: `and/feat/group-social-login`. 최신 `origin/develop`에서 분기하고 기존 소셜 인증·그룹 미리보기·GA·게스트 토큰 복구 PR의 코드를 통합했다.

## 확인한 결과

- `./gradlew :shared:jvmTest :androidApp:assembleDebug`: 434개 JVM 테스트, 실패 0, 오류 0. Android debug APK 빌드 성공.
- `:androidApp:assembleDebugAndroidTest`: 테스트 APK 빌드 성공.
- Android 35 Small_Tablet AVD의 화면을 휴대폰 크기(1080×2340, 420dpi)로 설정해 `GroupSocialLoginTest` 4개 통과. 마지막 실행은 80.76초.
- iOS Simulator `Mapmory` Debug 앱 전체 빌드 성공. Kotlin 공통 코드, Swift 카카오 ID token, Keychain pending 상태 브리지 포함. 실기기 iOS 인증은 실행하지 않았다.
- `git diff --check` 통과. 백엔드 소스 변경 없음.

## 회귀 범위

공통 테스트는 토큰 저장 실패 후 새 토큰 유지, 저장만 재시도, 회원 확인만 재시도, 응답 유실 후 명시적 결과 확인, 재시작 pending 복구, 옛 게스트 accessToken을 새 refresh 저장 증거로 오인하지 않기, 저장소 읽기 실패 때 신규 게스트 생성/오프라인 캐시 삭제 금지, 진행 중 요청·대기 저장과 계정 전환 상호 배제, 중복 로그인 차단, 무효 refresh의 명시적 복구 동의, 세 제공자의 요청 DTO/Bearer, 실제 HttpSend의 만료 토큰 재시도 1회를 포함한다. 기존 게스트 갱신 회귀도 함께 실행했다.

에뮬레이터 UI 테스트는 운영 서버와 실제 소셜 계정을 사용하지 않는 격리된 fixture로 다음을 확인했다.

1. 앱 초기 지도에는 소셜 로그인 버튼이 없고 그룹 탭에서만 표시된다. 로그인 완료 후 그룹 목록으로 돌아오고 login GA는 1회다.
2. 인증 취소는 게스트를 유지하고 login/app_error를 만들지 않는다.
3. 저장 실패에서는 로그인 성공 GA를 만들지 않는다. 결과 확인으로 재개해 SDK 로그인을 반복하지 않는다.
4. 그룹 목록 실패는 목록만 재시도하고 인증을 반복하지 않는다.

## 일반 앱 및 남은 검증

일반 `MainActivity`도 에뮬레이터에서 실행했다. 첫 화면은 개인 기록 소개/지도이며 소셜 로그인 화면이 아니다. 소개를 닫고 그룹 탭으로 이동하는 실제 경로를 확인했다.

현재 일반 앱의 개발 서버 계정 조회에서는 실패 안내가 표시되었다. 원격 서버의 정확한 배포 버전·실패 원인은 이번 UI 검사에서 확정하지 않았다. 로컬 Google 웹 클라이언트 ID와 카카오 네이티브 앱 키도 설정되어 있지 않다. Google 버튼은 설정 안내를 표시하며 가짜 로그인 성공을 만들지 않는다.

따라서 실제 Google/Kakao/Apple 계정 → 배포 서버 → 토큰 저장 → 실제 그룹 목록의 종단 성공, 실제 업로드 중 전환, 실제 강제 종료·키체인 잠금, Firebase/GA4 수신은 미검증이다. 서버 audience·제공자 콘솔 설정과 backend-develop API 배포를 맞춘 뒤 실제 계정 검증이 필요하다. 정확한 응답 유실 복구에는 서버 멱등/결과 조회 계약 보강이 남아 있다.

Android 37 프리뷰 AVD에서는 Espresso가 `InputManager.getInstance`를 찾지 못해 테스트 실행이 실패했다. 이를 앱 인증 실패로 간주하지 않고 설치된 Android 35 환경에서 네 시나리오를 검증했다.

## 증거

- JVM 결과: `shared/build/reports/tests/jvmTest/index.html`.
- 로컬 명령 로그: `/private/tmp/mapmory-social-final-tests.log`, `/private/tmp/mapmory-social-emulator-final.log`, `/private/tmp/mapmory-social-ios-final.log`.
- 화면 캡처: 이 채팅 시각화 폴더의 `social-login/social-login-verification/`에 로그인 안내, 성공 목록, 저장 재시도, 목록 재시도 화면을 보관했다. `social-login/actual-app-unconfigured.png`는 실제 앱의 미설정 안내다.
- 설계 및 API 계약: [group-social-login.md](../auth/group-social-login.md).
