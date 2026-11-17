# iOS Crashlytics 연결

## 확인된 누락과 수정

2026-10-19 코드 점검 당시 iOS 타깃에는 FirebaseAnalytics/Core/RemoteConfig만 있었고 FirebaseCrashlytics 제품 링크 및 dSYM 업로드 Build Phase가 없었다. 기존 Firebase 초기화만으로 Crashlytics가 추가되지는 않는다.

- 기존 firebase-ios-sdk 12.17.0 버전을 유지하고 FirebaseCrashlytics 제품을 앱 타깃에 링크.
- FirebaseApp.configure 이후 Crashlytics 인스턴스 초기화, 타깃 링커에 `-ObjC` 추가.
- Info.plist에서 Release 수집 활성화, Debug/Internal 수집 비활성화 명시. Analytics 설정은 유지.
- 마지막 Build Phase에서 `scripts/upload-crashlytics-symbols.sh` 실행. Release + iphoneos에 한해서 Firebase 공식 run 스크립트 실행.
- Release의 기존 dwarf-with-dsym 설정 유지. configuration별 xcfilelist로 Release 입력(앱 dSYM, dSYM 바이너리/Info.plist, 빌드된 GoogleService-Info.plist, 앱 실행 파일)을 선언. GoogleService-Info.plist를 제외하는 Internal 및 dSYM을 만들지 않는 Debug에는 해당 입력을 요구하지 않는다.

## 검증 범위

빌드 성공은 SDK 연결 검증이며 Firebase 실제 수신 성공을 뜻하지 않는다. 심볼 업로드도 기기 Release 빌드에서 별도로 확인한다. Debug/Internal과 시뮬레이터 빌드에서 업로드를 건너뛰는 것은 의도된 동작이다.

최초 수신은 테스트 전용 기기/빌드에서 다음 순서로 확인한다.

1. Firebase 설정이 있는 수집 활성화된 Release 검증 빌드를 준비하고 dSYM 업로드 로그를 확인한다.
2. 디버거를 분리한 상태에서 명시적인 테스트 크래시를 발생시킨다. 일반 사용자용 화면에는 크래시 버튼을 추가하지 않는다.
3. 앱을 재실행하고 Firebase Console의 iOS Crashlytics에서 앱 버전·크래시 발생 시각·심볼 처리 결과를 확인한다.
4. Swift/native 크래시와 Kotlin/Native 미처리 예외를 별도로 검증한다. 이 변경은 CrashKiOS 등 Kotlin exception bridge를 추가하지 않는다. Kotlin 예외 원인/스택의 가독성까지 보장한 상태가 아니다.

iOS hang은 Android ANR과 다른 영역이다. 이 연결로 MetricKit hang 수집이나 모든 handled exception의 non-fatal 업로드가 구현되지는 않는다. 처리된 기능 오류의 횟수는 `app_error` 이벤트를 참고한다.

공식 문서: [Apple Crashlytics 설정](https://firebase.google.com/docs/crashlytics/ios/get-started), [수집 제어](https://firebase.google.com/docs/crashlytics/ios/customize-crash-reports).

## 2026-11-18 모리 분석용 로그와 키

기본 SDK 연결은 PR [#404](https://github.com/woowacourse-teams/2026-Mapmory/pull/404)로 2026-10-20 `develop`에 병합되었다. 2026-11-18 점검 시 `main`과 오래된 `release/ios-app-store`에는 이 연결이 없었다. 출시 여부는 실제 배포에 사용한 커밋으로 별도 판단한다.

`FirebaseAnalyticsLogger`가 기존 Analytics 이벤트를 보낼 때 Crashlytics 수집이 활성화돼 있으면 커스텀 로그와 키도 함께 기록한다. Debug/Internal의 기존 수집 비활성화 정책은 유지한다. `isEnabled=false`인 로거는 두 경로 모두 호출하지 않는다.

- 브레드크럼은 기존 Firebase Analytics SDK 연동으로 수집한다. 별도의 모든 터치 기록 기능을 추가하지 않는다.
- 커스텀 로그는 이벤트 이름과 허용된 파라미터만 담는다. 기록/회원 ID, 원문 오류 메시지, 기록 제목·본문, 토큰, URL, 파일 경로, 좌표는 커스텀 로그·키에 복제하지 않는다. 기존 Analytics 전송 내용은 이 변경으로 수정하지 않으므로 자동 브레드크럼의 파라미터 정책은 기존 Analytics 정책을 따른다.
- 화면 진입 이벤트의 `screen_name`은 현재 화면으로 유지한다. 화면 이벤트에서 값이 누락되거나 잘못되면 `unknown`으로 바꾼다.
- `last_event`, `last_operation`, `last_action`, `last_outcome`, `last_error_type` 등은 마지막 이벤트를 설명한다. 이전 오류·작업이 새 이벤트의 상태로 남지 않도록 없는 항목은 매 이벤트마다 `unset`으로 덮어쓴다. 진행 중인 작업 전체나 동시 실행 작업 목록을 의미하지 않는다.
- `setUserProperty("is_team", value)`는 Crashlytics의 같은 키에도 `true`/`false`를 기록한다. 삭제·잘못된 값은 `unknown`으로 바꾸며 일반 사용자로 간주하지 않는다. 다른 사용자 속성은 복제하지 않는다.
- 상태 이름은 제한된 ASCII 식별자(최대 80바이트), 수치는 64비트 정수로 제한한다. 일반 수치는 음수를 거부하고 네이티브 오류 코드만 음수를 허용한다. 로그 한 줄은 최대 1,024바이트다.
- Analytics의 `event_schema_version=2`와 별도로 `crash_context_schema_version=1`을 남긴다.

이 연결은 새 non-fatal 예외를 생성하지 않는다. Kotlin 예외를 문자열로 받아 가짜 Swift 스택을 만들지 않으며, 실제 Kotlin/Native 예외 원인 연결은 여전히 별도 범위다.

### 자동 검증

Mac 또는 Swift가 설치된 환경에서 다음 명령으로 이벤트 컨텍스트의 변환·필터링·이전 상태 제거·크기 제한을 검증한다. Firebase SDK와 인증, 네트워크가 필요 없는 테스트다.

```sh
sh client/iosApp/scripts/test-crashlytics-context.sh
```

iOS PR CI와 수동 앱 빌드 CI에도 같은 테스트가 포함된다. 이 테스트는 Firebase 수신이나 실제 SDK 호출을 검증하지 않는다. 전체 앱 컴파일은 기존 iOS CI의 수동 `Build unsigned iOS app`으로 따로 확인한다.

### 최초 수신과 BigQuery 확인

1. 실제 배포 대상 커밋을 포함하는 Release 검증 빌드에서 Crashlytics 수집과 dSYM 업로드 성공을 확인한다. Debug/Internal은 수집이 꺼져 있다.
2. 테스트 기기에서 팀 기기 설정을 켜고 기록 열기·사진 선택·공유 등 기존 행동을 수행한다.
3. 임시 검증 코드로 명시적인 테스트 크래시를 발생시킨다. 디버거를 분리하고 수행하며 일반 배포에는 테스트 코드를 포함하지 않는다.
4. 앱을 다시 실행한 뒤 iOS Crashlytics 이벤트에서 실제 스택의 심볼, `breadcrumbs`, 커스텀 로그, `screen_name`, `is_team`, `last_event`를 확인한다.
5. BigQuery 일일 내보내기 후 `firebase_crashlytics.com_mapmory_ios_IOS`의 같은 `event_id`에서 `breadcrumbs`, `logs`, `custom_keys`, `threads`를 확인한다. 수집되지 않은 과거 로그·키는 소급해서 생성되지 않는다.

2026-11-18 Firebase 콘솔에서 Android·iOS 두 앱의 일일 내보내기와 세션 포함을 켰고 `firebase_crashlytics`, `firebase_sessions`가 `asia-northeast3`에 생성된 것을 확인했다. 스트리밍은 꺼져 있다. 첫 배치 내보내기와 이벤트별 필드 수신은 위 절차로 별도 검증한다.
