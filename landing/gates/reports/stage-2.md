# 2단계 문구 통과 보고서

측정 시각 2026-10-27T11:48:12.537Z · 대상 http://localhost:4173/ · 결과 **통과**

| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |
| --- | --- | --- | --- | --- | --- | --- |
| 2.1 | 한 화면에 글 덩어리는 폰 밖 2개(라벨, 제목), 폰 안 안내 1줄 이하 | 장면당 폰 밖 텍스트 블록 = 2, 폰 안 안내 ≤ 1 | 폰 밖 2/2/2/2/2/2, 폰 안 안내 1/1/1/1/1/1 | 통과 | NN/g, How Users Read on the Web; 8 things from Apple's product pages; 티온 10-27: 휙휙 내려도 글이 읽혀야 하고, 한 화면에 한 가지 | `gates/reports/evidence/stage-2/copy-blocks.json` |
| 2.2 | 제목은 360px 폰에서 2줄 이하 | 6제목 모두 ≤ 2줄 | 줄 수 1/2/2/1/1/2 | 통과 | NN/g, How Users Read on the Web (유도: 단어 수 절반 규칙을 줄 수로 옮김) |  |
| 2.3 | 쉬운 말: 기술 용어 금지 목록에 없고, 한글은 KS X 1001 안 | 금지어 0, 범위 밖 글자 0 | 금지어 0, 범위 밖 글자 0 | 통과 | Unbounce Conversion Benchmark Report (SaaS); NN/g, How Users Read on the Web |  |
| 2.4 | 모든 주장이 앱 코드에서 사실이다 (출시 전 꺼 두는 장면은 그 앱 PR 기준) | 6장면 모두 근거 경로가 있고 확인됨 | 정답 비율 장면별 100/100/100/100/100/100%, 제품 100% (판정자 1명, 기준 100%) | 통과 | landing/AGENTS.md (문구는 앱 코드로 확인, 공유 장면 출시 규칙) | `gates/judgments/truth.json` |
| 2.5 | 문구만 본 판정자가 제품과 장면의 내용을 맞힌다 | 장면별 80% 이상, 6장면 모두 | 정답 비율 장면별 100/100/100/100/100/100%, 제품 100% (판정자 5명, 기준 80%) | 통과 | 5-second test guide (80%+ recall = strong design) | `gates/judgments/copy.json` |
