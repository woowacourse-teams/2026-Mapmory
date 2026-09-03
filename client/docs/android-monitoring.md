# 모바일 모니터링 기준

## 목적

Mapmory Android·iOS 앱의 기능 오류와 성능 저하를 같은 기준으로 확인한다.
CI·Android Vitals·Logcat·Xcode Console·System Trace를 기본으로 사용하고, Android·iOS의 사용자 행동은 Firebase Analytics로 보조 관측한다.

모니터링은 사용자 행동 분석과 구분한다.

- 모니터링: 앱이 안정적으로 실행되고 빠르게 반응하는지 확인한다.
- 분석: 사용자가 어떤 기능을 사용하는지 확인한다.

## 1차 완료 범위

- PR 단계의 CI 검증 방법을 정의했다.
- 사진 추천 흐름의 기존 `MapmoryPhotoPerf` 로그와 측정 문서를 연결했다.
- iOS 사진 선택·추천 흐름에도 동일한 `MapmoryPhotoPerf` 측정 로그를 추가했다.
- Android 앱 시작 시간을 cold start와 hot start로 반복 측정하는 로컬 스크립트를 추가했다.
- Android·iOS 핵심 사용자 행동을 Firebase Analytics 이벤트로 기록하는 공통 인터페이스와 플랫폼별 구현을 연결했다.
- 지도 선택 정확성, 사진 추천, 기록 저장을 우선 모니터링 대상으로 정했다.
- 개인정보를 포함하지 않는 로그 규칙을 정했다.
- Android는 Firebase 설정 파일이 없는 개발·CI 환경에서 Analytics 어댑터가 no-op으로 동작한다. iOS는
  `GoogleService-Info.plist`가 앱 번들에 포함되어야 Firebase 초기화를 검증할 수 있다.

Firebase Analytics는 팀 공용 Firebase 프로젝트 `Mapmory Analytics`
([콘솔](https://console.firebase.google.com/project/mapmory-analytics-b6a50/overview))에 연결했다.
Android 앱은 `com.mapmory.android`, iOS 앱은 `com.mapmory.ios`로 등록되어 있으며, 두 앱의
이벤트는 같은 프로젝트의 Analytics에서 앱별로 확인한다.

### iOS Firebase Analytics 식별자 변경 이력

- 이전 식별자: `com.mapmory.ios3` (테스트용 Firebase 앱)
- 현재 식별자: `com.mapmory.ios` (현재 iOS 앱)
- 기존 `ios3` 이벤트 데이터는 과거 테스트 기록으로 보존하고, 현재 `ios` 데이터와 합산하지 않는다.
- Git 커밋 이력에 남은 `ios3` 문자열은 과거 설정 기록이므로 삭제하거나 재작성하지 않는다.
- Firebase·GA4 대시보드에서는 현재 사용자 행동을 `com.mapmory.ios` 앱 스트림으로만 조회한다.

이 문서는 현재 MVP의 개발·출시 전 검증 기준이다. 지도 전환을 자동 반복하는 Macrobenchmark와 원격 오류 수집은 별도 도입 조건이 충족될 때 추가한다.

## 모니터링 계층

| 시점 | 도구 | 확인 대상 |
| --- | --- | --- |
| PR 병합 전 | GitHub Actions CI | 테스트, Lint, Debug 빌드 |
| 개발 중 | `MapmoryPhotoPerf`, Logcat, `android.os.Trace` | 사진 조회·EXIF·추천 단계별 시간 |
| Android·iOS 사용자 행동 분석 | Firebase Analytics, DebugView, Analytics 대시보드 | 화면 진입, 지도 선택, 사진 추천, 기록 저장, 하단 탭 사용 |
| iOS 개발 중 | `MapmoryPhotoPerf`, Xcode Console | PHPicker·PhotoKit 사진 선택·추천 시간 |
| 성능 조사 | System Trace·Perfetto·Macrobenchmark | 앱 시작, 지도 전환, UI 응답성 |
| Play 배포 후 | Play Console Android Vitals | 크래시, ANR, 시작 시간, 렌더링, 메모리 |

CI 실행 방법은 [ci.md](./ci.md), 사진 측정 결과는 [photo-loading-benchmark.md](./performance/photo-loading-benchmark.md)에 기록한다.

## 우선 모니터링 항목

### 1. 안정성

| 흐름 | 기준 | 확인 방법 |
| --- | --- | --- |
| 앱 실행 | 크래시 없이 지도 화면 표시 | 수동 스모크 테스트, Android Vitals |
| 지도 선택 | 선택 후 앱이 종료되지 않고 다음 화면 이동 | Compose 계측 테스트, 수동 확인 |
| 기록 저장 | 성공 또는 사용자에게 이해 가능한 오류 표시 | 계측 테스트, 서버 응답 로그 |
| 사진 추천 | 권한·빈 결과·EXIF 누락 상황에서 크래시 없음 | 계측 테스트, 실기기 확인 |

크래시 또는 ANR이 발생하면 기능 성공으로 보지 않는다. Play에 배포된 버전은 Android Vitals에서 사용자 체감 크래시율과 ANR을 확인한다.

### 2. 사용자 흐름 성능

다음 구간을 별도의 측정 단위로 본다.

| 측정 이름 | 시작 | 종료 | 주요 값 |
| --- | --- | --- | --- |
| `app_startup` | 앱 실행 | 첫 화면 표시 | 전체 시간 |
| `map_scope_change` | 시·도 선택 | 상세 지도 표시 | 전체 시간, 선택 성공 여부 |
| `map_location_select` | 지도 Polygon 터치 | 기록 화면 표시 | 전체 시간, regionCode 일치 여부 |
| `photo_recommend` | 추천 버튼 터치 | 추천 결과 표시 | 전체 시간, EXIF 읽기 수, 캐시 재사용 수, 결과 수 |
| `photo_preview` | 사진 선택 | 미리보기 표시 | 전체 시간, 성공 여부 |
| `record_save` | 저장 버튼 터치 | 저장 성공 또는 오류 표시 | 전체 시간, 성공 여부 |

시간은 평균만 사용하지 않고 동일 조건에서 반복 측정한 중앙값과 최댓값을 함께 기록한다.
첫 기준선이 없는 항목은 임의의 절대 시간보다 동일 기기·동일 데이터의 이전 측정 대비 20% 이상 느려졌는지를 먼저 본다.

사진 추천은 시간만으로 캐시 효과를 판단하지 않는다. 다음 값도 함께 확인한다.

```text
previous_photos > 0
exif_reads 감소
reused_coordinates 증가
recommended_photos 결과 확인
```

### 3. 지도 선택 정확성

성능과 별개로 다음 조건을 항상 만족해야 한다.

- 화면에 표시된 지역과 기록 작성 화면의 지역명이 일치한다.
- 선택한 Polygon의 canonical `regionCode`가 앱 `Location`과 일치한다.
- 시·도에서 상세 지도로 이동한 뒤 뒤로가기를 하면 원래 시·도 지도로 돌아간다.
- 지역 경계선이나 작은 지역을 눌러도 앱이 종료되지 않는다.

이 항목은 시간보다 자동화 테스트의 성공 여부를 우선한다. 잘못된 지역으로 이동하면 성능이 빨라도 실패다.

## 로그 규칙

개발 중 성능 로그는 `MapmoryPhotoPerf` 태그를 사용한다.

```bash
adb -s <serial> shell setprop log.tag.MapmoryPhotoPerf DEBUG
adb -s <serial> logcat -v time -s MapmoryPhotoPerf:D
```

로그에 포함할 수 있는 값:

- 단계별 소요 시간
- 조회·처리한 사진 개수
- EXIF 읽기 개수
- 캐시 재사용 개수
- 성공·실패 여부
- 오류 종류

로그에 포함하지 않는 값:

- 사진 파일명과 전체 경로
- 사진 원본·미리보기 데이터
- 제목·본문 등 기록 내용
- 위도·경도 원본 좌표
- 회원 식별 정보

## Firebase Analytics 사용자 행동 이벤트

Firebase Analytics는 기능 사용 여부와 전환 흐름을 확인하기 위한 용도로만 사용한다. 모든
`Modifier.clickable`을 기록하지 않고, 제품 판단에 필요한 의미 있는 행동을 한 번씩 기록한다.

| 이벤트 | 기록 시점 | 주요 파라미터 |
| --- | --- | --- |
| `screen_view` | 지도·기록 작성 화면 진입 | `screen_name` |
| `bottom_nav_clicked` | 하단 지도·일지·통계 탭 클릭 | `from_tab`, `to_tab` |
| `map_scope_changed` | 대한민국·전세계 전환 | `scope` |
| `map_province_selected` | 대한민국 시·도 선택 | `province_code` |
| `map_location_selected` | 지도 또는 장소 검색에서 지역 선택 | `location_type`, `has_records` |
| `record_create_started` | 지도 FAB 클릭 | `source` |
| `photo_recommendation_started` | 위치 기반 사진 추천 시작 | `location_type` |
| `photo_recommendation_cancelled` | 사진 추천 중단 | 없음 |
| `photos_added` | 갤러리·추천 사진을 기록에 추가 | `source`, `count` |
| `record_save_started` | 기록 저장 시작 | `mode` |
| `record_save_completed` | 기록 저장 성공 | `mode` |
| `record_save_failed` | 기록 저장 실패 또는 검증 실패 | `mode` |
| `journal_record_opened` | 일지에서 기록 선택 | 없음 |
| `journal_filter_selected` | 일지 태그 필터 선택 | `tag` |

기록 제목·본문, 사진 파일명·원본, GPS 좌표, 회원 식별자와 같은 개인정보 또는 원본 데이터는
이벤트 파라미터로 보내지 않는다. `count`도 사진 내용이 아니라 처리된 개수만 의미한다.

### DebugView로 Android 이벤트 확인

Debug 빌드에서 대상 기기를 지정하고 앱을 다시 실행한다.

```bash
adb -s <serial> shell setprop debug.firebase.analytics.app com.mapmory.android
```

Firebase Console의 `Analytics > DebugView`에서 이벤트를 즉시 확인한다. 확인이 끝나면 다음 명령으로
해당 기기의 DebugView 모드를 해제한다.

```bash
adb -s <serial> shell setprop debug.firebase.analytics.app .none.
```

Android 프로젝트의 `google-services.json`은 `client/androidApp/google-services.json`에 두고,
앱 시작 시 `FirebaseApp.initializeApp()`으로 Firebase Analytics를 초기화한다. 설정 파일이 없는
개발·CI 환경에서는 Analytics 어댑터가 no-op으로 동작하므로 빌드와 공통 테스트를 막지 않는다.

### DebugView로 iOS 이벤트 확인

Debug 빌드의 Scheme > Run > Arguments Passed On Launch에 `-FIRAnalyticsDebugEnabled`를 추가한 뒤
Simulator 또는 실제 기기에서 앱을 실행한다. Firebase Console의 `Analytics > DebugView`에서
`screen_view`, `map_location_selected` 등의 이벤트가 들어오는지 확인한다. 확인이 끝나면 해당
실행 인자를 제거하거나 `-FIRAnalyticsDebugDisabled`를 사용해 DebugView를 해제한다.

iOS 설정 파일은 `client/iosApp/GoogleService-Info.plist`에 두고, `MapmoryApp` 초기화 시
`FirebaseApp.configure()`를 호출한다. Firebase Analytics 이벤트는 Swift 어댑터가 공통
`MapmoryAnalytics` 인터페이스를 구현해 전달한다.

### 현재 연결 확인 결과

- Android Debug 빌드에서 새 Firebase App ID로 초기화되고 `screen_view`,
  `record_create_started` 이벤트가 Logcat에 기록되며 업로드 응답 `204`를 확인했다.
- iOS Simulator Debug 빌드에서 새 Firebase App ID로 초기화되고 Analytics 수집이 활성화되는 것을
  확인했다. Simulator의 키체인 제약 때문에 Installation ID와 실제 이벤트 수신은 iOS 실기기에서
  추가 확인한다.
- DebugView는 이벤트 전송 후 콘솔에 반영되기까지 지연될 수 있으므로, 즉시 확인할 때는 기기 로그와
  DebugView를 함께 확인한다.

## iOS 사진 성능 로그

iOS의 `PHPicker`와 `PhotoKit` 흐름도 Debug 빌드에서만 `MapmoryPhotoPerf` 로그를 남긴다. 로그에는 사진 데이터나 좌표를 포함하지 않고 처리 시간과 개수만 포함한다.

Xcode에서는 Debug Console에서 `MapmoryPhotoPerf`를 검색한다. 부팅된 Simulator에서는 다음 명령으로 같은 로그를 확인할 수 있다.

```bash
xcrun simctl spawn booted log stream --style compact \
  --predicate 'eventMessage contains "MapmoryPhotoPerf"'
```

직접 사진 선택을 완료하거나 취소하면 다음 형식이 출력된다.

```text
MapmoryPhotoPerf pick_total_ms=... requested_photos=... loaded_photos=...
```

위치 기반 추천을 완료하거나 빈 결과가 나오면 다음 형식이 출력된다.

```text
MapmoryPhotoPerf recommend_total_ms=... recommended_photos=...
```

Android 로그와 iOS 로그는 플랫폼별 API 차이를 숨기지 않고 같은 측정 이름을 사용한다. 따라서 같은 사진 수·같은 기기 조건에서 플랫폼별 처리 시간과 성공 개수를 비교할 수 있다.

## Android 앱 시작 시간 측정

`adb shell am start -W`를 사용해 앱 시작 시간을 반복 측정한다. `cold`는 매 회 앱 프로세스를 종료한 뒤 실행하고, `hot`은 실행 중인 앱을 다시 여는 조건이다.

`client` 디렉터리에서 실행한다.

```bash
bash tools/monitoring/measure_android_startup.sh <adb-serial>

# 반복 횟수 변경
RUNS=10 bash tools/monitoring/measure_android_startup.sh <adb-serial>
```

스크립트는 각 회의 `total_ms`와 평균·중앙값·최댓값을 출력한다. 같은 기기와 같은 Debug APK에서 측정하고, 변경 전후 중앙값과 최댓값을 비교한다. 이 스크립트는 성능 기준선을 수집하는 로컬 도구이며 CI의 통과·실패를 결정하지 않는다.

## 실행 주기

| 시점 | 실행 항목 |
| --- | --- |
| 일반 코드 수정 | 관련 JVM·호스트 테스트, 필요 시 계측 테스트 |
| 사진·Room 수정 | 사진 메타데이터 자동화 테스트와 실기기 로그 확인 |
| 지도 렌더링·탐색 수정 | 지도 선택 계측 테스트와 System Trace 확인 |
| PR 생성·수정 | CI의 테스트·Lint·Debug 빌드 |
| 릴리스 전 | 대표 기기 스모크 테스트, Android 시작 시간 측정, Play Console 사전 출시 보고서 확인 |
| 릴리스 후 | Android Vitals에서 크래시·ANR·성능 이상 확인 |

## 원격 모니터링 도입 판단

Firebase는 백엔드 API나 데이터베이스를 대신하지 않는다.

- 백엔드: 여행 기록, 사진, Location, API 응답을 관리한다.
- Crashlytics: 사용자 기기에서 발생한 Android 크래시·ANR·비정상 오류를 원격으로 확인한다.
- Analytics: 기능 사용 흐름을 분석한다.
- Performance: 앱 시작과 네트워크 등 성능을 원격으로 수집한다.

현재 구현은 Android·iOS 앱에 Firebase Analytics 어댑터와 이벤트 호출을 연결했다. Android는
`google-services.json`, iOS는 `GoogleService-Info.plist`가 포함된 빌드에서 이벤트를 전송한다.
Android는 설정 파일이 없으면 no-op으로 동작하지만, iOS는 `GoogleService-Info.plist`가 앱 번들에
포함되어야 한다. Firebase Analytics를 실제 출시 빌드에서 활성화하면 개인정보처리방침과 Play
Console 데이터 보안 응답을 최종 배포 빌드 기준으로 갱신해야 한다.

실제 사용자 환경에서 재현되지 않는 오류를 원격으로 추적해야 할 때 Crashlytics 도입을 별도 결정한다.
Crashlytics와 Performance Monitoring은 현재 연결하지 않았다.

## 후속 작업

1. 이 기준으로 Android·iOS 사진 추천 로그를 같은 데이터 조건에서 측정해 기준선을 만든다.
2. 지도 전환 지연이 반복적으로 확인되고 전용 테스트 기기 환경을 운영할 수 있을 때 Macrobenchmark를 추가한다.
3. 비공개·프로덕션 테스트에서 Android Vitals를 주기적으로 확인한다.
4. Android·iOS DebugView에서 이벤트 수신을 각각 확인하고, 개인정보처리방침과 Play Console 데이터 보안 응답을 함께 검토한다.
5. 원격 오류 추적이 필요해지는 시점에만 Crashlytics를 검토한다.
