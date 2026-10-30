# Mapmory 행동 및 오류 수집 계약 (schema 2)

## 목적과 적용 시점

기록 생성 → 나중에 다시 열람하는 흐름, 행동별 리텐션, 핵심 기능의 실패 건수/영향 사용자/실패율을 분석한다. 이 문서는 코드 계약이다. 실제 Firebase 수신·출시 버전 반영·BigQuery 연결·GA4 맞춤 정의 등록은 별도 검증이 필요하다. 이전 버전에서 수집하지 않은 이벤트를 소급 복원할 수는 없다.

Android/iOS Firebase 어댑터는 `event_schema_version="2"`를 붙인다. `duration_ms`, `elapsed_ms`, `result_count`, `http_status`는 숫자로 전송한다. 기존 `count`, `photo_count` 등 문자열 파라미터의 타입은 기존 맞춤 정의와 충돌하지 않도록 유지한다. Debug/Internal의 기존 Analytics 비활성화 정책은 유지한다.

## 기존 이벤트 호환성

기존 이벤트 이름은 변경/삭제하지 않는다. 수정 저장 완료는 기존 Route에서 기존 `mode`, `has_title`, `has_content`, `has_end_date`, `has_tags`, `has_photos` 값을 그대로 전송하고 `record_id`만 추가한다. 신규 기록의 실제 저장 완료도 같은 이름/필드 계약을 재사용한다. 따라서 `record_save_completed` 전체 건수는 누락 보완 이후 증가할 수 있으며, 장기 비교는 `mode=edit/create`와 앱 버전을 나누어야 한다.

기존 `journal_record_opened`는 목록 클릭 의미와 시점을 유지하고 ID만 보완한다. `record_viewed`는 로딩 실패를 제외한 실제 상세 표시이므로 클릭과 합산하지 않는다. iOS 인증 갱신 실패 이벤트도 기존 플랫폼/단계/오류종류와 발생 조건을 유지한다. 신규 공통 operation/error 이벤트는 행동 이벤트의 이름 변경이 아닌 별도의 성공률·오류율 계측이다.

## 핵심 기능 점검표

| 영역 | 수집 지점/이벤트 | 해석 |
| --- | --- | --- |
| 첫 사용 안내 | `record_introduction_viewed`, `record_introduction_completed(action=create/explore)` | 완료는 안내 선택이며 기록 생성 성공이 아님 |
| 지도 탐색 | 기존 화면·scope·지역 선택 + `map_retry_clicked` | root/region 조회 결과는 공통 operation 이벤트 |
| 하단 탭 | 기존 `bottom_nav_clicked` | 각 아이콘·픽셀·스크롤마다 중복 계측하지 않음 |
| 기록 작성 | 기존 시작·단계·필드 상호작용·이탈·저장 시작·대기 | 제목/본문 자체는 전송하지 않음 |
| 실제 생성·수정 | `record_save_completed(mode=create/edit, record_id, photo_count, has_title, has_end_date, has_photos, has_content, has_tags)` | 생성은 백그라운드 저장 큐의 최종 성공 시점에 한 번 발행한다. repository는 공통 작업 결과만 측정한다. 수정은 기존 화면의 save 성공 처리 지점과 기존 파라미터를 유지하고 record_id만 추가. repository에서는 수정 완료를 중복 발행하지 않음 |
| 백그라운드 실패 | `record_save_failed(save_mode=background)` | 자동 재시도까지 끝나고 실패 상태가 된 작업 사이클. 앱 재시작으로 복구한 불확실 상태는 과거 실패로 재발행하지 않음 |
| 백그라운드 복구 행동 | `record_save_retry`, `record_save_discarded` | 실패 상태의 유효한 재시도/폐기만 기록 |
| 기록 목록 | 기존 필터·페이지·재시도, `journal_record_opened(record_id, source=journal)` | 목록 클릭이며 성공 열람과 구별 |
| 기록 열람 | `record_viewed(record_id)` | 상세 데이터가 성공적으로 표시된 탐색 진입당 1회. recomposition·동일 entry 복원·수정 후 새로고침으로 중복하지 않음. 편집용 데이터 조회는 열람으로 세지 않음 |
| 편집·삭제 | `record_edit_started`, `record_delete_started`, `record_deleted` | 삭제 시작은 확인 후 동작, 완료는 repository 성공 후 |
| 사진 확대 | `record_photo_opened(record_id)` | 사진 ID·경로·위치·EXIF는 수집하지 않음 |
| 사진 선택·추천 | 기존 picker/추가/추천 시작, `photo_recommendation_page_loaded(result_count, has_more)` | 채택된 추천 페이지 콜백의 크기. 전체 추천 사용자 수나 유일한 사진 수로 합산하지 않음 |
| 사진 권한 | `photo_permission_required(permission_state)` | limited/denied/restricted는 안내 이벤트. 앱 오류 아님 |
| 사진 실패 | `app_error(operation=photo_recommendation/photo_import/photo_prepare/photo_picker_open/photo_display)` | 기존 예외 처리 지점 또는 일부/전체 읽기 실패에서 수집. 이미지 표시는 대체 이미지까지 실패한 경우 입력별 1회, URL/바이트 미전송 |
| 태그·장소 | 공통 조회/생성/검색/선택 operation + `tag_created` | 태그 이름·검색어·장소 ID·세션 토큰 미전송 |
| 통계·설정 | 기존 화면·통계 재시도·설정 열기·테마·정책 이벤트 | 통계 조회 결과는 공통 operation |
| 의견 보내기 | `feedback_opened`, `feedback_submitted(category)` + 공통 operation | 의견 본문 미전송 |
| 인증 갱신 | `auth_session_refresh_failed(platform=ios, stage, error_type)` | 기존 iOS callback·Debug binary 제외 조건·파라미터 유지. 같은 실패가 상위 operation 오류로도 나타날 수 있어 서로 합산하지 않음 |

## 오류와 실패율

`app_operation_completed`는 완료된 repository 호출 1회에 1개이며 `operation`, `outcome=success/failure`, `duration_ms`를 갖는다. 성공·실패 모두 분모에 포함한다. 인증 재시도가 내부에서 성공하면 최종 성공으로 기록하고, 백그라운드 저장의 외부 자동 재시도는 별도 repository 호출로 기록한다.

대상 operation: `record_list_load`, `record_detail_load`, `record_create`, `record_update`, `record_delete`, `map_root_load`, `map_region_load`, `statistics_load`, `tags_load`, `tag_create`, `place_search`, `place_select`, `feedback_submit`.

실패 시 `app_error`를 추가하며 `operation`, `error_category`, `owner_hint`, `error_type`, API 오류의 `http_status`를 기록한다. 오류 메시지·서버 응답 본문·API instance·URL·토큰은 전송하지 않는다. 반환된 CancellationException과 던져진 CancellationException 모두 집계에서 제외한다. Analytics logger의 예외가 저장 결과를 실패로 바꾸거나 재시도를 유발하지 않도록 격리한다.

| error_category | owner_hint | 의미 |
| --- | --- | --- |
| client_logic | mobile | 별도 분류가 없는 IllegalState/IllegalArgument 계열. 모바일 조사 우선이며 확정 귀책은 아님 |
| media_playback | undetermined | Android/iOS 네이티브 플레이어 오류. 앱·파일·코덱·OS 중 원인을 단정하지 않으며 플랫폼 숫자 코드와 함께 조사 |
| data_contract | shared_contract | 역직렬화 계약 불일치. 앱/서버 공동 확인 |
| server | server | HTTP 5xx |
| authentication | undetermined | HTTP 401/403 |
| rate_limit | undetermined | HTTP 429 |
| request_rejected | undetermined | 그 밖의 API 거절 응답. 4xx를 모두 모바일 버그로 단정하지 않음 |
| io | undetermined | 네트워크 또는 로컬 미디어 I/O. operation과 함께 해석 |
| unknown | undetermined | 나머지 예외. 오류 없음으로 취급하지 않음 |

- 발생 건수: `app_error` 이벤트 수. 영향 사용자: 해당 이벤트를 낸 고유 `user_pseudo_id` 수.
- 기능 실패율: 같은 operation의 `app_operation_completed(outcome=failure)` / 전체 완료 호출 수. 정상 취소는 분모에서도 제외.
- 사진 오류·저장 준비 오류는 공통 repository 분모가 없으므로 위 비율에 섞지 않는다.
- 백그라운드 최종 실패 건수와 내부 시도 실패 건수는 서로 다른 지표다. 두 이벤트를 합산하면 중복이다.
- 오류 0건과 수집 데이터 없음은 구별한다. 표본 수·앱 버전·OS·조회 기간·데이터 최신 시각을 표시한다.
- 이 계측은 모든 Throwable을 전역 수집하지 않는다. 프로세스 시작 전 실패, 일부 내부 캐시의 삼켜진 오류, OS 종료·ANR·native crash 등은 별도 영역이다. Android crash/ANR은 기존 Crashlytics를 사용한다. iOS는 이번 후속 변경에서 Crashlytics SDK와 Release 기기 빌드 dSYM 업로드를 추가했다. iOS 크래시 수신 검증은 별도이며 GA 이벤트만으로 대체하지 않는다. 새로운 non-fatal stack 업로드는 이 변경에 포함하지 않는다.

## 리텐션과 회고 분석 기준

- 사용자: 우선 Firebase 앱 인스턴스 가명 ID (`user_pseudo_id`). 실제 사람 수/회원 수와 같지 않고 재설치·기기 변경을 연결하지 않는다. 새 user_id나 계정 추적은 추가하지 않았다.
- 작성자: `record_save_completed` 중 `mode=create`. queued/start/edit는 생성 사용자 판정에서 제외한다.
- 같은 기록 재열람: 사용자 가명 ID + `record_id`를 조인하고 생성 이후의 `record_viewed`를 찾는다. 생성 날짜와 다른 날짜의 열람을 회고 지표로 사용하면 저장 직후 확인과 구분할 수 있다.
- 앱 재방문 리텐션과 기록 재열람 리텐션은 별도 지표다. 기준 시간대는 속성 시간대(팀 리포트는 Asia/Seoul)를 일관되게 사용한다.
- 행동 집단 비교는 첫날/첫 주처럼 동일한 관찰 창에서 나눈다. D7을 관찰하지 못한 신규 사용자는 실패로 세지 않는다. 작은 표본은 비율과 인원수를 함께 표시한다.
- 수집은 SDK의 best effort다. offline 업로드 지연·동의·앱 종료·재설치의 영향을 받으며 DB 감사 로그/정확한 전체 생성 건수의 대체가 아니다.

## GA4 / 출시 후 확인

1. 릴리스 설정에서 이벤트가 수집되는 기기로 신규 생성, 수정, 삭제, 기록 재열람, 추천/권한 안내를 각각 수행한다. 운영 계측이 꺼진 Debug/Internal에서 안 보이는 것을 누락으로 오판하지 않는다.
2. 오류는 테스트 환경/허용된 검증에서 유발한다. 취소·권한 거부는 app_error에 없어야 하고 실제 실패는 operation/error가 한 쌍이어야 한다.
3. GA4에 필요한 이벤트 범위 맞춤 측정기준: `operation`, `outcome`, `error_category`, `owner_hint`, `error_type`, `mode`, `event_schema_version`, `permission_state`. `duration_ms`, `result_count`는 필요한 경우 맞춤 측정항목으로 등록한다.
4. `record_id`는 고카디널리티이므로 일반 집계 차원으로 등록하기보다 BigQuery 원시 이벤트 조인에 사용한다. BigQuery 연결·내보내기 시작일은 별도 확인한다.
5. 팀 리포트는 schema 2 및 배포 버전부터 비교한다. 실제 모바일 실행 → Firebase 수신 → BigQuery 행을 확인하기 전에는 운영 수집 완료로 표시하지 않는다.

공식 참고: [Firebase 이벤트](https://firebase.google.com/docs/analytics/events), [BigQuery Export](https://support.google.com/analytics/answer/9358801), [Crashlytics](https://firebase.google.com/docs/crashlytics).

## 이번 변경의 검증 결과 (2026-10-19)

- JVM 회귀 테스트 39개 통과: 새 계측 테스트 8개, AppContainer·백그라운드 저장·캐시 회귀 포함. 실패/오류/skip 0.
- 성공/실패 이벤트, exception/Result 형태의 취소 제외, logger 실패 격리, 민감 데이터 미포함, 자동 재시도 후 완료 1건, 최종 실패와 시도 실패 구분, 중복 폐기 제외, container 주입을 확인했다.
- `:shared:compileAndroidMain`, `:androidApp:compileDebugKotlin`, `:shared:compileKotlinIosSimulatorArm64` 통과.
- Xcode Mapmory Debug iOS Simulator 앱 빌드 통과. Swift Firebase 어댑터와 백그라운드 Kotlin 브리지 컴파일 포함.
- 실제 기기 행동·Firebase DebugView/운영 수신·GA4 맞춤 정의·BigQuery 데이터는 미검증. 커밋/푸시/스토어 배포 없음.


## 영상·이미지·업적 공유 계측 (2026-10-26)

공유 기능도 schema 2를 사용하며 기존 기록/지도/통계 이벤트의 이름과 발행 시점은 유지한다. `share_type`은 `travel_timeline`, `record`, `statistics`, `achievement`, `media_type`은 `video` 또는 `image`이다.

| 이벤트 | 발생 시점 | 추가 파라미터 |
|---|---|---|
| `share_media_action` | 다이얼로그 열기/닫기, 생성·재생성·재조회·다운로드 재시도·저장·공유 선택, 이미지 디자인 표시·대표 사진 선택·재시도·내보내기 차단, 네이티브 재생 상태 관측 | `action`, 선택 시 `selection_index` |
| `share_media_operation_started` | 생성 요청, 다운로드 URL 요청, 파일 다운로드/캐시 확보, 이미지 캡처, 네이티브 미리보기 준비, 사진 앱 저장, 공유 시트 열기, 완료 알림 발행 시작 | `operation` |
| `share_media_operation_completed` | 측정한 시도의 반환/예외/취소와 아래 실패 전용 관측. 모든 completed에 started가 대응하지 않음 | `operation`, `outcome`, 실제 시작을 측정한 경우 `duration_ms` |
| `share_video_status_changed` | 같은 앱 컨테이너에서 작업 상태가 달라진 경우와 복원된 작업의 최초 조회 결과 | `status`, `origin`, 종료 상태에서 시작 시각을 아는 경우 `elapsed_ms` |
| 기존 `app_error` | 생성 요청·서버 렌더링·상태 조회·다운로드 URL·파일·이미지 캡처·미리보기/재생·저장·공유 시트·알림의 실제 오류 | `operation=share_video_*` 또는 `share_image_*`, `feature`, `share_type`, `media_type`, `stage`, 오류 종류/분류/HTTP 상태. 재생 오류에는 숫자 `platform_code`, Android는 `platform_extra_code` 추가 |

### 집계 의미

- `create_request/success`는 서버의 작업 접수이다. 영상 완성은 `share_video_status_changed(status=completed)`로 판단한다. 서버 `failed`는 `generation/failure`와 `app_error(stage=generation)`로 1회 수집한다.
- `origin=created_here`는 이 컨테이너에서 생성 요청을 보낸 작업, `restored`는 저장된 작업 등 외부에서 복원한 관측이다. **복원된 완료 작업을 새 영상 생성 건수로 합산하지 않는다.** 작업 ID를 보내지 않으므로 서버 전체의 유일한 생성 수 집계는 아니다.
- 상태 변화는 작업별 최근 32건의 프로세스 메모리에서 중복 억제한다. 같은 상태의 2초 폴링은 추가 이벤트를 보내지 않는다. 같은 조회 오류는 연속 실패 중 종류가 같으면 1회만 보내며, 정상 조회 후 재발하면 새 오류로 센다. 앱 재시작 후 관측은 다시 기록될 수 있다.
- `elapsed_ms`는 생성 요청 시작부터 **앱이 종료 상태를 관측할 때까지**의 시간이다. 백그라운드에서 멈춘 폴링·네트워크 지연을 포함하며 서버 렌더러의 실제 실행 시간이 아니다. 복원된 작업은 저장된 시작 시각이 있을 때만 포함한다.
- `save/success`는 플랫폼 저장 API가 성공한 경우이다. `share_sheet/handoff`는 Android/iOS 공유 시트로 이미지를/동영상을 넘긴 경우이며 **Instagram 등 외부 앱에서 실제 게시·공유가 완료됐다는 의미가 아니다.**
- `download/success`는 파일이 사용 가능해졌다는 뜻이다. 기존 파일 캐시 재사용을 포함하며 항상 네트워크 다운로드가 발생했다는 뜻은 아니다.
- `preview_load/success`는 네이티브 플레이어가 준비된 경우이다. 단순 미리보기 화면 구성과 구분한다. 재생·일시정지는 네이티브 상태를 250ms 간격으로 관측하므로 짧은 상태 변화는 누락될 수 있고 수동 사용자 조작만을 뜻하지 않는다. 완료/오류는 같은 미리보기에서 중복 억제하며 종료 후 늦은 콜백은 제외한다.
- `notification_ready/posted`는 알림 발행 요청이 OS에 접수된 경우이며 알림 노출·클릭을 보장하지 않는다. 권한/채널 때문에 발행하지 못하면 `not_posted`로 기록한다.
- 권한 거절(`permission_denied`), 취소(`cancelled`), 영상 미완료(`not_ready`), 경로 부족(`insufficient_records`)은 실제 앱 오류 `app_error`에 포함하지 않는다. 기존 `photo_display`가 보고하는 이미지 로딩 Exception은 공유 화면에서 다시 `app_error`로 중복 보고하지 않는다.
- 영상 상태 조회를 포함한 API의 `MapmoryConnectionException`은 네트워크 오류인 `io`로 분류한다. 로거의 Exception은 비즈니스 성공/실패·재시도·상태 저장을 바꾸지 않는다.

### 시작 이벤트 없이 실패만 기록하는 관측

`recordObservedFailure()`는 시작부터 종료까지 측정한 시도가 아니라, 아래 단계에서 관측한 실패를 기록한다. 이 이벤트에는 대응하는 `share_media_operation_started`와 `duration_ms`가 없다. 호출자가 같은 오류의 중복 억제를 담당한다. 취소는 기록하지 않고, 예상된 권한 거절은 `permission_denied` 결과로만 기록하며 `app_error`에서는 제외한다.

| operation | 기록 범위 |
| --- | --- |
| `status_check` | 상태 조회 오류 구간별 실패. 정상 폴링은 기록하지 않음 |
| `generation` | 서버의 생성 실패 상태 관측. 작업 접수의 `create_request` 시도와 구분 |
| `preview_playback` | 준비 완료 이후 네이티브 재생 오류. 준비 과정의 `preview_load`는 시작/종료를 측정 |
| `notification_permission` | 권한 요청 API의 예외. 정상 반환은 기록하지 않음 |
| `notification_progress` | 진행 알림 API의 예외. 정상 반환은 기록하지 않음 |
| `notification_clear` | 진행 알림 정리 API의 예외. 정상 반환은 기록하지 않음 |
| `image_load` | 공유 이미지 로딩 실패. 별도 `imageLoadFailed()` 경로이며 실제 예외는 기존 `photo_display`가 보고 |

`notification_ready`는 별도로 시작/종료를 측정하고 `posted/not_posted`를 기록한다. 준비 완료 이후 재생 오류도 `media_playback/undetermined`로 분류하며 `platform_code`와 Android의 `platform_extra_code`를 보존한다.

위 실패 전용 관측은 전체 시도 수를 수집하지 않으므로 성공률·평균 소요 시간을 계산할 수 없다. **모든 operation을 합친 completed/started 비율을 성공률로 쓰지 않는다.** 시도 단위 분석은 시작/종료를 모두 수집하는 동일 operation 안에서 수행하고, 실패 전용 관측은 오류 건수·영향 사용자로 분석한다.

### 데이터와 출시 확인

이름·지역·기록 제목/본문·사진/기록/작업 ID·토큰·URL·파일 경로·예외 메시지·원본 SDK 오류 설명은 보내지 않는다. `selection_index`는 카드 디자인/대표 사진의 0부터 시작하는 순서이며 식별자가 아니다.

Debug/Internal의 기존 비활성화 설정은 유지한다. 실수신은 수집이 허용된 Release 빌드로 Firebase DebugView/GA4 또는 BigQuery에서 확인해야 한다. GA4 화면에서 `share_type`, `media_type`, `action`, `operation`, `outcome`, `status`, `origin`, `stage`별로 분석하려면 맞춤 정의가 필요할 수 있으며 이 PR은 콘솔 설정을 변경하지 않는다. `duration_ms`, `elapsed_ms`는 Android/iOS 양쪽에서 숫자로 전달한다.


### 분리 브랜치 검증 결과

- JVM 테스트 371개(공유 계측 신규 18개 포함), Android Host 테스트 347개: 실패·오류·스킵 없음. 두 실행은 공통 테스트가 겹치므로 합산한 고유 테스트 수로 해석하지 않는다.
- Android `assembleDebug`, `lint`, iOS `compileKotlinIosSimulatorArm64`, Xcode Debug iOS Simulator 전체 앱 빌드 통과.
- 실제 영상·이미지의 기기 재생/저장/외부 공유와 Release Firebase 수신은 미검증.

## 일지의 타임라인 영상 만들기 (Android/iOS 공통)

| 이벤트 | 파라미터 | 의미 |
| --- | --- | --- |
| `video_opened` | `source=journal` | 일지의 타임라인 영상 만들기 클릭 |
| `video_action` | `action=create_request/preview_download/save/share_sheet`, `outcome=started/success/failure/cancelled` | 생성 요청, 미리보기 파일 다운로드, 사진 앱 저장, 공유창 표시 시도와 결과 |
| `video_generation_result` | `outcome=success/failure` | 생성 응답 또는 상태 조회에서 관측한 서버 생성 완료/실패 |

생성 요청 성공은 서버 생성 완료가 아니다. `share_sheet` 성공은 공유 UI 표시 API가 반환했음을 의미하며 실제 전달·게시 완료나 공유창에서의 사용자 취소 여부를 의미하지 않는다. `cancelled`는 코루틴 취소다. `save` 성공은 플랫폼 저장 API 완료를 의미한다.

생성 결과는 컨테이너 수명 내 작업별 한 번만 기록한다. 다이얼로그를 닫아도 공통 상태 모니터의 조회 결과가 기록된다. 프로세스 재시작 후 서버에서 다시 관측하면 중복될 수 있으며, 앱이 결과를 조회하지 못하면 이벤트가 없다. 정확한 서버 작업 성공률은 서버 집계가 필요하다. 폴링 요청마다 이벤트를 기록하지 않는다.

작업 ID·회원 ID·파일 경로·다운로드 URL·여행 내용은 이벤트 파라미터로 전송하지 않는다. 기존 플랫폼 Firebase 수집 활성화 설정을 따르며, Debug/Internal에서의 실제 수신은 별도 확인해야 한다.

## 여행 기록 한 건 공유 (Android/iOS 공통)

| 이벤트 | 파라미터 | 의미 |
| --- | --- | --- |
| `record_share_preview_opened` | 없음 | 기록 한 건의 공유 미리보기가 표시됨 |
| `record_share_action` | `action=save_image/share_sheet`, `outcome=started/success/failure/cancelled` | 공유 이미지 저장 또는 시스템 공유 화면 열기 시도와 결과 |

`save_image`의 성공은 사진 보관함 저장 완료를 뜻한다. `share_sheet`의 성공은 플랫폼 공유 UI 표시 요청이 반환된 것을 뜻하며, 다른 앱에서 실제로 공유·게시됐거나 사용자가 공유를 완료했다는 뜻은 아니다. `cancelled`는 코루틴 취소만 의미하며 시스템 공유 화면을 닫은 사용자 행동을 나타내지 않는다.

기록 ID, 제목, 장소, 사진·파일 경로, 공유 대상 앱은 이벤트에 포함하지 않는다. 기록별 공유 동작은 공통 Compose 흐름에서 계측하므로 Android와 iOS가 같은 이벤트 계약을 사용한다.

`record_share_action`은 버튼 클릭부터 이미지 캡처와 저장·공유 시트 호출까지 한 번의 전체 시도를 나타낸다. `share_media_operation_*`은 이미지 캡처, 저장, 공유 시트 등 단계별 결과를 기록한다. 두 이벤트 계열은 분석 범위가 다르므로 건수를 합산하지 않는다. 상세 계측이 실제 예외를 보고하므로 상위 이벤트에서 `app_error`를 중복 발행하지 않는다. 권한 거부는 상위 액션에서 `failure`, 상세 operation에서 `permission_denied`로 남기며 `app_error`에는 포함하지 않는다.

## iOS 사진 로딩 진단

- `photo_load_failed(stage, error_domain, error_code)`는 로딩 단계의 실패다. PhotoKit 실패 후 원본/PHPicker 재시도로 복구할 수 있으므로 최종 누락 사진 수와 같지 않다. 촬영일이 없는 사진의 `capture_date_missing`도 이미지 표시 실패를 의미하지 않는다.
- `photo_load_completed(requested, succeeded, failed)`는 수동 선택 배치 또는 추천 페이지가 끝났을 때의 최종 건수다. 재시도로 복구한 사진은 `succeeded`에 포함한다. 수동 선택 취소는 집계하지 않는다.
- 추가 원본 읽기 실패는 `app_error(operation=photo_original_preview/photo_cached_original)`로 수집한다. 정상 코루틴 취소는 제외한다.
- 사진 바이트·EXIF 값·파일명·로컬 식별자·URL은 이벤트 파라미터에 넣지 않는다. 실제 Firebase 수신은 별도 검증이 필요하다.

### 기존 영상 이벤트와의 호환성 (PR #487 통합)

`video_opened`, `video_action`, `video_generation_result`와 기록 공유의 `record_share_*` 이벤트, 상세 `share_media_*` / `share_video_status_changed` 이벤트를 함께 유지한다. 기존 이벤트는 제품 행동 집계를 위한 상위 이벤트이고, 상세 이벤트는 단계별 성공·실패와 오류 진단용이므로 두 계열의 건수를 합산하지 않는다.

- 현재 `video_opened`는 일지의 영상 만들기 클릭(`source=journal`), `share_media_action/action=opened`는 다이얼로그가 열린 시점이다.
- 기존 `preview_download`는 URL 요청부터 파일 확보까지 포함한다. 상세 계측은 `download_url`과 `download`를 분리한다.
- 공유 API 반환은 기존 `video_action`에서는 `success`, 상세 `share_sheet`에서는 `handoff`다. 외부 게시 완료를 뜻하지 않는다.
- 최종 생성 결과는 기존 계약대로 컨테이너 수명 내 작업별 한 번 기록한다. 상세 상태 이력의 크기 제한과 별도로 중복을 억제한다.
- 기존 작업 결과 이벤트도 발행하지만 실제 오류의 `app_error` 보고는 상세 계측에서 담당하여 중복을 막는다. 권한 거절·미완료·기록 부족의 상세 분류는 유지한다.
