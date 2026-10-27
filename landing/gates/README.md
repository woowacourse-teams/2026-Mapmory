# gates: 기술 소개 섹션의 단계별 통과 판정

랜딩의 목적은 하나, 처음 온 사람이 **앱 받기**를 누르는 것이다 (1차 성과 = `download_click` 사용자 ÷ `page_view` 사용자,
`ANALYTICS_MEASUREMENT_PLAN.md`). 기술 소개 섹션은 그 사람이 버튼을 누르기 전에 품는 다섯 가지 궁금증에 답한다:
A 알아차림, B 나한테도, C 안전, D 보상, E 행동. 여기 있는 기준은 전부 그 다섯 가지 중 하나에 묶여 있고(`config.mjs`의
`purpose`), 수치는 임의로 정하지 않고 출처를 둔다(`SOURCES`). 현업의 제작 순서(브리프 → 문구 → 구조 → 시안 → 구현 →
검수 → 출시·측정)를 7단계로 두고, 앞 단계를 통과해야 다음 단계를 잴 수 있다.

팀 문서 "랜딩 기술 소개: 현업 제작 과정과 통과 기준"이 같은 표를 설명과 함께 담고 있다.

## 쓰는 법

```sh
npm run build && npm run preview          # http://localhost:4173/ 에 현재 빌드
npm run gate 1                            # 1단계 브리프 (파일 검사 + 판정자 점수)
npm run gate 3 -- --url http://localhost:4173/
npm run gate 4 -- --only 4.4              # 기준 하나만 (단계 통과로 기록되지는 않음)
npm run gate 5 -- --force                 # 앞 단계 미통과여도 실행 (보고서에 표시)
bash gates/baseline-lighthouse.sh         # 섹션 없는 페이지(merge-base 빌드)의 Lighthouse 기준선 (5.2·5.3이 인용)
```

결과는 `reports/stage-<n>.md`(표)와 `.json`, 증거(스크린샷·측정 JSON)는 `reports/evidence/stage-<n>/`(이 저장소의 다른
QA 캡처처럼 커밋하지 않고 로컬에 둔다), 단계별 통과 여부는 `state.json`. 실패하면 종료 코드 1.

브라우저 검사는 Playwright와 그 Chromium을 쓴다. 처음 한 번: `npm install` 뒤 `npx playwright install chromium`
(클라우드 세션은 이미 설치돼 있다). 5.2·5.3은 Lighthouse CLI를 같은 Chromium으로 돌린다.

## 페이지 단위 기준이 섹션 밖 원인으로 떨어질 때

5.2·5.3·5.4는 페이지 전체를 잰다(Lighthouse와 Core Web Vitals는 페이지 단위 지표다). 기준선 스크립트가 섹션 없는 빌드를
같은 방법으로 재서 보고서에 "기준선"으로 적고, 5.4는 섹션 자체 파일의 몫을 따로 적는다. 섹션이 기준선보다 나쁘게 만든 것은
이 작업에서 고치고, 기준선부터 떨어져 있던 것(예: 히어로 캔버스의 메인 스레드 점유, CSR 첫 그리기, 다음 섹션의 지구본
번들)은 기준을 고쳐 쓰지 않고 **실패로 남긴 채** 원인과 수치를 보고서와 팀 문서의 후속 목록에 적고, 다음 단계는 `--force`로
실행해 그 사실을 보고서 머리에 남긴다.

## 기준의 세 종류

| 종류 | 누가 재나 | 기록 |
| --- | --- | --- |
| auto | 실행기 (`checks/*.mjs`) | 측정값과 증거 파일 |
| judge | 맥락 없는 독자 5명이 5초 테스트 질문에 답한다 | `judgments/<이름>.json`에 원답과 채점 |
| manual | 사람이 실기기·대시보드에서 확인한다 | `manual/<번호>.json` |

**judge 절차.** 자료(`judgments/material/`)는 방문자가 보는 것만 담는다(문구, 스크린샷). 판정자는 이 프로젝트에 대해
아무것도 모르는 상태로 자료와 질문만 받고, 원답은 그대로 파일에 남긴다. 채점은 `brief.json`의 장면별 `rubric`에 맞춰
`correct`를 적고, 장면마다 정답률 80% 이상이어야 한다(5초 테스트: 80% 이상 기억 = 강한 디자인). 4.6(눈이 먼저 가는 것)은
정답 대신 판정자들이 같은 요소를 짚었는지로 본다.

**manual 형식.** `{ "pass": true, "by": "티온", "at": "2026-10-28", "device": "iPhone 15 Safari", "note": "6장면·mp4 확인" }`.
6.5는 `manual/6.5.json`에 `appShipped: true`를 적어야 공유 장면을 켠 채 통과한다.

## 파일

- `config.mjs` 7단계 34기준, 출처, 목적 연결. 기준을 바꾸면 팀 문서의 표도 같이 바꾼다.
- `run.mjs` 실행기. `baseline-lighthouse.sh` 5.2·5.3 기준선. `brief.json` 1단계 산출물. `variants.json` 4단계 3안과 선택 근거. `motion-exceptions.json` 4.4 예외(이유 필수).
- `lib/browser.mjs` 뷰포트·스크롤 도우미, `lib/interact.mjs` 장면별 터치 시나리오 (6.3, 5.6, 5.7이 같이 쓴다).
- `hooks/` Claude Code 훅. `settings.example.json`을 `.claude/settings.json`에 합치면 단계를 건너뛴 `landing/src/` 수정이 막힌다
  (1단계 전엔 전부, 2단계 전엔 문구 파일만 허용. `landing/prototypes/`는 늘 열려 있다).
