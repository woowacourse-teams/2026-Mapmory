# Mapmory PostHog 운영 대시보드 설정

갱신: 2026-10-08 · 현재 스키마: `analytics_schema_version=2`

기존 `Landing · Product Experience v2` 대시보드는 출시 전 기록으로 보존한다. 현재 랜딩 운영 타일은 `landing_version=v4`로 분리하고 최종 전환은 두 스토어의 `download_click`로 본다. 출시 알림 폼을 현재 퍼널에 다시 넣지 않는다.

## 목적

PostHog에서는 랜딩의 제품 체험과 Recap의 실제 사진 처리·공유·앱 전환을 한 화면에서 진단한다. GA4는 유입과 전체 스토어 전환의 기준으로 유지한다. PostHog의 비율은 같은 기간의 고유 사용자 기준으로 비교하며 이벤트 수와 혼용하지 않는다.

`persistence=memory`라 페이지를 새로 열 때마다 distinct_id가 바뀐다. PostHog의 고유 사용자는 사실상 페이지 로드 수이며 GA4 사용자 수와 다르다. 비율은 한 도구 안에서만 비교한다.

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

### 02 · 지구본 체험 퍼널

순서 고정 Funnel, 전환 창 1일:

```text
$pageview → experience_view → experience_start
→ memory_open (open_index = 1) → download_click
```

`landing_version=v4`로 필터링하고 `experience_view`, `experience_start`, `memory_open` 단계마다 `experience_type = globe`를 건다. 퍼널 breakdown 값은 첫 단계 `$pageview`에서 오는데 여기에는 `experience_type`이 없어 breakdown으로는 유형을 나눌 수 없다. 기기 유형으로만 나눈다. 이 퍼널은 병목 진단용이며 스토어 전환의 필수 경로로 해석하지 않는다.

### 02-a · 모바일 기억 바텀시트

`landing_version=v4`와 모바일 기기로 필터링해 `memory_open → memory_photo_swiped → memory_sheet_closed` 흐름을 본다. `close_method`, `photos_viewed`, `time_since_memory_open_seconds`로 사진을 넘겨본 뒤 닫았는지, 닫기 버튼과 브라우저 뒤로가기 중 어떤 경로가 쓰였는지 확인한다.

### 02-b · 3단계 사용법 퍼널

순서 고정 Funnel, 전환 창 1일, `landing_version=v4`:

```text
$pageview → experience_view (experience_type = how_play)
→ experience_start (experience_type = how_play)
→ how_play_save (save_index = 1) → how_play_save (save_index = 2)
```

기기 유형으로 나눈다. view→start 이탈은 장소 고르기, start→첫 저장 이탈은 사진 고르기 단계다. 02와 합산하지 않는다. 한 방문자가 두 퍼널에 모두 들어갈 수 있다.

### 02-c · 사용법 저장 후 스토어 이동

순서 고정 Funnel, 전환 창 1일:

```text
how_play_save → download_click (cta_placement ≠ demand_primary)
```

저장한 방문자의 스토어 이동이며 상관관계다. 저장한 방문자는 원래 의도가 높을 수 있으므로 체험의 효과로 해석하지 않는다.

### 02-d · 사용법 체험 깊이

- `experience_end(experience_type = how_play)`: breakdown `last_completed_step` (`experience_start` 장소만 고름, `photo_pick` 사진을 골랐지만 저장 안 함, `how_play_save`)
- `experience_end(experience_type = how_play).active_duration_seconds`: 중앙값과 25·75 백분위
- `how_play_save(save_index = 1)`: breakdown `demo_place`, `selected_photos`(4 = 사진 전부)

`experience_end`는 첫 연속 체험만 반영하므로 저장 수는 02-b로 본다. 두·세 번째 저장, `selected_photos`, `photo_pick`은 표본이 작을 때 비율로 판단하지 않는다.

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

### 07 · 지구본 체험 깊이

- `experience_end(experience_type = globe).active_duration_seconds`: 중앙값과 25·75 백분위
- `experience_end(experience_type = globe).unique_memories_opened`: 0개, 1개, 2개 이상
- `memory_open(experience_type = globe, open_index=1).time_since_start_seconds`: 중앙값

how_play의 `experience_end`는 `unique_memories_opened`가 항상 0이라 거르지 않으면 0개 구간이 부풀려진다. 평균 체류시간 하나만으로 체험 성공을 판단하지 않는다.

## 출시·운영 검증

- [ ] 메인 랜딩과 `/recap/`을 각각 새 탭에서 직접 열어 `$pageview` 수신 확인
- [ ] 실제 사진 흐름에서 `photo_select → processing_complete|analysis_empty` 중 정확히 한 경로 확인
- [ ] 실제 사진 이벤트의 `picker_source`가 `file_system_access|legacy_input` 중 하나이며 두 경로의 생성률 비교가 가능한지 확인
- [ ] 샘플과 실제 사진에 `journey_source=demo|photos`가 구분되는지 확인
- [ ] 공유 취소·다운로드 폴백·실패가 서로 다른 `result`로 보이는지 확인
- [ ] `surface`, `analytics_schema_version`, `traffic_type` 공통 속성 확인
- [ ] 파일명·좌표·촬영시각·이메일 등 개인정보 속성이 없는지 확인
- [ ] how_play 이벤트에 `experience_type=how_play`가 있고 사진 src·alt가 없는지 확인
- [ ] 02·07의 `experience_type = globe` 필터를 how_play 배포 전에 저장하고, 지구본 타일에 how_play가 나타나지 않는지 확인
- [ ] `?internal=1` 이벤트가 운영 타일에서 제외되는지 확인
- [ ] GA4 DebugView와 PostHog Live Events의 동일 동작 순서 비교

코드 반영, 프로젝트 환경변수 설정, Live Events 실수신, 대시보드 저장은 각각 별도 증거로 확인한다. 어느 하나를 다른 단계의 완료로 보고하지 않는다.
