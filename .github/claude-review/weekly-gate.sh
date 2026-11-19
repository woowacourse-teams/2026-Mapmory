#!/usr/bin/env bash
# 주 1회 작업을 backend-develop push로 돌리기 위한 문지기.
# schedule은 기본 브랜치(main)에 워크플로가 있어야 돈다. main에 없는 동안에는 push마다 이 스크립트가
# "지난 7일 안에 그 job이 성공한 적이 있나"를 보고, 없을 때만 돌게 한다.
# 사용: weekly-gate.sh <저장소> <워크플로 파일 이름> <job 이름> <현재 run id>
# 출력: run=true|false (GITHUB_OUTPUT에 쓴다)
set -euo pipefail

repo="$1"; workflow="$2"; job="$3"; current="$4"
since=$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%SZ)

ids=$(gh api "repos/$repo/actions/workflows/$workflow/runs?created=>=$since&per_page=100" \
  --jq ".workflow_runs[] | select(.id != $current) | .id" || true)

for id in $ids; do
  done_ok=$(gh api "repos/$repo/actions/runs/$id/jobs?per_page=100" \
    --jq "[.jobs[] | select(.name == \"$job\" and .conclusion == \"success\")] | length" || echo 0)
  if [ "${done_ok:-0}" -gt 0 ]; then
    echo "지난 7일 안에 $job이 성공했다(run $id). 이번에는 건너뛴다."
    echo "run=false" >> "$GITHUB_OUTPUT"
    exit 0
  fi
done

echo "지난 7일 안에 성공한 $job이 없다. 이번에 돈다."
echo "run=true" >> "$GITHUB_OUTPUT"
