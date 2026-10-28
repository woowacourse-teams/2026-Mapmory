# 4단계 시안 통과 보고서

측정 시각 2026-10-28T01:34:12.027Z · 대상 http://localhost:4173/ · 결과 **통과**

| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |
| --- | --- | --- | --- | --- | --- | --- |
| 4.1 | 글자 대비 | 일반 글자 4.5:1 이상, 큰 글자 3:1 이상, 라이트·다크 | 위반 0 (라이트·다크 × 6장면), 판정 불가 130곳 | 통과 | WCAG 2.2 SC 1.4.3 Contrast (Minimum) | `gates/reports/evidence/stage-4/contrast.json` |
| 4.2 | 누르는 곳 크기 | 모두 44×44 CSS px 이상 | 158개 중 44px 미만 0개, 최소 44px | 통과 | WCAG 2.2 SC 2.5.5 Target Size (Enhanced) |  |
| 4.3 | 글자 크기 | 폰 밖 16px 이상, 폰 안 13px 이상 | 최소 폰 밖 16px, 폰 안 13px | 통과 | Learn UI Design, font size guidelines |  |
| 4.4 | 움직임 길이와 곡선 | 요소 하나의 transition/animation 100~400ms, ease-in 단독·transition:all 없음 (스크롤에 묶인 연속 움직임 제외) | 전환 29개 중 범위 밖 0개 (예외 4개) | 통과 | NN/g, Animation Duration; Material Design, Duration & easing; Emil Kowalski, design engineering skills | `gates/reports/evidence/stage-4/motion.json` |
| 4.5 | 동작 줄이기 설정을 켰을 때 위치·크기 움직임이 없다 | transform 전환 0, 스테이지 변환 고정 | 위치·크기 전환 0개, 단계 전환 중 폰 변환값 1가지 | 통과 | WCAG 2.2 SC 2.3.3 Animation from Interactions; NN/g, Animation Duration |  |
| 4.6 | 화면마다 눈이 먼저 가는 것이 하나다 | 6장면 모두 판정자 80% 이상이 같은 요소를 짚는다 | 같은 요소 비율 장면별 100/100/100/100/100/100% (판정자 5명, 기준 80%) | 통과 | 티온 10-27: 휙휙 내려도 글이 읽혀야 하고, 한 화면에 한 가지 | `gates/judgments/focus.json` |
| 4.7 | 처음 보는 사람이 각 화면의 내용과 할 일을 맞힌다 | 장면별 80% 이상, 6장면 모두 | 정답 비율 장면별 100/100/100/100/100/100% (판정자 5명, 기준 80%) | 통과 | 5-second test guide (80%+ recall = strong design) | `gates/judgments/visual.json` |
| 4.8 | 안 선택 규칙: 축이 다른 3안, 각 안이 4.1~4.5 통과, 4.7 점수가 가장 높은 안 (동점이면 움직임이 적은 안, 그래도 동점이면 4.6 시선 일치가 높은 안, 그래도 동점이면 승인 스케치에 가까운 안) | variants.json에 3안과 점수, 선택 근거 | 3안, 선택 base (인식 100%) | 통과 | Clique Studios, 7 stages of the web design process; Emil Kowalski, design engineering skills | `gates/variants.json` |
