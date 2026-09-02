# Mapmory Firebase Analytics 사용자 행동 분석 및 개선 보고서

기준일: 2026-09-02
측정 기간: 2026-08-30 ~ 2026-09-02

> 요청한 종료일인 2026-09-03은 아직 시작되지 않았으므로 이번 기록에는 포함하지 않았다.
> 2026-09-03까지는 기존 배포 버전의 기준선 데이터를 추가로 수집한다. 이번에 추가한 이벤트는 새 Android·iOS 빌드가 배포된 뒤부터 별도 측정한다.

## 문제 상황

기능을 여러 개 넣었지만 어떤 기능이 실제로 사용되는지, 사용자가 어느 단계에서 이탈하는지 확인하기 어려웠다. 개선 우선순위를 정할 때도 데이터보다 팀원의 추측에 의존하게 되는 문제가 있었다.

## 완료 조건

- 화면 진입, 주요 버튼 클릭, 핵심 기능 사용 여부를 수집하고 확인한다.
- 수집한 데이터를 근거로 기능 개선·유지·제거 여부를 판단한다.

## 기술 역량 목표

- 먼저 확인할 질문을 정하고 그에 맞는 이벤트를 정의한다.
- Firebase Analytics와 GA4 Explore에서 수집된 데이터를 확인한다.
- 데이터가 보여주는 사실과 팀의 해석을 구분한다.

## 1. 분석 목적

다음 핵심 흐름을 확인한다.

> 사용자가 지도에서 지역을 선택하고 여행 기록을 저장한 뒤, 저장한 기록을 다시 확인하는가?

세부적으로 다음 내용을 확인한다.

- 지도에서 지역을 선택하는가?
- 기록 작성 화면에 진입한 사용자가 저장까지 완료하는가?
- 위치 기반 사진 추천을 사용하는가?
- 추천 과정에서 취소하거나 이탈하는가?
- 저장한 기록을 다시 여는가?

## 2. 분석 도구 및 수집 현황

- 분석 도구: Firebase Analytics
- 플랫폼: Android, iOS
- 공통 인터페이스: `MapmoryAnalytics`
- Android 구현: `FirebaseAnalyticsLogger.kt`
- iOS 구현: `FirebaseAnalyticsLogger.swift`
- 실시간 검증: Firebase DebugView
- 순차 퍼널 분석: GA4 Explore
- Firebase 프로젝트: [Mapmory Analytics](https://console.firebase.google.com/u/1/project/mapmory-analytics-b6a50/overview)
- GA4 속성: `mapmory-analytics-b6a50` (`552255927`)
- GA4 Explore: [Mapmory 앱 이벤트 보고서](https://analytics.google.com/analytics/web/?authuser=1#/a405659252p552255927/reports/explorer?params=_u..nav%3Dmaui%26_u.date00%3D20260830%26_u.date01%3D20260902&collectionId=business-objectives&ruid=top-events,business-objectives,examine-user-behavior&r=top-events)

| 플랫폼 | Firebase 앱 | 측정 기간의 배포 버전 | 확인 내용 |
| --- | --- | --- | --- |
| Android | `com.mapmory.android` | `0.1.3` | 78회/4명, 지도·기록 작성·저장 관련 제품 이벤트 확인 |
| iOS | `com.mapmory.ios3` | `0.1.0` | 38회/7명, `screen_view`와 자동 이벤트만 확인 |

Firebase Events 보고서는 기본적으로 프로젝트 단위로 보일 수 있으므로, Android와 iOS를 비교할 때는 반드시 `플랫폼` 필터와 앱 버전을 함께 확인한다.

## 3. 이벤트 정의

아래는 코드에 정의된 이벤트와 이번 측정 기간의 확인 상태를 구분한 표다. `미확인`은 사용하지 않았다는 뜻과 수집되지 않았다는 뜻을 구분할 수 없다는 의미다.

| 사용자 행동 | 이벤트명 | 코드 상태 | 2026-08-30~09-02 관측 |
| --- | --- | --- | --- |
| 주요 화면 진입 | `screen_view` | 구현 | 전체 37회/11명, Android 21회/4명, iOS 16회/7명 |
| 하단 메뉴 클릭 | `bottom_nav_clicked` | 구현 | Android 18회/3명, iOS 0회 |
| 지도 범위 변경 | `map_scope_changed` | 구현 | 미확인 |
| 시·도 선택 | `map_province_selected` | 구현 | Android 2회/2명 |
| 지도 지역 선택 | `map_location_selected` | 구현 | Android 2회/2명 |
| 기록 작성 시작 | `record_create_started` | 구현 | Android 2회/2명 |
| 작성 장소 확정 | `record_location_selected` | 이번 코드 반영, 다음 배포부터 확인 | 아직 집계 전 |
| 날짜 선택 완료 | `record_date_set` | 이번 코드 반영, 다음 배포부터 확인 | 아직 집계 전 |
| 본문 입력 시작 | `record_content_started` | 이번 코드 반영, 다음 배포부터 확인 | 아직 집계 전 |
| 작성 중 나가기 요청 | `record_editor_exit_requested` | 이번 코드 반영, 다음 배포부터 확인 | 아직 집계 전 |
| 기록 저장 시작 | `record_save_started` | 구현 | Android 8회/2명 |
| 기록 저장 성공 | `record_save_completed` | 구현 | Android 2회/2명 |
| 기록 저장 실패 | `record_save_failed` | 구현 | Android 6회/2명 |
| 사진 선택창 열기 | `photo_picker_opened` | 구현 | 미확인 |
| 사진 추가 | `photos_added` | 구현 | Android 1회/1명 |
| 위치 기반 사진 추천 시작 | `photo_recommendation_started` | 구현 | Android 1회/1명 |
| 위치 기반 사진 추천 결과 | `photo_recommendation_completed` | 이번 코드 반영, 다음 배포부터 확인 | 아직 집계 전 |
| 위치 기반 사진 추천 취소 | `photo_recommendation_cancelled` | 구현 | 미확인 |
| 저장한 기록 열기 | `journal_record_opened` | 구현 | Android 1회/1명 |

기록 제목·본문, 사진 파일명·원본, GPS 원본 좌표, 회원 식별자와 같은 개인정보는 이벤트 파라미터로 보내지 않는다.

## 4. 측정 결과

### 플랫폼별 요약

| 범위 | 활성 사용자 | 전체 이벤트 | 비고 |
| --- | ---: | ---: | --- |
| 전체 GA4 앱 속성 | 11명 | 116회 | Android·iOS 합산, 2026-08-30~09-02 |
| Android 필터 | 4명 | 78회 | 제품 이벤트와 자동 이벤트 포함 |
| iOS 필터 | 7명 | 38회 | `screen_view` 16회, 자동 이벤트와 `setup_probe` 포함 |

자동 수집 이벤트(`first_open`, `session_start`, `user_engagement`, `app_remove`)와 테스트용 `setup_probe`는 제품 기능 사용률을 판단하는 지표에서 제외했다.

### 핵심 이벤트

| 이벤트 | 이벤트 수 | 사용자 수 | 해석 시 주의점 |
| --- | ---: | ---: | --- |
| `screen_view` | 37 | 11 | Android 21회/4명, iOS 16회/7명. `screen_name`별 분리가 필요하다. |
| `map_location_selected` | 2 | 2 | Android에서만 확인됐다. 전체 화면 진입 사용자 대비 값은 순차 퍼널 전환율이 아니다. |
| `record_create_started` | 2 | 2 | Android에서 기록 작성 진입 신호가 확인됐다. |
| `record_save_started` | 8 | 2 | Android에서 동일 사용자의 재시도가 포함될 수 있다. |
| `record_save_completed` | 2 | 2 | Android에서 저장 성공 신호가 확인됐다. |
| `record_save_failed` | 6 | 2 | Android에서 저장 실패 신호가 6회 기록됐다. 시도 ID가 없어 사용자 기준 실패율로 확정할 수 없다. |
| `photo_recommendation_started` | 1 | 1 | Android에서 1회 확인됐으며 표본이 작다. |
| `journal_record_opened` | 1 | 1 | Android에서 1회 확인됐으며 저장 후 재조회로 단정하려면 순차 퍼널이 필요하다. |

## 5. 퍼널 해석

현재 데이터는 같은 사용자의 순서를 보장하는 GA4 순차 퍼널로 구성되지 않았으므로, 아래 수치는 방향을 확인하는 참고값으로만 사용한다.

| 구간 | 참고 계산 | 관측값 | 판단 |
| --- | --- | ---: | --- |
| 지도 진입 → 지역 선택 | `map_location_selected` 사용자 ÷ 전체 `screen_view` 사용자 | 2/11 = 18.2% | 지도 화면만 분모로 한 값이 아니므로 참고값으로만 사용 |
| 기록 작성 시작 → 저장 시작 | `record_save_started` 사용자 ÷ `record_create_started` 사용자 | 2/2 = 100.0% | 사용자 수는 같지만 순차 퍼널이 아니므로 실제 전환율로 확정할 수 없음 |
| 저장 시도 → 성공·실패 | `record_save_completed` 또는 `record_save_failed` 이벤트 ÷ `record_save_started` 이벤트 | 성공 2/8, 실패 6/8 | 시도별 연결 키가 없어 확정 전환율은 아니지만 저장 흐름의 가장 큰 오류 신호 |
| 저장 성공 → 기록 재조회 | `journal_record_opened` 사용자 ÷ `record_save_completed` 사용자 | 1/2 = 50.0% | 동일 사용자의 순서를 보장하지 않으므로 참고값으로만 사용 |

가장 큰 개선 신호는 Android의 저장 흐름에서 발견됐다. 저장 시작 8회에 대해 성공 2회와 실패 6회가 기록되어 저장 실패 원인과 재시도 흐름을 우선 확인해야 한다. iOS는 7명의 사용자가 집계됐지만 지도 선택·기록 저장·사진 추천 같은 핵심 행동 이벤트가 0회라, 기능 사용률보다 이벤트 호출 경로와 배포 버전을 먼저 검증해야 한다.

## 6. 데이터에 따른 결정

| 영역 | 데이터가 보여준 사실 | 팀의 해석 | 결정 |
| --- | --- | --- | --- |
| iOS 이벤트 수집 | `screen_view`는 확인됐지만 핵심 제품 이벤트는 확인되지 않았다. | iOS 미사용인지, 배포 버전·Firebase 설정·실제 경로 문제인지 아직 구분되지 않는다. | **개선**: iOS 실기기 DebugView와 최신 빌드에서 이벤트 전송을 재검증한다. |
| 기록 저장 | Android에서 저장 시작 8회, 성공 2회, 실패 6회가 기록됐다. | 저장 요청 재시도 또는 실패 후 안내가 중요한 구간이다. | **개선**: 실패 원인과 재시도 흐름을 확인하고 사용자 오류 안내를 보완한다. |
| 지도·기록 핵심 기능 | 지역 선택과 기록 작성 이벤트가 실제로 확인됐다. | 핵심 가치 흐름은 사용되고 있으므로 제거 근거가 없다. | **유지**: 사용자 수를 늘려 동일 퍼널을 다시 측정한다. |
| 위치 기반 사진 추천 | Android에서 시작 1회와 사진 추가 1회가 확인됐고, iOS에서는 확인되지 않았다. | 기능 사용 표본이 작고 플랫폼별 수집 상태가 다르다. | **유지 후 검증**: 완료·빈 결과 이벤트를 보완한 뒤 사용률을 판단한다. |
| 기능 제거 | 표본이 11명이고 4일뿐이며 일부 이벤트가 누락됐다. | 현재 수치만으로 기능 필요성을 판단할 수 없다. | **보류**: 개선 전·후와 충분한 표본을 비교한 뒤 결정한다. |

## 7. 이번에 적용한 개선

보고서의 `screen_view` 범위와 실제 코드가 일치하도록 다음 화면 진입 로그를 추가했다.

- 일지 화면: `screen_name=journal`
- 통계 화면: `screen_name=statistics`
- 기록 상세 화면: `screen_name=record_detail`

기록 작성 화면에서 이탈 지점을 구분할 수 있도록 다음 이벤트도 추가했다.

- `record_location_selected`: 작성 화면에서 장소를 확정한 순간
- `record_date_set`: 시작·종료 날짜를 확정한 순간과 `field`
- `record_content_started`: 본문에 처음 입력한 순간
- `record_editor_exit_requested`: 저장 전 나가기 시도와 `reason`
- `photo_recommendation_completed`: 최초 추천 결과와 `result`, `count`

이 이벤트들은 `commonMain`에서 호출되며 Android의 `FirebaseAnalyticsLogger.kt`와 iOS의
`FirebaseAnalyticsLogger.swift`가 같은 이름과 문자열 파라미터를 Firebase Analytics로 전달한다.
따라서 플랫폼별 구현을 따로 복제하지 않고도 Android·iOS 퍼널을 같은 기준으로 비교할 수 있다.

이 변경은 이번 측정 기간 이후의 코드에 반영된 것이므로, 과거 Firebase 수치에는 포함되지 않는다. 다음 Android·iOS 빌드에서 DebugView로 확인한 뒤 새 측정 기간을 시작해야 한다.

### iOS 핵심 제품 이벤트가 확인되지 않은 이유

현재 iOS 코드에는 `FirebaseApp.configure()`, `FirebaseAnalyticsLogger`, `GoogleService-Info.plist`가 포함되어 있고, 실제로 `screen_view`가 수집되었다. 따라서 Firebase SDK 연결 자체가 완전히 끊긴 상태라고 단정할 수는 없다.

다만 핵심 제품 이벤트가 보이지 않는 것만으로는 다음 원인을 구분할 수 없다.

- 해당 기간에 iOS 사용자가 지도 선택·기록 저장·사진 추천 과업까지 진행하지 않았을 가능성
- 이벤트가 추가된 코드가 포함되지 않은 이전 iOS 빌드를 사용했을 가능성
- 일반 Analytics 보고서 반영 지연
- Android와 iOS에서 실제로 실행되는 화면 경로가 달라 이벤트 호출 지점에 도달하지 않았을 가능성

따라서 iOS 기능 사용률을 수치로 판단하지 않고, iOS 최신 빌드에 `-FIRDebugEnabled`를 적용한 뒤 Firebase DebugView에서 동일한 과업을 직접 수행해 이벤트 호출 여부를 먼저 확인한다. DebugView에서 이벤트가 보이면 보고서 반영 지연 또는 측정 기간의 미사용 문제이고, 보이지 않으면 iOS 호출 경로·Firebase 앱 설정·빌드 포함 여부를 점검한다.

## 8. 다음 측정 및 개선 계획

1. Android와 iOS 최신 빌드를 각각 실행하고 DebugView에서 `screen_view`, `map_location_selected`, `record_save_started`, `record_save_completed`를 확인한다.
2. GA4 Explore에서 `screen_name`과 플랫폼을 분리해 `지도 → 지역 선택 → 기록 작성 → 저장` 순차 퍼널을 만든다.
3. 저장 실패 이벤트에 비식별 `failure_type`을 추가할 수 있는지 검토한다.
4. 위치 기반 사진 추천에 `started → completed(result=success|empty) → cancelled` 흐름을 추가로 기록한다.
5. 기록 작성 화면은 모든 컴포넌트 클릭을 기록하지 않고, 다음과 같이 의사결정에 필요한 단계만 추가 측정한다.

   | 측정 단계 | 이벤트 또는 파라미터 | 확인하려는 것 |
   | --- | --- | --- |
   | 장소 선택 완료 | `record_location_selected` | 장소 선택 단계에서의 이탈 |
   | 날짜 입력 완료 | `record_date_set` | 날짜 입력 흐름의 어려움 |
   | 본문 입력 시작 | `record_content_started` | 기록 작성 의향과 저장 전 이탈 |
   | 사진 흐름 완료 | `photo_recommendation_completed` + `result` | 추천 성공·빈 결과·실패 구분 |
   | 유효성 검증 실패 | `record_validation_failed` + `field` | 어떤 입력 항목에서 막히는지 |
   | 저장하지 않고 나가기 시도 | `record_editor_exit_requested` + `reason` | 명시적인 작성 중단과 이탈 경로 |

   기존 `photo_picker_opened`, `photos_added`, `record_save_started`, `record_save_completed`, `record_save_failed` 이벤트와 연결한다. 이번에 추가한 이벤트는 공통 `commonMain` 화면에서 호출되므로 Android와 iOS에 같은 이름과 파라미터로 전달된다. 단순 화면 재구성이나 같은 컴포넌트의 반복 클릭은 기록하지 않는다.
6. 최소 7일 또는 30명 이상의 유효 사용자 데이터를 모은 뒤 개선 전·후 전환율을 비교한다.

## 9. 한계

- 측정 기간이 4일이고 전체 활성 사용자가 11명으로 작다.
- 2026-09-03 데이터는 아직 포함되지 않았다.
- Firebase 기본 Events 화면만으로는 이벤트의 순서와 동일 사용자 여부를 완전히 확인하기 어렵다.
- iOS 핵심 제품 이벤트가 확인되지 않아 iOS 기능 사용률을 판단할 수 없다.
- 이벤트가 보이지 않는 경우 미사용, SDK 설정, 배포 버전, 이벤트 지연을 구분하기 위해 DebugView 검증이 필요하다.

## 10. 결론

Firebase Analytics와 GA4 앱 속성을 통해 Android·iOS 앱의 사용자 행동을 플랫폼별로 확인할 수 있는 기반은 마련되어 있다. 이번 측정에서는 Android의 저장 흐름에서 실패 신호가 확인되었고, iOS는 핵심 제품 이벤트 수집 여부를 먼저 검증해야 한다는 사실을 확인했다.

따라서 현재의 결론은 기능 제거가 아니라 **측정 품질 보완과 저장 흐름 개선을 우선한다**는 것이다. 이후 플랫폼·화면·사용자 단위가 분리된 순차 퍼널을 다시 측정하고, 그 결과로 기능 유지·개선·제거를 판단한다.
