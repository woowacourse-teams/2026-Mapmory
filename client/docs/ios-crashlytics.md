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
