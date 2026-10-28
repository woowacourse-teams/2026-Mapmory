// Stage gates for the landing's tech section (기술 소개).
//
// Every threshold here comes from a published source, listed in SOURCES; the criteria table in the
// team doc "랜딩 기술 소개: 현업 제작 과정과 통과 기준" mirrors this file. A criterion is one of:
//   auto    the runner measures it (see checks/*.mjs)
//   judge   five context-free readers answer 5-second-test questions; the runner scores judgments/<name>.json
//   manual  a person checks it on a real device and records manual/<id>.json
// `purpose` ties each criterion to one of the five things a visitor needs before pressing 앱 받기:
//   A 알아차림 (what is this), B 나한테도 (works on my library), C 안전 (photos stay on the phone),
//   D 보상 (what the record becomes), E 행동 (nothing in the way of the store button).

export const SOURCES = Object.freeze({
  nngReading: { title: "NN/g, How Users Read on the Web", url: "https://www.nngroup.com/articles/how-users-read-on-the-web/" },
  nngScrolljack: { title: "NN/g, Scrolljacking 101", url: "https://www.nngroup.com/articles/scrolljacking-101/" },
  nngDuration: { title: "NN/g, Animation Duration", url: "https://www.nngroup.com/articles/animation-duration/" },
  material: { title: "Material Design, Duration & easing", url: "https://m1.material.io/motion/duration-easing.html" },
  emil: { title: "Emil Kowalski, design engineering skills", url: "https://github.com/emilkowalski/skills" },
  wcag143: { title: "WCAG 2.2 SC 1.4.3 Contrast (Minimum)", url: "https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html" },
  wcag1410: { title: "WCAG 2.2 SC 1.4.10 Reflow", url: "https://www.w3.org/WAI/WCAG22/Understanding/reflow.html" },
  wcag211: { title: "WCAG 2.2 SC 2.1.1 Keyboard", url: "https://www.w3.org/WAI/WCAG22/Understanding/keyboard.html" },
  wcag247: { title: "WCAG 2.2 SC 2.4.7 Focus Visible", url: "https://www.w3.org/WAI/WCAG22/Understanding/focus-visible.html" },
  wcag233: { title: "WCAG 2.2 SC 2.3.3 Animation from Interactions", url: "https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html" },
  wcag255: { title: "WCAG 2.2 SC 2.5.5 Target Size (Enhanced)", url: "https://www.w3.org/WAI/WCAG22/Understanding/target-size-enhanced.html" },
  webVitals: { title: "web.dev, Web Vitals", url: "https://web.dev/articles/vitals" },
  lighthouse: { title: "Lighthouse performance scoring", url: "https://developer.chrome.com/docs/lighthouse/performance/performance-scoring" },
  httpArchive: { title: "HTTP Archive Web Almanac 2025, Page Weight", url: "https://almanac.httparchive.org/en/2025/page-weight" },
  unbounce: { title: "Unbounce Conversion Benchmark Report (SaaS)", url: "https://unbounce.com/conversion-benchmark-report/saas-conversion-rate/" },
  deloitte: { title: "Deloitte·Google, Milliseconds Make Millions", url: "https://web.dev/case-studies/milliseconds-make-millions" },
  learnui: { title: "Learn UI Design, font size guidelines", url: "https://www.learnui.design/blog/mobile-desktop-website-font-size-guidelines.html" },
  fiveSecond: { title: "5-second test guide (80%+ recall = strong design)", url: "https://www.koji.so/docs/5-second-test-guide" },
  clique: { title: "Clique Studios, 7 stages of the web design process", url: "https://cliquestudios.com/university/resources/web-design-process" },
  ramotion: { title: "Ramotion, web design process", url: "https://www.ramotion.com/blog/web-design-process/" },
  apple: { title: "8 things from Apple's product pages", url: "https://uxplanet.org/8-things-i-learned-analyzing-apples-product-pages-9a5284681b37" },
  usercall: { title: "Usercall, why app landing pages don't convert", url: "https://www.usercall.co/post/why-app-landing-pages-dont-convert-7-common-problems" },
  plan: { title: "landing/ANALYTICS_MEASUREMENT_PLAN.md (1차 성과 = download_click 사용자 ÷ page_view 사용자)", url: "ANALYTICS_MEASUREMENT_PLAN.md" },
  agents: { title: "landing/AGENTS.md (문구는 앱 코드로 확인, 공유 장면 출시 규칙)", url: "AGENTS.md" },
  tion: { title: "티온 10-27: 휙휙 내려도 글이 읽혀야 하고, 한 화면에 한 가지", url: "" },
});

export const SCENE_COUNT = 6;
export const JUDGES = 5; // 6–10 readers give directional results (fiveSecond); five AI readers is the floor we can afford each round

const auto = (id, name, pass, check, purpose, source, extra = {}) => ({ id, name, pass, kind: "auto", check, purpose, source, ...extra });
const judge = (id, name, pass, judgment, purpose, source, extra = {}) => ({ id, name, pass, kind: "judge", check: "judge.score", judgment, threshold: 0.8, purpose, source, ...extra });
const manual = (id, name, pass, purpose, source) => ({ id, name, pass, kind: "manual", check: "manual.record", purpose, source });

export const STAGES = Object.freeze([
  { n: 1, key: "brief", name: "브리프", criteria: [
    auto("1.1", "장면마다 브리프가 채워져 있다 (사용자의 궁금증, 한 줄 메시지, 한 가지 조작, 사실 근거, 측정 이벤트)", "6장면 모두 5항목, 메시지와 조작은 장면당 1개", "brief.schema", "B", ["clique", "ramotion", "nngReading"]),
    judge("1.2", "브리프만 읽은 판정자가 앱이 무엇을 하는지와 각 장면에서 사용자가 무엇을 하는지를 맞힌다", "장면별 판정자 80% 이상, 6장면 모두", "brief", "A", ["fiveSecond"]),
  ] },
  { n: 2, key: "copy", name: "문구", criteria: [
    auto("2.1", "한 화면에 글 덩어리는 폰 밖 2개(라벨, 제목), 폰 안 안내 1줄 이하", "장면당 폰 밖 텍스트 블록 = 2, 폰 안 안내 ≤ 1", "copy.blocks", "A", ["nngReading", "apple", "tion"]),
    auto("2.2", "제목은 360px 폰에서 2줄 이하", "6제목 모두 ≤ 2줄", "copy.titleLines", "A", ["nngReading"], { derived: "단어 수 절반 규칙을 줄 수로 옮김" }),
    auto("2.3", "쉬운 말: 기술 용어 금지 목록에 없고, 한글은 KS X 1001 안", "금지어 0, 범위 밖 글자 0", "copy.plainWords", "A", ["unbounce", "nngReading"]),
    judge("2.4", "모든 주장이 앱 코드에서 사실이다 (출시 전 꺼 두는 장면은 그 앱 PR 기준)", "6장면 모두 근거 경로가 있고 확인됨", "truth", "C", ["agents"], { threshold: 1, minJudges: 1 }),
    judge("2.5", "문구만 본 판정자가 제품과 장면의 내용을 맞힌다", "장면별 80% 이상, 6장면 모두", "copy", "A", ["fiveSecond"]),
  ] },
  { n: 3, key: "structure", name: "구조", criteria: [
    auto("3.1", "페이지 스크롤을 조종하지 않는다", "금지 호출 0 (scrollTo/scrollBy/scrollIntoView, wheel·touch 가로채기, scroll-snap)", "structure.noScrolljack", "E", ["nngScrolljack"]),
    auto("3.2", "가로 스크롤이 없다", "320, 360, 390, 1440px에서 scrollWidth = 뷰포트 폭", "structure.noHorizontalScroll", "E", ["wcag1410", "nngScrolljack"]),
    auto("3.3", "어디에 멈춰도 제목이 화면 안에 온전히 있다", "섹션 안 멈춤 지점 40곳 중 100% (양 끝 10% 제외)", "structure.titleAlwaysReadable", "E", ["nngScrolljack", "tion"]),
    auto("3.4", "보통 속도로 내리면 6장면이 모두 한 번씩 활성화된다", "6/6", "structure.sceneReach", "E", ["nngScrolljack"]),
    auto("3.5", "장면에 멈췄을 때 폰, 라벨, 제목이 겹침 없이 한 화면에 보인다", "360·390px 장면 6개 모두", "structure.sceneLayout", "D", ["apple", "tion"], { derived: "스크롤에 따라 기능을 하나씩 보여 주기를 '한 화면에 다 보임'으로 옮김" }),
    auto("3.6", "휙 내려도 장면마다 변화가 보인다", "390px에서 2,000px/s로 내리는 동안 프레임마다 표본, 장면 6개 모두 서로 다른 상태 2개 이상", "structure.flickLegible", "A", ["tion", "nngScrolljack"], { derived: "티온 10-28: 글자를 안 보고 휙휙 넘기는 사람에게도 장면의 변화가 보여야 한다 → 변화를 스크롤 위치에 묶고, 빠른 스크롤에서도 상태가 바뀌는지 잰다" }),
  ] },
  { n: 4, key: "design", name: "시안", criteria: [
    auto("4.1", "글자 대비", "일반 글자 4.5:1 이상, 큰 글자 3:1 이상, 라이트·다크", "visual.contrast", "E", ["wcag143"]),
    auto("4.2", "누르는 곳 크기", "모두 44×44 CSS px 이상", "visual.targets", "E", ["wcag255"]),
    auto("4.3", "글자 크기", "폰 밖 16px 이상, 폰 안 13px 이상", "visual.fontSizes", "E", ["learnui"]),
    auto("4.4", "움직임 길이와 곡선", "요소 하나의 transition/animation 100~400ms, ease-in 단독·transition:all 없음 (스크롤에 묶인 연속 움직임 제외)", "visual.motion", "E", ["nngDuration", "material", "emil"]),
    auto("4.5", "동작 줄이기 설정을 켰을 때 위치·크기 움직임이 없다", "transform 전환 0, 스테이지 변환 고정", "visual.reducedMotion", "E", ["wcag233", "nngDuration"]),
    judge("4.6", "화면마다 눈이 먼저 가는 것이 하나다", "6장면 모두 판정자 80% 이상이 같은 요소를 짚는다", "focus", "D", ["tion"]),
    judge("4.7", "처음 보는 사람이 각 화면의 내용과 할 일을 맞힌다", "장면별 80% 이상, 6장면 모두", "visual", "A", ["fiveSecond"]),
    auto("4.8", "안 선택 규칙: 축이 다른 3안, 각 안이 4.1~4.5 통과, 4.7 점수가 가장 높은 안 (동점이면 움직임이 적은 안, 그래도 동점이면 4.6 시선 일치가 높은 안, 그래도 동점이면 승인 스케치에 가까운 안)", "variants.json에 3안과 점수, 선택 근거", "variants.pick", "D", ["clique", "emil"]),
  ] },
  { n: 5, key: "build", name: "구현", criteria: [
    auto("5.1", "테스트와 빌드", "npm test 전부 통과, npm run build 성공", "build.testsAndBuild", "E", ["ramotion"]),
    auto("5.2", "Lighthouse 모바일 점수", "성능 ≥ 90, 접근성 ≥ 90, 권장 사항 ≥ 90", "build.lighthouseScores", "E", ["lighthouse"]),
    auto("5.3", "Core Web Vitals (실험실)", "LCP ≤ 2.5s, CLS ≤ 0.1, TBT ≤ 200ms", "build.webVitals", "E", ["webVitals"]),
    auto("5.4", "페이지 무게", "섹션까지 내려 본 총 전송량 < 2,164KB, JS < 646KB", "build.pageWeight", "E", ["httpArchive", "deloitte"]),
    auto("5.5", "접근성 자동 검사", "axe serious·critical 위반 0, 라이트·다크", "build.axe", "E", ["wcag143"]),
    auto("5.6", "콘솔 오류와 깨진 자원", "오류 0, 섹션 자원 404 0", "build.consoleAnd404", "E", ["clique"]),
    auto("5.7", "분석 이벤트", "view 1, scene 1~6, interact 장면별 1", "build.analytics", "E", ["clique", "plan"]),
  ] },
  { n: 6, key: "qa", name: "검수", criteria: [
    auto("6.1", "기기·테마 매트릭스", "320/360/390/768/1440 × 라이트·다크 × 동작 줄이기: 겹침·잘림·가로 스크롤 0", "qa.matrix", "E", ["clique", "wcag1410"]),
    auto("6.2", "키보드", "조작 요소 전부 Tab 도달, Enter/Space 동작, 포커스 보임", "qa.keyboard", "E", ["wcag211", "wcag247"]),
    auto("6.3", "터치 시나리오", "6/6 (뒤집기, 모으기, 기록, 지도, 지구본 끌기 vs 세로 스크롤, 영상)", "qa.touch", "E", ["ramotion"]),
    manual("6.4", "실기기", "iPhone Safari와 Android Chrome에서 6장면과 mp4 재생 확인", "E", ["clique"]),
    auto("6.5", "출시 규칙", "공유 앱 기능(#436) 출시 전엔 TECH_SHARE.enabled=false로 내보냄", "qa.shareFlag", "E", ["agents"]),
  ] },
  { n: 7, key: "launch", name: "출시·측정", criteria: [
    manual("7.1", "지표가 보인다", "PostHog에 view → scene 1~6 도달과 장면별 시도율 타일", "E", ["clique", "plan"]),
    manual("7.2", "이전 섹션보다 나쁘지 않다", "마지막 장면 도달률 ≥ 기존 how_story 장면 5 도달률 (출시 후 2주)", "E", ["plan"]),
    manual("7.3", "수정 규칙", "주 1회 보고, 한 번에 장면 하나만 바꾼다", "E", ["clique"]),
  ] },
]);

export const stageByKeyOrNumber = (s) => STAGES.find((st) => String(st.n) === String(s) || st.key === s);
