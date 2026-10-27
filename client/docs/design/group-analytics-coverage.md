# 그룹 행동·오류 계측 점검

2026-10-23. 이번 그룹 기능의 현재 화면·작업 상태를 대상으로 한다. 앱의 다른 기존 기능 전체를 새로 계측한 것은 아니다. 실제 서버 API는 아직 연결되지 않았으며 샘플 성공·실패는 운영 지표로 해석하지 않는다.

## 이벤트 계약

| 이벤트 | 의미 |
| --- | --- |
| `group_action` | 실제 UI 콜백 1회. 행동 종류는 고정된 `action` 값으로 표현한다. |
| `group_viewed` | 그룹 목록·홈·앨범·사진 화면 또는 시트·삭제 확인의 표시. 재구성 중복 제외. |
| `group_action_rejected` | 서비스 준비 중 또는 `GroupRuleException`의 정책 거절. 운영 오류와 구분. |
| `group_preview_result` | 샘플 생성·참여·초대·저장·실패·재시도·삭제 결과. 실제 저장/삭제 성공 이벤트를 발행하지 않음. |
| 기존 `app_error` | 행동 처리, 삭제 준비, 입력 갱신, 상태 저장·복원에서 발생한 실제 Exception. 기존 분류·예외 타입·owner_hint 유지. |
| 기존 `bottom_nav_clicked` | 그룹 진입·이탈·재선택. 별도 group_action으로 같은 탭 클릭을 중복 기록하지 않음. |
| 기존 `app_error(operation=photo_display)` | 재사용 이미지 컴포넌트의 이미지 표시 실패. 그룹에서 중복 발행하지 않음. |

행동 이벤트에는 변경 전 문맥을, 미리보기 결과에는 변경 후 문맥을 붙인다. 이름·코드 입력은 시트 표시당 첫 수정 1회, 스크롤은 종료당 1회, 사진 넘김은 settled page가 바뀐 경우만 수집한다. 개별 키 입력·스크롤 프레임·추천 사진 ID는 수집하지 않는다.

공통 필드: `feature=group`, `screen`, `overlay`, `execution_mode=preview/unavailable`, `data_origin=sample`, `stage`, `target_type`, `input_type`, `selected_count`, `record_count`, `photo_count`, `event_schema_version=2`. 카운트 필드는 현재 공통 Map<String,String> 어댑터의 문자열 계약을 따른다. 기존 숫자 변환 필드의 타입은 변경하지 않는다.

`app_error`에는 같은 구조 문맥과 `operation`, `action`(행동이 있는 경우), `error_category`, `error_type`, `owner_hint`, API 예외의 `http_status`를 붙인다. 실제 앱 로직 Exception은 미리보기에서 발생해도 수집하고 execution_mode로 구분한다. 시뮬레이션 실패 버튼은 app_error를 만들지 않는다. CancellationException은 오류에서 제외하고 원래 취소 동작을 유지한다. 예상 정책 거절도 원래 예외 동작은 유지한다.

## 콜백 점검표

| 범위 | group_action의 action 값 |
| --- | --- |
| 개발 미리보기 | `preview_toggled`, `empty_preview_toggled` |
| 그룹 목록 | `create_opened`, `join_opened`, `group_opened`, `list_retried`, `list_scrolled` |
| 그룹 생성 | `name_edited`, `create_submitted` |
| 코드·참여 | `code_edited`, `invitation_checked`, `join_confirmed`, `joined_group_opened`, `invitation_returned` |
| 초대 | `invite_opened`, `invite_generated`, `expired_simulated`, `full_simulated` |
| 그룹 홈·앨범 | `home_scrolled`, `album_opened`, `album_scrolled`, `album_add_opened`, `new_album_selected`, `place_selected` |
| 기존 기록 공유 | `record_picker_opened`, `record_selected` |
| 사진 선택 | `photos_add_opened`, `photo_picker_opened`, `photo_selected`, `photo_deselected`, `photo_save_submitted` |
| 사진 뷰어 | `photo_opened`, `photo_page_changed`, `photo_closed` |
| 삭제 | `photo_delete_opened`, `photo_delete_confirmed`, `photo_delete_cancelled`, `photo_delete_dismissed` |
| 저장·복구 | `save_advanced`, `failure_simulated`, `unknown_simulated`, `save_closed`, `save_retried`, `result_checked`, `save_discarded`, `saved_album_opened` |
| 공통 시트 | `sheet_scrolled`, `sheet_dismissed` |
| 이동 | `header_back`, `system_back`, `edge_back`, `edge_forward`, `edge_back_cancelled`, `edge_forward_cancelled` |

- 선언한 54개 행동 모두 Route 콜백에 연결되어 있는지 정적 대조했다. 이 대조는 실제 Firebase 수신 증명은 아니다.
- 목록/홈/그리드/시트의 스크롤 관찰과 사진 settled-page 관찰도 Route 콜백으로 전달된다.
- 사진 닫기 버튼과 아래로 드래그하여 닫기는 공통 photo_closed 콜백을 사용한다. 닫히지 않은 드래그의 이동량·프레임은 수집하지 않는다.
- 하위 버튼·카드 컴포넌트는 독립 이벤트를 발행하지 않는다. 실제 행동 소유자인 Route가 한 번 발행한다.
- 제스처 시작과 취소 시 화면 복원은 구분한다. 복원 콜백을 반대 방향 사용자 이동으로 기록하지 않는다. 제스처 진행 중 임시 화면의 view는 억제하고, 최종 화면이 이전과 같으면 중복 발행하지 않는다.
- Analytics 호출 실패는 emit에서 격리한다. 행동 결과나 원래 Exception을 바꾸지 않는다.
- 삭제 확인이 이미 처리된 뒤 다시 들어온 콜백에는 행동 시도만 기록하고 삭제 완료 결과를 중복 발행하지 않는다.

## 수집 제외 데이터와 미구현 범위

그룹명, 소셜 이름, 회원/그룹/앨범/기록/사진 ID, 초대 코드·토큰, 입력 본문, 경로/URL/EXIF, 예외 메시지·서버 응답은 새 그룹 계측에 포함하지 않는다. 화면 중복 방지 키의 ID는 프로세스 내부 비교에만 쓰고 전송하지 않는다.

실제 초대 API, 소셜 로그인 복귀, 원본 삭제, 10장 묶음 업로드 및 서버 연결 결과는 API가 아직 연결되지 않아 운영 성공/실패 계측 지점도 미연결이다. 이후 실제 작업 완료 경계에서 별도로 이어야 한다. 이 문서의 점검 범위는 현재 그룹 화면이며, 동시에 진행 중인 다른 인증 기능의 전체 계측은 포함하지 않는다.

GA에는 오류 분류와 예외 타입을 보낸다. 원본 메시지·스택을 보내는 새 non-fatal Crashlytics 브리지는 추가하지 않았다. 프로세스 크래시·ANR은 기존 플랫폼 Crashlytics 검증 범위다. 전역 모든 Throwable 수집을 보장하지 않는다.

## 검증

- 실제 Compose 화면 콜백을 이용한 그룹 진입·앨범·사진·스크롤·뒤로가기/취소 계측 회귀 테스트.
- 미리보기 성공·실패 구분, 재시도 단계, 취소 제외, 정책 거절, 원래 Exception 보존, logger 실패 격리, 개인정보 미포함, 중복 view·삭제 방지, 상태 복원 실패와 Firebase 이벤트 형식 테스트.
- `:shared:jvmTest --tests '*Group*Test' --tests '*RecordPhotosTest' --tests '*MapmoryNavigationHistoryTest' --tests '*Analytics*Test'`
- `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin`.
- 실제 Firebase DebugView·GA4 맞춤 정의·BigQuery 수신은 미검증. 현재 iOS Debug/Internal plist는 Analytics collection을 비활성화하고 Android internal 어댑터도 수집하지 않는다. 해당 설정은 유지했다. 미리보기의 계측 검증에는 테스트 logger를 사용했다.

참고: [Firebase 이벤트](https://firebase.google.com/docs/analytics/events), [Android logEvent 계약](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#logEvent(java.lang.String,android.os.Bundle)), [Crashlytics 보고](https://firebase.google.com/docs/crashlytics/customize-crash-reports).

## PR 분리 (2026-10-27)

그룹 샘플 화면 PR #485의 후속 계측이다. 제공자 인증·계정 로그인·로그아웃 GA는 #497에서 관리한다. 이 PR은 GroupRoute/GroupPreviewState의 행동·화면·예외와 미리보기 실행 모드만 계측한다. 실제 Firebase 수신은 별도 검증 항목이다.
