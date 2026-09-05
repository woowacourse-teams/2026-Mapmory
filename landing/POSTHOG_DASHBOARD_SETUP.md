# Mapmory PostHog 운영 대시보드 설정

갱신: 2026-09-05 · 현재 스키마: `analytics_schema_version=2`

## 목적

PostHog에서는 랜딩의 제품 체험과 Recap의 실제 사진 처리·공유·앱 전환을 한 화면에서 진단한다. GA4는 유입과 전체 스토어 전환의 기준으로 유지한다. PostHog의 비율은 같은 기간의 고유 사용자 기준으로 비교하며 이벤트 수와 혼용하지 않는다.

기존 `Landing · Product Experience v2` 대시보드는 출시 전 기록으로 보존한다. 운영용 새 대시보드는 `Landing + Recap · Growth v3`로 만들고 기본 기간을 최근 14일로 설정한다.

## 프로젝트 연결과 개인정보 기본값

두 앱 모두 같은 PostHog 프로젝트를 사용한다.

```text
VITE_POSTHOG_KEY=<Project API key>
VITE_POSTHOG_HOST=<프로젝트 설정에 표시된 Host>
VITE_POSTHOG_CAPTURE_LOCAL=false
```

메인 랜딩과 `/recap/`은 각각 독립적으로 SDK를 초기화하고 `$pageview`를 한 번 보낸다. 둘 중 한 화면을 먼저 거칠 필요가 없다. 두 값 중 하나라도 비어 있으면 PostHog은 비활성화된다.

코드 기본값은 자동 클릭·입력 수집과 자동 페이지뷰, 세션 녹화, 설문, 기능 플래그 요청, 개인 프로필, 영구 식별, GeoIP 보강을 비활성화한다. 사진 파일명·좌표·촬영시각·내용, 이메일·전화번호·주소·자유 입력은 이벤트 속성으로 보내지 않는다. PostHog 프로젝트에서도 `Discard client IP data`를 사용한다.

## 대시보드 공통 필터

모든 운영 타일에 다음 필터를 적용한다.

```text
analytics_schema_version = 2
traffic_type = external
```

랜딩 타일은 `surface=landing`, Recap 타일은 `surface=recap`을 추가한다. 내부 QA는 `?internal=1`로 표시하고 운영 수치에서 제외하며 `?internal=0`으로 해제한다. 이 표시는 인증 기능이 아니다.

## 운영 타일

### 01 · 전체 앱 전환

Trends에서 `$pageview`와 `download_click`의 고유 사용자를 같은 기간에 표시한다. `download_click`은 `surface`, `store`, `cta_placement`로 나눈다.

```text
앱 전환 의도율 = download_click 고유 사용자 / $pageview 고유 사용자
```

스토어 이동이며 설치 완료가 아니다. 두 스토어 클릭을 합산하지 말고 `download_click` 고유 사용자로 중복을 제거한다.

### 02 · 랜딩 체험 퍼널

순서 고정 Funnel, 전환 창 1일:

```text
$pageview → experience_view → experience_start
→ memory_open (open_index = 1) → download_click
```

`experience_type`과 기기 유형으로 나눈다. 이 퍼널은 병목 진단용이며 스토어 전환의 필수 경로로 해석하지 않는다.

### 03 · Recap 실제 사진 퍼널

`journey_source=photos`를 고정한 순서 고정 Funnel, 전환 창 1일:

```text
travel_map_photo_select → travel_map_processing_complete
→ travel_map_recap_view → travel_map_demand_view → download_click
```

샘플 사용자는 이 타일에서 제외한다. 공유 없이 앱 안내로 이동한 사용자도 정상 흐름이다.

### 04 · Recap 사진 판독 품질

한 타일에 `travel_map_photo_select`, `travel_map_processing_complete`, `travel_map_photo_analysis_empty`, `travel_map_processing_failed` 고유 사용자 추이를 표시한다. 먼저 `picker_source=file_system_access|legacy_input`으로 나눠 원본 파일 선택 경로가 GPS 성공률을 개선하는지 확인하고, 빈 결과는 `metadata_missing_photos`, `read_failed_photos`, `unsupported_photos`로 진단한다.

```text
경로 생성률 = processing_complete 고유 사용자 / photo_select 고유 사용자
GPS 유효 비율 = sum(valid_gps_photos) / sum(selected_photos)
```

GPS 유효 비율은 속성 합계를 지원하는 Trends 또는 HogQL로 만들고 분모가 0인 기간은 제외한다. 사진 수는 입력 호환성 진단에만 사용한다.

### 05 · 공유·저장 결과

`journey_source=photos`를 기본으로 다음을 표시한다.

- `travel_map_share_result`, breakdown `result`
- `travel_map_video_saved`와 `travel_map_image_saved`, filter `result=download_started`
- `travel_map_export_failed`, breakdown `format`, `error_type`

`download_started`는 브라우저가 다운로드를 시작했다는 뜻이며 OS 저장 완료로 부르지 않는다. `cancelled`는 기술 실패와 분리한다.

### 06 · Recap 샘플 대비 실제 사진

다음 Funnel을 `journey_source`로 나눈다.

```text
travel_map_recap_view → travel_map_app_bridge_click
→ travel_map_demand_view → download_click
```

`demo`가 높고 `photos`가 낮으면 앱 관심보다 사진 판독·결과 품질 문제를 먼저 본다. 표본이 100명보다 적을 때는 작은 비율 차이에 성공·실패 판정을 붙이지 않는다.

### 07 · 랜딩 체험 깊이

- `experience_end.active_duration_ms`: 중앙값과 25·75 백분위
- `experience_end.unique_memories_opened`: 0개, 1개, 2개 이상
- `memory_open(open_index=1).time_since_start_ms`: 중앙값

평균 체류시간 하나만으로 체험 성공을 판단하지 않는다.

## 출시·운영 검증

- [ ] 메인 랜딩과 `/recap/`을 각각 새 탭에서 직접 열어 `$pageview` 수신 확인
- [ ] 실제 사진 흐름에서 `photo_select → processing_complete|analysis_empty` 중 정확히 한 경로 확인
- [ ] 실제 사진 이벤트의 `picker_source`가 `file_system_access|legacy_input` 중 하나이며 두 경로의 생성률 비교가 가능한지 확인
- [ ] 샘플과 실제 사진에 `journey_source=demo|photos`가 구분되는지 확인
- [ ] 공유 취소·다운로드 폴백·실패가 서로 다른 `result`로 보이는지 확인
- [ ] `surface`, `analytics_schema_version`, `traffic_type` 공통 속성 확인
- [ ] 파일명·좌표·촬영시각·이메일 등 개인정보 속성이 없는지 확인
- [ ] `?internal=1` 이벤트가 운영 타일에서 제외되는지 확인
- [ ] GA4 DebugView와 PostHog Live Events의 동일 동작 순서 비교

코드 반영, 프로젝트 환경변수 설정, Live Events 실수신, 대시보드 저장은 각각 별도 증거로 확인한다. 어느 하나를 다른 단계의 완료로 보고하지 않는다.
