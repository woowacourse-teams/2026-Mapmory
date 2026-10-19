# Mapmory 행동 및 오류 수집 계약 (schema 2)

## 목적과 적용 시점

기록 생성 → 나중에 다시 열람하는 흐름, 행동별 리텐션, 핵심 기능의 실패 건수/영향 사용자/실패율을 분석한다. 이 문서는 코드 계약이다. 실제 Firebase 수신·출시 버전 반영·BigQuery 연결·GA4 맞춤 정의 등록은 별도 검증이 필요하다. 이전 버전에서 수집하지 않은 이벤트를 소급 복원할 수는 없다.

Android/iOS Firebase 어댑터는 `event_schema_version="2"`를 붙인다. `duration_ms`, `result_count`, `http_status`는 숫자로 전송한다. 기존 `count`, `photo_count` 등 문자열 파라미터의 타입은 기존 맞춤 정의와 충돌하지 않도록 유지한다. Debug/Internal의 기존 Analytics 비활성화 정책은 유지한다.

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
| 실제 생성·수정 | `record_save_completed(mode=create/edit, record_id, photo_count, has_title, has_end_date, has_photos, has_content, has_tags)` | 생성은 repository 성공 후 새로 보완. 수정은 기존 화면의 save 성공 처리 지점과 기존 파라미터를 유지하고 record_id만 추가. repository에서는 수정 완료를 중복 발행하지 않음 |
| 백그라운드 실패 | `record_background_save_failed` | 자동 재시도까지 끝나고 실패 상태가 된 작업 사이클. 앱 재시작으로 복구한 불확실 상태는 과거 실패로 재발행하지 않음 |
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
| client_logic | mobile | IllegalState/IllegalArgument 계열. 모바일 조사 우선이며 확정 귀책은 아님 |
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
