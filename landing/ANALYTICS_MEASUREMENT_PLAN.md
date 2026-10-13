# Mapmory 현재 랜딩·Recap 측정 계획

갱신: 2026-10-13 · GA4 웹 스트림: `G-MC93CZWLZF`

## 판단할 성과

**1차 성과는 App Store / Google Play로 이동한 방문자 비율이다.**
지도 체험을 건너뛰고 헤더에서 바로 스토어를 열어도 정상 전환이다.
스토어 클릭은 설치 완료가 아니다. 실제 설치·첫 기억 생성은 앱 분석을 별도로 연결해야 한다.

| 화면 | 현재 경험 | 측정에서 제외할 것 |
| --- | --- | --- |
| `/` 첫 화면 | 사진 찾기 데모 1회 재생, 장소 칩(제주·일본·미국)으로 다시 보기 | 자동 재생을 체험 시작으로 세지 않음; 장소 칩 탭만 `hero_demo_select` |
| `/` 사용 방법(`#how`) | 스크롤로 넘기고 되감을 수 있는 5장면 이야기: 사진마다 찍은 곳 → 그곳 사진만 모임 → 찾기는 폰 안에서 → 고른 사진만 올라감 → 지도에 칠해짐 (예시 그림, 앱 화면·버튼 없음). 동작 줄이기·낮은 화면(가로로 든 폰)·큰 글씨는 다섯 장면을 쌓은 정적 목록 | 스크롤·장면 전환을 체험 시작으로 세지 않음; `experience_*`를 보내지 않고 도달 이벤트 두 개만 보냄 |
| `/` 세계 지도 | 나라 선택 후 별도 기억 패널 조회 | 안내 닫기, 기본 사진, 단순 터치, 모바일 세로 스크롤 |
| `/recap/` | 내 사진/샘플로 경로 구성→영상 미리보기→공유/저장→앱 안내 | 샘플 성공을 내 사진 처리 성공에 포함하지 않음 |

## 공통 필터와 수치 정의

- 자동 `page_view`와 커스텀 이벤트: `analytics_schema_version=2`, `surface=landing|recap`, `traffic_type=external|internal`.
- 운영 보고서는 **schema 2 + traffic_type=external + 해당 surface**를 모두 적용한다. 과거 오집계와 내부 QA를 섞지 않는다.
- 화면 버전은 `landing_version=v4`, `campaign_version=travel-map-v1` 유지. 측정 방식 변경은 별도 schema로 분리한다.
- 주요 KPI: 같은 기간 `download_click` **총 사용자 수** ÷ 해당 화면 `page_view` **총 사용자 수**. 이벤트 수와 사용자 수를 혼용하지 않는다. 두 스토어를 모두 누른 사용자는 전체 전환자에서 한 번만 센다.
- 위치별 수치는 클릭 사용자 분포다. CTA 노출 이벤트가 없으므로 위치별 CTR로 명명하지 않는다.
- 기기는 기본 `device category`로 비교한다. 모바일 모션 완료와 데스크톱 스크롤 완료를 직접 비교하지 않는다.
- 체험 퍼널은 진단용이지 전체 전환의 필수 경로가 아니다. 체험/비체험 전환율 차이는 관찰된 상관관계이며 인과 효과가 아니다.

## 랜딩 이벤트 사전

| 이벤트 | 현재 발생 지점 | 속성 / 중복 정책 |
| --- | --- | --- |
| `experience_cta_click` | 헤더 메뉴·사용법 섹션의 지구본 미리 보기 링크, 헤더 사용 방법 링크(how_story, header_nav; how_story 배포 전에는 how_play) | `experience_type`, `cta_placement`; 클릭마다 |
| `experience_view` | 활성 탭에서 영역이 뷰포트 기준 50% 이상 1초 노출, 또는 명시적 첫 조작. (how_story 배포 전 how_play는 사용법 폰 화면 `.how-play-phone` 기준) | `experience_type`; 유형별 페이지당 1회 |
| `experience_start` | 나라 선택·드래그 의도·확대 (how_story 배포 전에는 사용법 첫 장소 탭 place_select도) | `interaction_type`; 유형별 페이지당 1회 |
| `memory_open` | 패널이 React 화면에 반영된 뒤 | `memory_id`, `selection_source`, `open_index`, `time_since_start_seconds`; 첫 연속 체험에서 기억별 1회 |
| `memory_photo_swiped` | 모바일 기억 바텀시트에서 첫 실제 사진 스와이프 | `memory_id`, `photo_index`, `photo_count`, `time_since_memory_open_seconds`; 패널을 열 때마다 1회 |
| `memory_sheet_closed` | 닫기 버튼 또는 브라우저 뒤로가기로 기억 바텀시트 종료 | `memory_id`, `close_method`, `max_photo_index`, `photos_viewed`, `time_since_memory_open_seconds`; 패널을 열 때마다 1회 |
| `experience_end` | 체험을 1.5초 벗어나거나 pagehide | `active_duration_seconds`, `unique_memories_opened`, `last_completed_step`, `exit_reason`; 유형별 첫 연속 체험 1회. (how_story 배포 전 how_play: `last_completed_step=experience_start\|photo_pick\|how_play_save`, `unique_memories_opened` 항상 0) |
| `download_click` | 실제 App Store·Google Play 링크 클릭 | `store=app_store|google_play`, `cta_placement`; **설치 완료 아님** |
| `hero_demo_select` | 히어로 사진 찾기 데모의 장소 칩(제주·일본·미국) 탭 | `experience_type=hero_demo`, `demo_place`; 탭마다. 자동 재생은 기록하지 않음. `demo_place`는 how_story 배포 전 `how_play_save`와 공유했으며 값은 겹치지 않음 |
| `how_story_view` | 사용 방법 섹션(`#how`)이 활성 탭에서 화면에 보이는 상태로 1초 연속 | `experience_type=how_story`, `story_mode=scroll\|static`(보낼 때의 방식); 페이지당 1회. 첫 `how_story_scene`이 먼저 오게 되면(장면 한가운데로 복원된 새로고침 등) 그 장면 바로 앞에 보내므로 늘 장면보다 먼저 온다. `static`은 동작 줄이기, 높이 500px 미만 화면(가로로 든 폰), 그림이 너무 작아지는 큰 글씨 |
| `how_story_scene` | 한 장면에 0.7초 머묾(탭 활성). 스크롤 방식: 그 장면이 고정된 화면에 떠 있을 때(02·04는 장면 안 두 번째 동작이 시작된 뒤부터). 정적 목록: 그 장면 항목이 절반 이상 보일 때 | `experience_type=how_story`, `scene_index=1-5`; 장면별 페이지당 1회. 제목 화면은 장면이 아님. 빠르게 지나친 장면은 세지 않고(0.7초 타이머가 울리면 다음 프레임에서 그 장면이 아직 멈춰 있는지 다시 확인하므로, 메인 스레드가 잠시 멈춰 타이머가 늦게 울려도 이미 지나친 장면은 세지 않음), 되감아 다시 봐도 재전송 안 함 |
| `how_play_save` (how_story 배포 전) | 저장 후 칠해진 지도가 화면에 반영된 뒤 | `experience_type=how_play`, `demo_place=busan\|gangwon\|gyeongbuk`, `save_index=1-3`, `selected_photos`; 페이지당 장소별 1회. 2026-10-08부터 how_story 배포일까지의 데이터에만 있음 |

체험 유형: `globe`. (`how_play`는 2026-10-08부터 how_story 배포일까지. `how_story`는 `experience_*`를 보내지 않고 `experience_cta_click`·`how_story_*`에만 붙음. `korea_detail`, `korea_memory_add`, `download_cta_click`의 `cta_placement=korea_memory`는 2026-10-08 대한민국 체험 섹션 삭제 전 데이터에만 있음)
`experience_view`·`experience_start`·`experience_end`를 세는 보고서는 반드시 `experience_type`으로 거른다. 사용법 섹션이 히어로 바로 아래라 합치면 지구본 노출이 약 두 배로 보인다. `demo_place` 분석도 이벤트 이름을 먼저 고정한다.
체험 진입 위치: `header_nav`, `how_section`(사용 방법 섹션 아래 지구본 미리 보기 링크). (`hero`, `hero_mobile`, `hero_handoff`, `hero_reduced_handoff`는 2026-10 사진 찾기 히어로 교체 전 데이터에만 있음)
스토어 위치: `header`, `hero`, `final` (Recap은 `demand_primary`).
`waitlist_*`는 비노출 폴백으로 보존하지만 현재 퍼널에서 제외한다.

체험시간은 탭과 영역이 함께 보이는 동안만 누적한다. 종료 전송은 브라우저 종료 시 누락될 수 있다.
첫 연속 체험 종료 후 재진입한 탐색은 깊이·시간에 추가하지 않는다. 진단 보고서에 이 제한을 표시한다.
`how_story_view`·`how_story_scene`은 체험이 아니라 도달 측정이다. 페이지 로드당 각 1회(장면은 장면별 1회)만 보내므로 "어디까지 멈춰서 봤나"(가장 큰 `scene_index`)는 볼 수 있지만 체류 시간이나 재시청은 알 수 없다. 스크롤 방식과 정적 목록은 머묾의 기준이 달라 `story_mode`로 나눠 본다. (how_story 배포 전 `how_play_save`는 체험 종료 후 재진입 저장도 기록했다.)

## Recap 이벤트 사전

모든 행동/결과의 `journey_source=photos|demo`를 구분한다. 개인 사진 좌표·파일명·촬영시각·사진 내용·오류 원문은 전송하지 않는다.

| 이벤트 | 의미 / 추가 속성 |
| --- | --- |
| `travel_map_photo_select` / `travel_map_demo_start` | 내 사진 선택 / 샘플 시작; `selected_photos`, 내 사진은 `picker_source=file_system_access|legacy_input` |
| `travel_map_processing_complete` | 경로 생성 가능; `selected_photos`, `valid_gps_photos`, `duration_seconds`, `picker_source` |
| `travel_map_photo_analysis_empty` | 분석했으나 GPS 경로 없음; 메타데이터 누락/판독 실패/미지원 개수, `picker_source` |
| `travel_map_processing_failed` | 분석 예외; `error_type=analysis_failed` |
| `travel_map_replay_complete` | 경로 재생 종료. 영상 파일 생성 성공을 뜻하지 않음 |
| `travel_map_recap_view` | 결과 미리보기 표시 |
| `travel_map_share_click` | 영상 공유 시도 |
| `travel_map_share_result` | `result=shared|download_started|cancelled|failed`; 공유 API 완료·다운로드 폴백·취소·실패 |
| `travel_map_video_save_start` / `travel_map_image_save_start` | 파일 내보내기 시도 |
| `travel_map_video_saved` / `travel_map_image_saved` | 호환 이름 유지. **`result=download_started`**, OS 저장 완료 확인 불가 |
| `travel_map_export_failed` | 내보내기 실패; `format`, `error_type=export_failed` |
| `travel_map_app_bridge_click` / `travel_map_demand_view` | 앱 알아보기 클릭 / 앱 안내 표시 |
| `travel_map_landing_click` | 메인 랜딩 이동. 전환 아님 |
| `download_click` | Google Play 이동; `store=google_play`, `cta_placement=demand_primary` |

`travel_map_app_store_click`은 새 코드에서 발생시키지 않는다. 공통 전환과 중복 합산하지 않는다.
Recap 내부 화면을 가짜 `page_view`로 보내지 않고 단계별 이벤트로 분석한다.

## GA4 보고서 설계와 설정 상태

**콘솔 저장·배포·실제 수신은 별도 확인이 필요하다.**
기존 GA4 속성(Mapmory Landing Page, `551158914`) 접속과 아래 2026-10-08 맞춤 측정기준 7개 등록까지 확인. 새 보고서 저장(02 수정, 02-b·02-c 생성은 탐색 보고서라 콘솔에서 직접)·실수신은 아직 미검증이다.

| 보고서 | 데이터/필터 | 판단 |
| --- | --- | --- |
| 01 랜딩 성과 | `surface=landing`; page_view/download_click 총 사용자; 기기·소스/매체·캠페인 | 체험 없이 스토어 이동한 사용자도 포함한 전체 KPI |
| 02 지구본 체험 | `experience_view → experience_start → memory_open → download_click`; 앞 세 단계에 `experience_type≠how_play`(2026-11-05부터 `=globe`); 기기 분리 | 지구본 조작·기억 조회 병목. 전체 필수 퍼널 아님. 10/8 이전 행은 유형이 `(not set)`이고 삭제된 대한민국 체험이 섞여 있음. `=globe`로 바로 거르면 이 기간이 빠짐. how_story는 `experience_*`를 보내지 않으므로 이 보고서의 필터는 바꾸지 않아도 됨 |
| 02-b 사용법 체험 (how_story 배포 전) | `experience_type=how_play`; 닫힌 퍼널 `experience_view → experience_start → how_play_save → how_play_save(save_index=2) → (save_index=3)`; 기기 분리 | 2026-10-08부터 how_story 배포일까지의 기록 비교용. 배포 후 새 데이터는 없음 |
| 02-c 사용법 후 스토어 (how_story 배포 전) | 닫힌 퍼널 `how_play_save → download_click(cta_placement≠demand_primary)` | 위와 같은 기간의 기록 비교용 |
| 사용 방법 도달 (PostHog 전용) | `how_story_view → how_story_scene(1) → … → (5)`, `story_mode`로 나눔; `story_mode`·`scene_index`가 GA4 미등록이라 PostHog에서만 봄. GA4에서는 이벤트 이름별 사용자 수만 | 장면별 도달·이탈. 상관관계이며 이야기의 인과 효과 아님 |
| 03 스토어·CTA | `event_name=download_click`; surface/store/cta_placement; 총 사용자·이벤트 수 | 목적지·위치별 클릭 분포. 설치/CTR 아님 |
| 04 Recap 내 사진 | surface=recap, journey_source=photos; photo_select→processing_complete→recap_view→demand_view→download_click | 샘플 제외 흐름. 공유 없이 앱 안내로 가도 정상 |
| 05 Recap 품질 (등록 전 PostHog 전용) | surface=recap; journey_source/result/error_type/format별 처리·공유·내보내기 | GPS 부재·기술 실패·취소·다운로드 폴백 구분 |

GA4 맞춤 정의 등록 상태(이벤트 범위, 속성 `551158914`):
- 2026-10-08 등록: `store`, `cta_placement`, `experience_type`, `analytics_schema_version`, `landing_version`, `demo_place`, `journey_source`.
- 2026-10-08 09:52 UTC 등록: `save_index`(how_play 코드 배포 전). 값은 문자열 `1`·`2`·`3`으로 들어오므로 문자열 일치로 거른다.
- 미등록(현재 PostHog에서만 분석): `surface`, `traffic_type`, `result`, `error_type`, `format`, `last_completed_step`, `memory_id`, `selection_source`, `open_index`, `campaign_version`, `interaction_type`, `exit_reason`, `close_method`, `story_mode`과 숫자 값 `active_duration_seconds`, `time_since_start_seconds`, `time_since_memory_open_seconds`, `unique_memories_opened`, `selected_photos`(how_story 배포 후 Recap 전용), `valid_gps_photos`, `photo_index`, `photo_count`, `max_photo_index`, `photos_viewed`, `scene_index`. GA4 보고서에 필요해질 때 등록한다.
- `save_index`는 how_story 배포 후 랜딩에서 보내지 않는다. 기록 비교를 위해 GA4 등록은 그대로 둔다.
- GA4 보고서는 미등록 `surface` 대신 페이지 경로로 거른다(랜딩 `/`, Recap `/recap/`로 시작). `traffic_type=external`은 GA4 탐색에서 `테스트 데이터 필터 이름`이 `Internal Traffic`인 행을 빼는 것으로 대신한다. 05는 `result`·`error_type`·`format`이 미등록이라 등록 전까지 PostHog에서만 본다.
- GA4는 등록 전 데이터를 소급하지 않는다. `experience_type`은 2026-10-08 이전 행이 `(not set)`이며, 나중에 등록하는 항목도 등록 시점부터만 보인다.

`open_index`는 순서 필터용이며 합계를 KPI로 쓰지 않는다. `save_index`도 순서 필터용, 합계 KPI 아님. `add_index`·`korea_memory_add`·`download_cta_click`·`cta_placement=korea_memory`는 재사용 금지. (`add_index`는 2026-10-08 대한민국 체험 삭제 전 데이터에만 있음)

주요 이벤트 `download_click`은 ‘앱 스토어 이동’으로 설명하며 세션당 한 번 집계를 권장한다. `how_story_view`·`how_story_scene`(그리고 이전 `how_play_save`)과 `experience_*`는 주요 이벤트로 지정하지 않는다.
사용자 전환율은 주요 이벤트 횟수가 아니라 총 사용자 기준으로 별도 계산한다.
과거 `generate_lead`와 보고서는 삭제하지 않고 출시 전으로 구분한다. 신규 측정기준은 등록 후 처리 시간이 필요하며 소급 적용을 가정하지 않는다.

## 유입 정보·QA·배포

- 예약된 `campaign_name`을 제품명으로 덮어쓰지 않는다. 원래 UTM을 GA4가 처리한다.
- Recap→메인 내부 링크에 UTM을 붙이지 않는다. `travel_map_landing_click`으로 진단한다.
- 외부 Google Play Install Referrer 표기는 유지. 실제 설치 귀속은 별도 검증 대상이다.
- 두 앱 모두 `VITE_GA_MEASUREMENT_ID` 명시 필수. CodeBuild에 운영 ID가 전달되는지 배포 전에 확인한다.
- 두 앱은 같은 `VITE_POSTHOG_KEY`와 `VITE_POSTHOG_HOST`를 사용하지만 각각 SDK를 초기화한다. `/recap/` 직접 진입도 메인 랜딩을 거치지 않고 `$pageview`와 Recap 이벤트를 전송해야 한다.
- CodeBuild는 측정 ID가 없거나 QA/debug 플래그가 켜진 경우 빌드 전에 실패시켜 추적이 빠진 운영 배포를 방지한다.
- `map-mory.com`/`www.map-mory.com` 외 기본 비활성. QA만 `VITE_GA_CAPTURE_LOCAL=true`, `VITE_GA_DEBUG=true`와 `?internal=1` 사용.
- 내부 표시는 같은 출처 저장소 공유. 보고서에서 제외하되 영구 데이터 제외 필터는 임의 활성화하지 않는다.
- 동의 설정·보존기간·Ads 연동·데이터 삭제 정책은 이번 변경에서 수정하지 않는다.
- 가짜 측정 ID·네트워크 차단 테스트 통과는 GA4 실수신 증거가 아니다.
- PostHog 운영 타일과 필터는 [POSTHOG_DASHBOARD_SETUP.md](POSTHOG_DASHBOARD_SETUP.md)를 따르며, 코드 연결과 Live Events 수신·대시보드 저장을 각각 확인한다.
- `save_index`는 how_play 코드 배포 전에 GA4에 등록했다(2026-10-08). `experience_type` 필터(정적 대시보드 `랜딩 체험 흐름`, PostHog 02·07, GA4 02, [KPI 문서](landing-kpi-framework.md))는 how_story가 `experience_*`를 보내지 않으므로 그대로 맞다.
- how_story 코드에는 GA4 관리 화면 변경이 필수가 아니다(새 이벤트는 이름으로 집계되고, 새 속성 `story_mode`·`scene_index`는 PostHog에서 본다). GA4 보고서에서 장면별로 보려면 그때 두 속성을 등록하며, 등록 전 데이터는 소급되지 않는다.
- how_story 배포 뒤 남은 수동 작업(아직 하지 않음): `?internal=1`로 DebugView/Realtime과 PostHog Live Events에서 `how_story_view`·`how_story_scene` 수신을 각각 확인한다. PostHog 02-b·02-c·02-d 타일을 [대시보드 문서](POSTHOG_DASHBOARD_SETUP.md)대로 바꾸고 how_play 타일은 기간을 고정해 남긴다. 배포일을 이 문서와 [측정 이력](ANALYTICS_MEASUREMENT_HISTORY.md)의 how_play 종료일로 적는다. 정적 대시보드와 `refresh.mjs`(`HOW_PLAY_SINCE`)는 별도 후속 작업이다.

이전 계획과 가설은 [보존 문서](ANALYTICS_MEASUREMENT_HISTORY.md)에 남긴다.
