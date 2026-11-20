#!/usr/bin/env bash
# apply-cloudwatch-agent.sh의 is_running이 가짜 ctl 출력을 맞게 판단하는지 확인한다.
# 실행: backend/scripts/tests/apply-cloudwatch-agent-test.sh
set -uo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT

mkdir -p "$work/agent/bin"
export CLOUDWATCH_AGENT_ROOT="$work/agent"
cat > "$work/agent/bin/amazon-cloudwatch-agent-ctl" <<'CTL'
#!/usr/bin/env bash
# FAKE_STATUS: status 값, FAKE_EXIT: 종료 코드, FAKE_TRAILING_BYTES: 뒤에 더 쓰는 출력 크기
printf '{\n  "status": "%s",\n  "starttime": "2026-11-20T00:00:00+00:00",\n  "configstatus": "configured",\n  "cwoc_status": "%s",\n  "version": "1.300000"\n}\n' \
  "${FAKE_STATUS:-running}" "${FAKE_CWOC_STATUS:-stopped}"
# 파이프 버퍼(64KB)보다 길게 이어 쓴다. 읽는 쪽이 먼저 끝나면 이 printf(내장 명령)가 SIGPIPE를 받아
# ctl이 141로 끝난다. 서버에서 본 증상을 매번 재현하려는 것이다.
if (( ${FAKE_TRAILING_BYTES:-0} > 0 )); then
  printf '%*s\n' "$FAKE_TRAILING_BYTES" ''
fi
exit "${FAKE_EXIT:-0}"
CTL
chmod +x "$work/agent/bin/amazon-cloudwatch-agent-ctl"

# shellcheck source=../apply-cloudwatch-agent.sh
source "$TEST_DIR/../apply-cloudwatch-agent.sh"

failures=0

expect() {
  local expected=$1 name=$2
  shift 2
  local actual=stopped assignment
  if (
    for assignment in "$@"; do
      export "${assignment?}"
    done
    is_running
  ); then
    actual=running
  fi
  if [[ $actual == "$expected" ]]; then
    echo "ok - $name"
  else
    echo "not ok - $name (expected $expected, got $actual)"
    failures=$((failures + 1))
  fi
}

expect running "실행 중이면 running으로 판단한다" FAKE_STATUS=running
expect running "출력이 길어 읽는 쪽이 먼저 끝나도 running으로 판단한다(SIGPIPE)" FAKE_STATUS=running FAKE_TRAILING_BYTES=200000
expect stopped "멈춰 있으면 stopped로 판단한다" FAKE_STATUS=stopped
expect stopped "다른 필드(cwoc_status)만 running이면 stopped로 판단한다" FAKE_STATUS=stopped FAKE_CWOC_STATUS=running
expect stopped "status 명령이 실패하면 출력과 관계없이 stopped로 판단한다" FAKE_STATUS=running FAKE_EXIT=1

if (( failures > 0 )); then
  echo "${failures}개 실패"
  exit 1
fi
echo "모두 통과"
