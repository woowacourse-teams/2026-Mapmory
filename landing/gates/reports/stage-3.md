# 3단계 구조 통과 보고서

측정 시각 2026-10-27T11:51:24.753Z · 대상 http://localhost:4173/ · 결과 **통과**

| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |
| --- | --- | --- | --- | --- | --- | --- |
| 3.1 | 페이지 스크롤을 조종하지 않는다 | 금지 호출 0 (scrollTo/scrollBy/scrollIntoView, wheel·touch 가로채기, scroll-snap) | 금지 호출 0, passive 스크롤 리스너 | 통과 | NN/g, Scrolljacking 101 |  |
| 3.2 | 가로 스크롤이 없다 | 320, 360, 390, 1440px에서 scrollWidth = 뷰포트 폭 | 320/360/390/1440 모두 scrollWidth = 뷰포트 | 통과 | WCAG 2.2 SC 1.4.10 Reflow; NN/g, Scrolljacking 101 |  |
| 3.3 | 어디에 멈춰도 제목이 화면 안에 온전히 있다 | 섹션 안 멈춤 지점 40곳 중 100% (양 끝 10% 제외) | 80/80 멈춤 지점에서 제목이 온전히 보임 (390·360px) | 통과 | NN/g, Scrolljacking 101; 티온 10-27: 휙휙 내려도 글이 읽혀야 하고, 한 화면에 한 가지 | `gates/reports/evidence/stage-3/fling-stops.json` |
| 3.4 | 보통 속도로 내리면 6장면이 모두 한 번씩 활성화된다 | 6/6 | 6/6 장면 활성화 (1,2,3,4,5,6) | 통과 | NN/g, Scrolljacking 101 |  |
| 3.5 | 장면에 멈췄을 때 폰, 라벨, 제목이 겹침 없이 한 화면에 보인다 | 360·390px 장면 6개 모두 | 390·360px 6장면 모두 폰·라벨·제목이 한 화면에, 겹침 없음 | 통과 | 8 things from Apple's product pages; 티온 10-27: 휙휙 내려도 글이 읽혀야 하고, 한 화면에 한 가지 (유도: 스크롤에 따라 기능을 하나씩 보여 주기를 '한 화면에 다 보임'으로 옮김) | `gates/reports/evidence/stage-3/scene-390-1.png` `gates/reports/evidence/stage-3/scene-390-2.png` `gates/reports/evidence/stage-3/scene-390-3.png` `gates/reports/evidence/stage-3/scene-390-4.png` `gates/reports/evidence/stage-3/scene-390-5.png` `gates/reports/evidence/stage-3/scene-390-6.png` `gates/reports/evidence/stage-3/scene-360-1.png` `gates/reports/evidence/stage-3/scene-360-2.png` `gates/reports/evidence/stage-3/scene-360-3.png` `gates/reports/evidence/stage-3/scene-360-4.png` `gates/reports/evidence/stage-3/scene-360-5.png` `gates/reports/evidence/stage-3/scene-360-6.png` |
