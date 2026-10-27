# 5단계 구현 통과 보고서

측정 시각 2026-10-27T12:45:54.524Z · 대상 http://localhost:4173/ · 결과 **실패**

| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |
| --- | --- | --- | --- | --- | --- | --- |
| 5.1 | 테스트와 빌드 | npm test 전부 통과, npm run build 성공 | 테스트 통과 134, 실패 0; npm run build 성공 | 통과 | Ramotion, web design process | `gates/reports/evidence/stage-5/build.log` |
| 5.2 | Lighthouse 모바일 점수 | 성능 ≥ 90, 접근성 ≥ 90, 권장 사항 ≥ 90 | 성능 53, 접근성 95, 권장 사항 96 | 실패 | Lighthouse performance scoring | `gates/reports/evidence/stage-5/lighthouse.json` `gates/reports/evidence/stage-5/baseline-lighthouse.json` |
| 5.3 | Core Web Vitals (실험실) | LCP ≤ 2.5s, CLS ≤ 0.1, TBT ≤ 200ms | LCP 3.53s, CLS 0.016, TBT 5853ms | 실패 | web.dev, Web Vitals | `gates/reports/evidence/stage-5/lighthouse.json` `gates/reports/evidence/stage-5/baseline-lighthouse.json` |
| 5.4 | 페이지 무게 | 섹션까지 내려 본 총 전송량 < 2,164KB, JS < 646KB | 총 1,918KB (JS 735KB, 이미지 279KB, 영상 285KB, 글꼴 594KB) | 실패 | HTTP Archive Web Almanac 2025, Page Weight; Deloitte·Google, Milliseconds Make Millions | `gates/reports/evidence/stage-5/page-weight.json` |
| 5.5 | 접근성 자동 검사 | axe serious·critical 위반 0, 라이트·다크 | serious·critical 0 (가벼운 지적 0곳) | 통과 | WCAG 2.2 SC 1.4.3 Contrast (Minimum) | `gates/reports/evidence/stage-5/axe.json` |
| 5.6 | 콘솔 오류와 깨진 자원 | 오류 0, 섹션 자원 404 0 | 콘솔 오류 0, 같은 출처 4xx/5xx 0 | 통과 | Clique Studios, 7 stages of the web design process | `gates/reports/evidence/stage-5/console.json` |
| 5.7 | 분석 이벤트 | view 1, scene 1~6, interact 장면별 1 | view 1, scene 1,2,3,4,5,6, interact 1:flip 2:gather 3:save 4:map_toggle 5:globe_drag 6:year_pick | 통과 | Clique Studios, 7 stages of the web design process; landing/ANALYTICS_MEASUREMENT_PLAN.md (1차 성과 = download_click 사용자 ÷ page_view 사용자) | `gates/reports/evidence/stage-5/analytics-events.json` |

## 실패 상세

- 5.2: first-contentful-paint 36; largest-contentful-paint 63; speed-index 89; total-blocking-time 0; max-potential-fid 61; errors-in-console 0; interactive 9; mainthread-work-breakdown 0; color-contrast 0; unused-css-rules 50 — 기준선(섹션 없는 main dc03fbc (이 브랜치의 merge-base) 빌드): 성능 54, 접근성 95, 권장 사항 96
- 5.3: LCP 초과, TBT 초과 — LCP 요소 "제주는 31장" — 기준선(섹션 없는 main dc03fbc (이 브랜치의 merge-base) 빌드): LCP 3.47s, CLS 0.016, TBT 8755ms
- 5.4: 378KB react-globe.gl-CC-MqKzC.js; 285KB share-classic.webm; 271KB tiles.webp; 201KB line-seed-kr-regular-core.woff2; 191KB line-seed-kr-bold-core.woff2 — 이 섹션의 몫 366KB (JS 73KB)
