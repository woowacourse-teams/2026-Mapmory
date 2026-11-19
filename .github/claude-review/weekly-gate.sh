#!/usr/bin/env bash
# 주 1회 작업을 backend-develop push로 돌리기 위한 문지기.
# schedule은 기본 브랜치(main)에 워크플로가 있어야 돈다. main에 없는 동안에는 push마다 이 스크립트가 보고 돌릴지 정한다.
#   - 지난 7일 안에 그 job이 성공했으면 건너뛴다.
#   - 지난 24시간 안에 그 job을 시도했으면(실패·시간 초과 포함) 건너뛴다. 토큰 만료처럼 계속 실패할 때 push마다 다시 돌지 않게 한다.
#   - 조회가 실패하면 건너뛴다(닫힌 쪽으로 실패).
# 사용: weekly-gate.sh <저장소> <워크플로 파일 이름> <job 이름> <현재 run id>
# 출력: run=true|false (GITHUB_OUTPUT에 쓴다)
set -uo pipefail

repo="$1"; workflow="$2"; job="$3"; current="$4"
week=$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%SZ)
day=$(date -u -d '1 day ago' +%Y-%m-%dT%H:%M:%SZ)

skip() { echo "$1 이번에는 건너뛴다."; echo "run=false" >> "$GITHUB_OUTPUT"; exit 0; }

if ! ids=$(gh api "repos/$repo/actions/workflows/$workflow/runs?created=>=$week&per_page=100" \
    --jq ".workflow_runs[] | select(.id != $current) | .id"); then
  skip "실행 기록을 조회하지 못했다."
fi

for id in $ids; do
  if ! jobs=$(gh api "repos/$repo/actions/runs/$id/jobs?per_page=100" \
      --jq ".jobs[] | select(.name == \"$job\") | \"\(.conclusion) \(.started_at)\""); then
    skip "run $id의 job을 조회하지 못했다."
  fi
  while read -r conclusion started; do
    [ -z "$conclusion" ] && continue
    if [ "$conclusion" = "success" ]; then
      skip "지난 7일 안에 $job이 성공했다(run $id)."
    fi
    if [ "$conclusion" != "skipped" ] && [ "$conclusion" != "null" ] && [[ "$started" > "$day" ]]; then
      skip "지난 24시간 안에 $job을 시도했다(run $id, $conclusion)."
    fi
  done <<<"$jobs"
done

echo "지난 7일 안에 성공한 $job이 없고, 24시간 안의 시도도 없다. 이번에 돈다."
echo "run=true" >> "$GITHUB_OUTPUT"
