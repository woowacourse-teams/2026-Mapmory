# Design

## Source of truth

- Status: Active
- Last refreshed: 2026-08-11
- Primary product surfaces: Android/iOS 모바일 온보딩, 여행 기록, 기록 상세·작성, 대한민국 지도
- Decision order: Figma → this document → HTML design previews → Compose implementation
- Evidence reviewed:
  - [Mapmory UI v1](https://www.figma.com/design/o1sJdUU3Kr4Lal5qRyJpIR/Mapmory-UI-v1?node-id=8-8)
  - `client/docs/onboarding/onboarding.html`
  - `client/docs/onboarding/onboarding-first-screen.html`
  - `client/docs/design/core-screens-dark.html`
  - `client/docs/design/map-screen-dark.html`
  - `client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/triprecord/screen/TripRecordDesign.kt`

## Brand

- Personality: 차분하고 따뜻한 여행 기록 서비스. 사진보다 방문한 장소와 그곳의 이야기에 집중한다.
- Trust signals: 읽기 쉬운 정보 계층, 충분한 대비, 명확한 선택 상태, 과하지 않은 장식
- Avoid: 기기 목업, 과도한 초록색 면적, 불필요한 그림자·그라데이션, 지도 형태를 임의로 단순화하는 표현

## Product goals

- Goals:
  - 사용자가 여행 기록을 장소와 지도에 연결해 남긴다는 가치를 빠르게 이해한다.
  - 기록 목록, 상세, 작성, 지도에서 동일한 시각 언어를 경험한다.
  - 사진이 없어도 기록을 만들 수 있음을 자연스럽게 전달한다.
- Non-goals:
  - 온보딩에서 모든 기능을 설명하거나 권한을 요청하지 않는다.
  - 지도 화면이 실제 지도 SDK의 동작을 미리 약속하지 않는다.
- Success signals: 사용자가 기록 작성과 지도 탐색의 시작점을 쉽게 찾고, 선택된 지역·탭·주요 행동을 구분할 수 있다.

## Personas and jobs

- Primary personas: 여행에서 방문한 행정구역과 순간을 나중에 되돌아보고 싶은 모바일 사용자
- User jobs:
  - 방문한 지역을 지도에서 확인한다.
  - 지역에 연결된 여행 기록을 남기고 사진을 선택적으로 첨부한다.
  - 기존 기록을 목록과 상세 화면에서 다시 읽는다.
- Key contexts of use: 여행 중 짧게 기록할 때, 여행 뒤 사진과 메모를 정리할 때, 방문 지역을 되돌아볼 때

## Information architecture

- Primary navigation: 하단 탭 `지도 · 기록 · 작성 · 내 정보`
- Prototype navigation: 시연용 웹 프로토타입은 하단 탭 `지도 · 일지 · 통계`를 사용한다.
- Core routes/screens:
  - 온보딩 3종: 지도 가치 소개 → 지역과 기록 → 사진은 선택
  - 지도 최초 진입 코치 마크: 필터 → 방문 지역 → 기록 작성 → 하단 메뉴
  - 기록 목록
  - 기록 상세
  - 기록 작성·수정
  - 대한민국 지도
- Content hierarchy: 현재 위치 또는 화면 제목 → 핵심 정보·콘텐츠 → 보조 메타데이터와 필터 → 주요 행동

## Design principles

- 장소와 기록이 주인공이다. 사진은 기록을 보강하는 요소로 사용한다.
- 한 화면에는 하나의 주된 행동을 둔다. 작성은 `＋`, 저장은 하단 또는 상단의 단일 강조 행동으로 표현한다.
- 강조색은 상태와 행동을 안내할 때만 쓴다. 큰 면적을 초록색으로 채우지 않는다.
- 지도는 행정구역의 실제 경계와 섬의 비율을 보존한다. 단순 아이콘처럼 바꾸지 않는다.
- HTML 시안은 전체 화면 직사각형 캔버스로 제공한다. 휴대폰 외곽선은 넣지 않는다.

## Visual language

- Color:
  - Canvas: `#0B141A`
  - Surface: `#121D24`
  - Raised surface: `#1A262F`
  - Divider: `#29404A`
  - Accent: `#21E69A`
  - Primary text: `#F0FFF8`
  - Muted text: `#B9CBBC`
  - Error: `#FF6264`
  - Accent is reserved for primary actions, selected tabs and filters, visited-map areas, and small markers.
- Typography: Be Vietnam Pro 우선, Pretendard와 시스템 폰트를 대체로 사용한다. 제목은 굵고 짧게, 본문은 편안한 행간으로 쓴다.
- Spacing/layout rhythm: `4 / 8 / 12 / 16 / 20 / 24 / 32 px`
- Shape/radius/elevation: 카드 20 px 이하, 입력·버튼 14 px, 칩·필 9999 px. 화면 캔버스 외곽은 각진 형태. 그림자는 최소화한다.
- Motion: 현재 정적 시안만 정의한다. 실제 구현 시에는 전환과 선택 상태에 짧고 절제된 모션만 사용한다.
- Imagery/iconography: 온보딩은 실제 앱 화면 캡처를 우선 사용해 사용자가 도착할 화면을 이해하게 한다. 지도는 실제 경계 데이터를 기반으로 표현한다.

## Components

- Existing components to reuse:
  - 앱의 `TripRecordPalette`, `TripRecordTopBar`, `TripBottomBar`
  - 온보딩의 Skip link, Indicator, Primary button, 실제 앱 화면 미리보기
- New/changed components:
  - 여행 기록 카드
  - 위치·날짜 칩
  - 기록 작성 입력 필드와 미디어 선택 영역
  - 지도 필터, 방문 지역 표현, 지도 확대/축소 또는 범위 제어
- Variants and states:
  - 하단 탭과 필터: 기본/선택
  - 버튼: primary/secondary/disabled
  - 지도 지역: 미방문/방문/현재 선택
  - 기록 카드: 사진 있음/사진 없음
- Token/component ownership: 공통 색상·간격·타이포그래피는 공통 디자인 토큰으로 관리하고, 개별 화면은 토큰을 재정의하지 않는다.

## Accessibility

- Target standard: Android 기본 접근성 기준을 충족하고, 터치 대상은 최소 44 dp 수준을 목표로 한다.
- Keyboard/focus behavior: HTML 변환본은 링크와 버튼을 순서대로 포커스할 수 있어야 하며, Compose 구현은 의미 있는 클릭 라벨을 제공한다.
- Contrast/readability: 본문·배경 대비를 확보하고, 선택 상태를 색상만으로 구분하지 않는다.
- Screen-reader semantics: 지도, 탭, 필터, 기록 카드, 주요 행동에 역할과 상태를 전달한다.
- Reduced motion and sensory considerations: 모션은 별도 정의 전까지 사용하지 않는다.

## Responsive behavior

- Supported breakpoints/devices: 390×844 기준의 세로형 모바일 화면. Android와 iOS의 일반적인 세로형 휴대폰을 우선 지원한다.
- Layout adaptations: 화면은 기기 폭을 채우고, 기본 내부 여백은 20~24 px을 유지한다. HTML 시안은 전체 화면 직사각형으로 표시한다.
- Touch/hover differences: 주요 버튼·탭·칩은 충분한 터치 영역을 확보한다. hover는 필수 피드백으로 사용하지 않는다.

## Interaction states

- Loading: 지도와 기록 목록은 로딩 중임을 별도 상태로 보여 준다.
- Empty: 사진 없이 기록을 만들 수 있고, 기록·방문 지역이 없을 때 다음 행동을 안내한다.
- Error: 네트워크·저장 실패는 원인과 재시도 행동을 짧게 안내한다.
- Success: 기록 저장 뒤 목록 또는 상세 화면에서 결과를 확인할 수 있어야 한다.
- First entry: 지도 코치 마크는 최초 진입 시 한 번만 표시하고 닫기 행동을 제공한다.
- Disabled: 유효하지 않은 입력에서는 저장 행동을 비활성화하고 이유를 알린다.
- Offline/slow network: 네트워크 의존 화면은 연결 상태와 재시도 경로를 제공한다.

## Content voice

- Tone: 친근하고 안심시키는 존댓말
- Terminology: `여행 기록`, `지역`, `방문 지역`, `사진`, `지도`
- Microcopy rules: 한 화면에는 하나의 핵심 메시지를 둔다. 짧은 문장을 우선하고, 사진 첨부가 선택 사항임을 숨기지 않는다.

## Implementation constraints

- Framework/styling system: Compose Multiplatform 공통 UI. HTML/CSS 파일은 Figma 반입과 화면 시안 검토용 산출물이다.
- Design-token constraints: 새 화면과 시안은 이 문서의 색상 토큰을 사용한다. 현재 `TripRecordPalette`의 기존 초록색 `#19E5A2`는 `#21E69A`와 차이가 있어 동기화 검토가 필요하다.
- Performance constraints: 지도는 외부 지도 SDK에 의존하지 않는 방향을 유지한다. 대용량 이미지는 시안 외 실제 앱 UI에 무분별하게 넣지 않는다.
- Compatibility constraints: Android minSdk 28, iOS deployment target 16.0을 기준으로 한다.
- Test/screenshot expectations: 기록·상세·작성·지도 화면의 다크 모드 대비와 선택 상태를 프리뷰 또는 에뮬레이터에서 확인한다.

## Open questions

- [ ] Figma의 최종 화면과 HTML/Compose 구현 간의 차이를 어떤 주기로 동기화할지 결정
- [ ] `TripRecordPalette`의 `#19E5A2`를 디자인 기준 색상 `#21E69A`로 교체할지 결정
- [ ] 지도에서 방문·선택·미방문 지역을 어떻게 구분할지와 접근성 라벨 결정
- [ ] 지도 확대/축소, 지구본 전환, 필터의 실제 동작과 API 계약 결정
- [ ] 최종 폰트 파일 및 CMP 적용 방식 결정
- [ ] 소개형 온보딩과 지도 코치 마크의 완료 여부 저장 위치 및 다시 보기 제공 여부 결정
- [ ] 앱의 4개 탭과 웹 프로토타입의 3개 탭 중 최종 정보 구조 확정
