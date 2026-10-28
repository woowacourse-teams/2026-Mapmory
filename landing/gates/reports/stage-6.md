# 6단계 검수 통과 보고서

측정 시각 2026-10-28T01:27:35.760Z · 대상 http://localhost:4173/ · 결과 **실패** · 앞 단계 미통과 상태에서 강제 실행

| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |
| --- | --- | --- | --- | --- | --- | --- |
| 6.1 | 기기·테마 매트릭스 | 320/360/390/768/1440 × 라이트·다크 × 동작 줄이기: 겹침·잘림·가로 스크롤 0 | 120칸 중 문제 0칸 (5폭 × 2테마 × 동작 줄이기 2 × 6장면) | 통과 | Clique Studios, 7 stages of the web design process; WCAG 2.2 SC 1.4.10 Reflow | `gates/reports/evidence/stage-6/matrix.json` `gates/reports/evidence/stage-6/matrix-320-light.png` `gates/reports/evidence/stage-6/matrix-320-light-reduce.png` `gates/reports/evidence/stage-6/matrix-320-dark.png` `gates/reports/evidence/stage-6/matrix-320-dark-reduce.png` `gates/reports/evidence/stage-6/matrix-360-light.png` `gates/reports/evidence/stage-6/matrix-360-light-reduce.png` `gates/reports/evidence/stage-6/matrix-360-dark.png` `gates/reports/evidence/stage-6/matrix-360-dark-reduce.png` `gates/reports/evidence/stage-6/matrix-390-light.png` `gates/reports/evidence/stage-6/matrix-390-light-reduce.png` `gates/reports/evidence/stage-6/matrix-390-dark.png` `gates/reports/evidence/stage-6/matrix-390-dark-reduce.png` `gates/reports/evidence/stage-6/matrix-768-light.png` `gates/reports/evidence/stage-6/matrix-768-light-reduce.png` `gates/reports/evidence/stage-6/matrix-768-dark.png` `gates/reports/evidence/stage-6/matrix-768-dark-reduce.png` `gates/reports/evidence/stage-6/matrix-1440-light.png` `gates/reports/evidence/stage-6/matrix-1440-light-reduce.png` `gates/reports/evidence/stage-6/matrix-1440-dark.png` `gates/reports/evidence/stage-6/matrix-1440-dark-reduce.png` |
| 6.2 | 키보드 | 조작 요소 전부 Tab 도달, Enter/Space 동작, 포커스 보임 | 조작 요소 60개 중 Tab 도달 60, 포커스 표시 60, 키로 동작 60 | 통과 | WCAG 2.2 SC 2.1.1 Keyboard; WCAG 2.2 SC 2.4.7 Focus Visible | `gates/reports/evidence/stage-6/keyboard.json` |
| 6.3 | 터치 시나리오 | 6/6 (뒤집기, 모으기, 기록, 지도, 지구본 끌기 vs 세로 스크롤, 영상) | 6/6 (뒤집기 O, 모으기 O, 기록 O, 지도 O, 지구본 O, 영상 O) | 통과 | Ramotion, web design process | `gates/reports/evidence/stage-6/touch.json` |
| 6.4 | 실기기 | iPhone Safari와 Android Chrome에서 6장면과 mp4 재생 확인 | 기록 없음 | 실패 | Clique Studios, 7 stages of the web design process |  |
| 6.5 | 출시 규칙 | 공유 앱 기능(#436) 출시 전엔 TECH_SHARE.enabled=false로 내보냄 | enabled=true인데 앱 공유 기능(#436) 출시 기록 없음 | 실패 | landing/AGENTS.md (문구는 앱 코드로 확인, 공유 장면 출시 규칙) |  |

## 실패 상세

- 6.4: gates/manual/6.4.json 에 사람이 확인한 결과를 적어야 함
- 6.5: 출시 빌드 전에 src/techStory.js TECH_SHARE.enabled를 false로 두거나, 앱이 스토어에 나간 뒤 gates/manual/6.5.json에 appShipped: true를 기록
