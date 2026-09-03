# Mapmory PostHog 대시보드 설정

> 아래는 v2 출시 전 대시보드 설정 기록이다. 현재 기준은 [ANALYTICS_MEASUREMENT_PLAN.md](ANALYTICS_MEASUREMENT_PLAN.md)를 따른다. 운영 시 `surface=landing`, `analytics_schema_version=2`, `traffic_type=external`, `landing_version=v3`로 분리하고 최종 전환은 두 스토어의 `download_click`이다. 아래 출시 알림 폼을 현재 퍼널에 다시 넣지 않는다. 이번 코드 변경이 기존 PostHog 대시보드 설정까지 수정한 것은 아니다.

## 목적

GA4에서 여러 탐색 보고서를 반복해서 만들지 않고, 랜딩의 제품 체험 흐름을 한 화면에서 진단한다. GA4는 유입과 최종 전환 판단에 유지하고 PostHog은 체험 퍼널, 활성 시간, 기억 열람 깊이, 단계별 이탈의 운영 화면으로 사용한다.

## 현재 구성 상태

2026-08-28 기준 PostHog 프로젝트에 `Landing · Product Experience v2` 대시보드를 생성했다.

- 대시보드: `https://us.posthog.com/project/580627/dashboard/2039395`
- 생성된 첫 차트: `랜딩 체험 핵심 퍼널`
- 현재 단계: `$pageview → experience_view → experience_start → memory_open`
- 집계: 고유 사용자, 순차 퍼널, 전환 창 1일

현재 차트의 값에는 초기 연결 QA가 포함되어 있으므로 성과 판정에는 사용하지 않는다. 운영 분석에는 `traffic_type != internal` 공통 필터를 적용하고, 초기 QA 이벤트는 알려진 테스트 시간대·세션으로 별도 제외한다.

## 1. 프로젝트 연결

PostHog 프로젝트 설정 화면에서 Project API key와 Host를 확인하고 배포 환경에 다음 값을 넣는다.

```text
VITE_POSTHOG_KEY=<Project API key>
VITE_POSTHOG_HOST=<프로젝트 설정에 표시된 Host>
VITE_POSTHOG_CAPTURE_LOCAL=false
```

두 값 중 하나라도 없으면 PostHog은 초기화되지 않으며 GA4는 계속 정상 작동한다. 로컬 검증이 꼭 필요할 때만 별도의 테스트 프로젝트에서 `VITE_POSTHOG_CAPTURE_LOCAL=true`를 사용한다.

현재 구현은 다음 항목을 기본으로 비활성화한다.

- 자동 클릭·입력 수집
- 자동 페이지뷰·페이지 이탈 수집
- 세션 녹화
- 개인 프로필 생성
- 쿠키·로컬 스토리지 기반 영구 식별
- 설문과 기능 플래그 요청
- IP 기반 도시·위경도 보강

페이지 진입은 코드가 `$pageview`를 한 번 명시적으로 전송한다. 모든 이벤트는 GeoIP 보강을 거부한다. PostHog SDK가 브라우저·기기·페이지 같은 표준 진단 속성을 추가할 수 있지만, Mapmory의 커스텀 속성에는 출시 알림 이메일, 개인정보 동의값, 만 14세 확인값을 보내지 않는다. 프로젝트 설정의 `Discard client IP data`도 켜서 서버 측 IP 저장을 막는다.

팀 내부 검수 브라우저는 한 번 `https://map-mory.com/?internal=1`로 접속해 분석 전용 로컬 표시를 저장한다. 이후 모든 이벤트에 `traffic_type=internal`이 붙는다. `?internal=0`으로 해제할 수 있으며, 이 URL은 인증이나 보안 경계가 아니다.

## 2. 대시보드 만들기

대시보드 이름은 `Landing · Product Experience v2`로 한다. 기본 기간은 최근 14일, 운영 공통 필터는 `landing_version = v2`, `traffic_type != internal`로 시작한다.

### 타일 A · 전체 순차 퍼널

순서를 고정한 Funnel 인사이트를 만든다.

```text
$pageview
→ experience_view
→ experience_start
→ memory_open (open_index = 1)
→ waitlist_form_view
→ waitlist_submit_attempt
→ waitlist_submit
```

전환 창은 한 세션 안에서 끝나는 랜딩 흐름에 맞게 1일로 설정한다. CTA를 누르지 않고 체험 영역으로 바로 스크롤한 방문자도 포함하기 위해 `experience_cta_click`은 핵심 퍼널 단계에서 제외하고 별도 진단에 사용한다.

### 타일 B · 정확한 활성 체험시간

`experience_end`만 선택하고 숫자 속성 `active_duration_ms`의 중앙값을 본다. 가능하면 25·75 백분위도 함께 표시한다. 평균은 소수의 장시간 체험에 크게 흔들리므로 보조값으로만 사용한다.

### 타일 C · 기억 열람 깊이

`experience_end`의 `unique_memories_opened`를 다음 세 구간으로 나눈다.

- 0개
- 1개
- 2개 이상

`experience_type`으로 분리해 지구본과 대한민국 상세지도 중 어느 흐름에서 기억이 더 깊게 열리는지 확인한다.

### 타일 D · 첫 가치 도달시간

`memory_open` 중 `open_index = 1`만 선택하고 `time_since_start_ms` 중앙값을 본다. 시간이 길어지면서 기억 열기 비율이 함께 떨어지면 조작 안내나 전환 모션을 먼저 점검한다.

### 타일 E · 폼 마찰

`waitlist_submit_attempt → waitlist_submit` 퍼널과 `waitlist_submit_error` 추이를 함께 둔다. 오류는 `error_type`과 `validation_field`로 나누며 이메일이나 입력값은 분석 속성으로 추가하지 않는다.

## 3. 공통 분해 기준

모든 타일에서 필요한 경우에만 다음 기준으로 나눈다.

- `landing_version`
- `traffic_type` (`external`, `internal`)
- 모바일·데스크톱
- `experience_type`
- 유입 소스와 캠페인

표본이 100세션보다 적을 때는 백분율 변화에 성공·실패 판정을 붙이지 않는다. 초기에는 이벤트가 의도한 시점에 들어오는지와 비정상적인 0값·중복만 확인한다.

## 4. 출시 전 확인

- [ ] Project API key와 Host를 배포 환경에 설정
- [ ] Project Settings의 IP data capture를 `Discard client IP data`로 설정
- [ ] 개인정보·쿠키 안내에 실제 사용하는 분석 사업자와 처리 내용을 반영
- [ ] PostHog Live Events에서 `$pageview`, `experience_start`, `memory_open`, `experience_end` 수신 확인
- [ ] 이벤트 속성에 이메일·동의값·자유 입력이 없는지 확인
- [ ] 팀 검수 기기에서 `?internal=1`을 한 번 열고 `traffic_type=internal` 수신 확인
- [ ] 운영 대시보드에 `traffic_type != internal` 공통 필터 적용
- [ ] GA4 DebugView와 PostHog Live Events에서 동일 조작의 이벤트 순서 비교
- [ ] 위 다섯 타일을 만들고 팀 대시보드로 공유

세션 녹화가 필요해지면 별도 개인정보 검토와 명시적 결정 후 활성화한다. 현재 설정 변경만으로 자동 활성화하지 않는다.
