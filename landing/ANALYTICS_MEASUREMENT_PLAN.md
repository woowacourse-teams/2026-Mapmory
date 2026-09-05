# Mapmory 현재 랜딩·Recap 측정 계획

갱신: 2026-09-03 · GA4 웹 스트림: `G-MC93CZWLZF`

## 판단할 성과

**1차 성과는 App Store / Google Play로 이동한 방문자 비율이다.**
지도 체험을 건너뛰고 헤더에서 바로 스토어를 열어도 정상 전환이다.
스토어 클릭은 설치 완료가 아니다. 실제 설치·첫 기억 생성은 앱 분석을 별도로 연결해야 한다.

| 화면 | 현재 경험 | 측정에서 제외할 것 |
| --- | --- | --- |
| `/` 첫 화면 | 모바일 사진→기록→지도 1회 모션 / 데스크톱 스크롤 연출 | 자동 모션·사진 등장·스크롤을 기록 생성이나 체험 시작으로 세지 않음 |
| `/` 세계 지도 | 나라 선택 후 별도 기억 패널 조회 | 안내 닫기, 기본 사진, 단순 터치, 모바일 세로 스크롤 |
| `/` 대한민국 지도 | 예시 사진 추가→지역 색칠→기억 조회 | 실제 사용자 기록 저장으로 해석하지 않음 |
| `/recap/` | 내 사진/샘플로 경로 구성→영상 미리보기→공유/저장→앱 안내 | 샘플 성공을 내 사진 처리 성공에 포함하지 않음 |

## 공통 필터와 수치 정의

- 자동 `page_view`와 커스텀 이벤트: `analytics_schema_version=2`, `surface=landing|recap`, `traffic_type=external|internal`.
- 운영 보고서는 **schema 2 + traffic_type=external + 해당 surface**를 모두 적용한다. 과거 오집계와 내부 QA를 섞지 않는다.
- 화면 버전은 `landing_version=v3`, `campaign_version=travel-map-v1` 유지. 측정 방식 변경은 별도 schema로 분리한다.
- 주요 KPI: 같은 기간 `download_click` **총 사용자 수** ÷ 해당 화면 `page_view` **총 사용자 수**. 이벤트 수와 사용자 수를 혼용하지 않는다. 두 스토어를 모두 누른 사용자는 전체 전환자에서 한 번만 센다.
- 위치별 수치는 클릭 사용자 분포다. CTA 노출 이벤트가 없으므로 위치별 CTR로 명명하지 않는다.
- 기기는 기본 `device category`로 비교한다. 모바일 모션 완료와 데스크톱 스크롤 완료를 직접 비교하지 않는다.
- 체험 퍼널은 진단용이지 전체 전환의 필수 경로가 아니다. 체험/비체험 전환율 차이는 관찰된 상관관계이며 인과 효과가 아니다.

## 랜딩 이벤트 사전

| 이벤트 | 현재 발생 지점 | 속성 / 중복 정책 |
| --- | --- | --- |
| `experience_cta_click` | 헤더·각 히어로 체험 진입 링크 | `experience_type`, `cta_placement`; 클릭마다 |
| `experience_view` | 활성 탭에서 영역이 뷰포트 기준 50% 이상 1초 노출, 또는 명시적 첫 조작 | `experience_type`; 유형별 페이지당 1회 |
| `experience_start` | 나라 선택·드래그 의도·확대·예시 사진 추가 | `interaction_type`; 유형별 페이지당 1회 |
| `memory_open` | 패널이 React 화면에 반영된 뒤 | `memory_id`, `selection_source`, `open_index`, `time_since_start_ms`; 첫 연속 체험에서 기억별 1회 |
| `korea_memory_add` | 대한민국 예시 사진 색칠 모션 완료 | `memory_id`, `add_index`, `time_since_start_ms`; 기억별 1회 |
| `experience_end` | 체험을 1.5초 벗어나거나 pagehide | `active_duration_ms`, `unique_memories_opened`, `last_completed_step`, `exit_reason`; 유형별 첫 연속 체험 1회 |
| `download_cta_click` | ‘내 기억 지도도 만들기’로 `#download`에 이동 | `cta_placement=korea_memory`; **전환 아님** |
| `download_click` | 실제 App Store·Google Play 링크 클릭 | `store=app_store|google_play`, `cta_placement`; **설치 완료 아님** |

체험 유형: `globe`, `korea_detail`.
체험 진입 위치: `header_nav`, `hero`, `hero_mobile`, `hero_handoff`, `hero_reduced_handoff`.
스토어 위치: `header`, `hero`, `final` (Recap은 `demand_primary`).
`waitlist_*`는 비노출 폴백으로 보존하지만 현재 퍼널에서 제외한다.

체험시간은 탭과 영역이 함께 보이는 동안만 누적한다. 종료 전송은 브라우저 종료 시 누락될 수 있다.
첫 연속 체험 종료 후 재진입한 탐색은 깊이·시간에 추가하지 않는다. 진단 보고서에 이 제한을 표시한다.

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
기존 GA4 속성(Mapmory Landing Page, `551158914`) 접속까지 확인. 새 보고서 저장·실수신은 아직 미검증이다.

| 보고서 | 데이터/필터 | 판단 |
| --- | --- | --- |
| 01 랜딩 성과 | `surface=landing`; page_view/download_click 총 사용자; 기기·소스/매체·캠페인 | 체험 없이 스토어 이동한 사용자도 포함한 전체 KPI |
| 02 랜딩 체험 | 유형별 `experience_view → experience_start → memory_open → download_click`; 기기 분리 | 세계/대한민국 조작·기억 조회 병목. 전체 필수 퍼널 아님 |
| 03 스토어·CTA | `event_name=download_click`; surface/store/cta_placement; 총 사용자·이벤트 수 | 목적지·위치별 클릭 분포. 설치/CTR 아님 |
| 04 Recap 내 사진 | surface=recap, journey_source=photos; photo_select→processing_complete→recap_view→demand_view→download_click | 샘플 제외 흐름. 공유 없이 앱 안내로 가도 정상 |
| 05 Recap 품질 | surface=recap; journey_source/result/error_type/format별 처리·공유·내보내기 | GPS 부재·기술 실패·취소·다운로드 폴백 구분 |

이벤트 범위 맞춤 측정기준(기존 항목 재사용):
`analytics_schema_version`, `surface`, `traffic_type`, `experience_type`, `cta_placement`, `store`, `journey_source`, `result`, `error_type`, `format`, `last_completed_step`.
세부 탐색용 추가 기준: `memory_id`, `selection_source`, `landing_version`, `campaign_version`, `interaction_type`, `exit_reason`.
맞춤 측정항목: `active_duration_ms`/`time_since_start_ms`(밀리초), `unique_memories_opened`/`selected_photos`/`valid_gps_photos`(표준).
`open_index`/`add_index`는 순서 필터용이며 합계를 KPI로 쓰지 않는다.

주요 이벤트 `download_click`은 ‘앱 스토어 이동’으로 설명하며 세션당 한 번 집계를 권장한다.
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

이전 계획과 가설은 [보존 문서](ANALYTICS_MEASUREMENT_HISTORY.md)에 남긴다.
