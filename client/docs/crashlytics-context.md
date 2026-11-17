# Android·iOS 크래시 컨텍스트

## 연결 상태와 목적

Android의 기본 Crashlytics는 기존 Gradle 플러그인·SDK·`google-services.json`으로 연결되어 있다. 2026-11-18 Release 의존성을 조회해 Firebase BoM 34.18.0이 `firebase-crashlytics:20.1.0`을 선택하는 것을 확인했다. Firebase 프로젝트는 `mapmory-analytics-b6a50`, 패키지는 `com.mapmory.android`다. SDK 버전을 변경하는 작업은 아니다.

iOS 기본 SDK·초기화·심볼 업로드는 [#404](https://github.com/woowacourse-teams/2026-Mapmory/pull/404)로 `develop`에 포함되어 있다. `main`에는 해당 기본 연결이 아직 없었다. 실제 배포 앱에 포함되는지는 출시 커밋을 확인한다.

이번 변경은 두 플랫폼의 기존 `FirebaseAnalyticsLogger`에서 Crashlytics 커스텀 로그와 키를 함께 기록한다. 기존 Analytics 이벤트는 자동 브레드크럼의 원천으로 유지한다. 원래 수집하지 않은 과거 행동은 복원되지 않는다.

## 공통 계약

- Analytics가 활성화되어 Firebase 초기화에 성공한 경로에서만 연결한다. Crashlytics 수집이 활성화돼 있을 때만 커스텀 컨텍스트를 기록한다.
- Android Debug의 수집 비활성화는 유지하고 Internal에도 비활성 메타데이터를 명시한다. Internal은 Firebase 자동 초기화 제공자도 제거한다. iOS Debug/Internal의 비활성 정책도 유지한다.
- `screen_name`은 `app_screen_view` 이벤트에서 현재 화면으로 갱신한다. 값이 없거나 형식이 틀린 화면 이벤트는 `unknown`으로 갱신한다.
- `is_team`은 같은 이름의 Analytics 사용자 속성을 설정할 때 Crashlytics에도 기록한다. `true`와 `false`만 그대로 사용하고 삭제·잘못된 값은 `unknown`으로 처리한다.
- `last_event`와 `last_operation`, `last_action`, `last_outcome`, `last_error_type`, `last_http_status` 등은 마지막 이벤트의 상태다. 새 이벤트에 없는 상태는 `unset`으로 갱신한다. 진행 중인 모든 동시 작업을 나타내지 않는다.
- 허용한 상태 이름과 수치만 복제한다. 상태 값은 최대 80바이트 ASCII 식별자, 수치는 64비트 정수다. 일반 수치는 음수를 거부하고 네이티브 플랫폼 오류 코드만 음수를 허용한다. 로그는 한 줄 1,024바이트 이내다.
- 커스텀 컨텍스트에는 ID·기록 내용·원문 오류·토큰·URL·경로·좌표를 복제하지 않는다. 자동 Analytics 브레드크럼의 파라미터 정책은 기존 Analytics 정책을 따른다.
- `event_schema_version=2`, `crash_context_schema_version=1`을 두 플랫폼에서 동일하게 사용한다.
- 이 연결은 별도의 `recordException`/`record(error:)` 보고를 생성하지 않는다. 원래 예외가 없는 문자열로 가짜 스택을 만들지 않는다.

## 검증

Android 로컬 테스트와 빌드:

```sh
cd client
./gradlew :androidApp:dependencyInsight --dependency firebase-crashlytics --configuration releaseRuntimeClasspath
./gradlew :androidApp:testDebugUnitTest :androidApp:assembleRelease :androidApp:processInternalMainManifest
```

Windows에서는 `gradlew.bat`을 사용한다. `testDebugUnitTest`는 Firebase에 데이터를 보내지 않고 필터링, 상태 초기화, 수치 경계와 로그 크기를 검증한다. Android PR CI에도 포함된다. Release/Debug/Internal의 병합된 Manifest에서 수집 정책을 별도로 확인한다. Release는 기본 수집 활성화, Debug/Internal은 명시적 비활성화다.

iOS 컨텍스트 테스트:

```sh
sh client/iosApp/scripts/test-crashlytics-context.sh
```

실제 수신 검증은 수집이 활성화된 기기 또는 에뮬레이터 빌드에서 `is_team=true`로 표시한 뒤 행동을 수행하고 테스트 크래시를 발생시키는 방식으로 진행한다. 테스트 코드는 검증 APK에만 임시로 넣고 PR·일반 배포에 포함하지 않는다. 앱 재실행 후 Firebase에서 스택, 기기, 브레드크럼, 커스텀 로그와 키를 확인한다. BigQuery는 일일 내보내기 완료 후 같은 `event_id`를 확인한다.

iOS 기기·심볼 검증 조건은 [iOS Crashlytics 연결](ios-crashlytics.md)을 참고한다. 자동 테스트·앱 컴파일·Firebase 수신·BigQuery 내보내기 검증은 각각 구분해서 보고한다.
