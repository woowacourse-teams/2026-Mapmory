#!/usr/bin/env bash
# 배포·부팅 뒤 캐시를 NORMAL로 켜는 enable_deployment_cache_when_alone과 부팅 스크립트를 가짜 curl·systemctl로 확인한다.
# 실행: backend/scripts/tests/deployment-cache-normal-test.sh
set -uo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SCRIPTS_DIR=$(cd "$TEST_DIR/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/bin"

# 가짜 systemctl. FAKE_STATE_DIR/active-<서비스> 파일이 있으면 active다. 호출은 FAKE_STATE_DIR/calls에 남긴다.
# FAKE_STOP_FAILS=1이면 stop이 실패하고 서비스가 남는다.
cat > "$work/bin/systemctl" <<'FAKE'
#!/usr/bin/env bash
printf 'systemctl %s\n' "$*" >> "$FAKE_STATE_DIR/calls"
case "$1" in
  is-active) [[ -f "$FAKE_STATE_DIR/active-${*: -1}" ]] ;;
  start) touch "$FAKE_STATE_DIR/active-$2" ;;
  stop)
    if [[ ${FAKE_STOP_FAILS:-0} == 1 ]]; then exit 1; fi
    rm -f "$FAKE_STATE_DIR/active-$2" ;;
  *) exit 0 ;;
esac
FAKE

# 가짜 curl. -d가 있으면 모드 변경(POST), 없으면 조회(GET)다.
# FAKE_POST_FAILS=1: 변경 요청 실패, FAKE_GET_MODE: 조회 응답의 모드(기본은 마지막으로 바꾼 모드), FAKE_GET_FAILS=1: 조회 실패.
cat > "$work/bin/curl" <<'FAKE'
#!/usr/bin/env bash
url=${*: -1}
if [[ $url == */actuator/health ]]; then
  printf 'curl health %s\n' "$url" >> "$FAKE_STATE_DIR/calls"
  exit 0
fi
body=""
while (($# > 0)); do
  if [[ $1 == -d ]]; then body=$2; fi
  shift
done
if [[ -n $body ]]; then
  printf 'curl POST %s %s\n' "$url" "$body" >> "$FAKE_STATE_DIR/calls"
  if [[ ${FAKE_POST_FAILS:-0} == 1 ]]; then echo "curl: (22) 500" >&2; exit 22; fi
  printf 'NORMAL' > "$FAKE_STATE_DIR/mode"
  printf '{"mode":"NORMAL","generation":1,"changedAt":"2026-11-22T00:00:00Z"}'
else
  printf 'curl GET %s\n' "$url" >> "$FAKE_STATE_DIR/calls"
  if [[ ${FAKE_GET_FAILS:-0} == 1 ]]; then echo "curl: (7) refused" >&2; exit 7; fi
  mode=${FAKE_GET_MODE:-$(cat "$FAKE_STATE_DIR/mode" 2>/dev/null || echo BYPASS)}
  printf '{"mode":"%s","generation":1,"changedAt":"2026-11-22T00:00:00Z"}' "$mode"
fi
FAKE

# 부팅 스크립트는 root 확인을 한다. 가짜 id로 root처럼 보이게 한다.
cat > "$work/bin/id" <<'FAKE'
#!/usr/bin/env bash
echo 0
FAKE
chmod +x "$work/bin/"*
# 이 테스트의 모든 명령이 가짜 curl·systemctl·id를 쓰게 한다.
PATH="$work/bin:$PATH"

failures=0
pass() { echo "ok - $1"; }
fail() { echo "not ok - $1"; failures=$((failures + 1)); }

reset_state() {
  rm -rf "$work/state"
  mkdir -p "$work/state"
  export FAKE_STATE_DIR="$work/state"
  unset FAKE_POST_FAILS FAKE_GET_MODE FAKE_GET_FAILS FAKE_STOP_FAILS
}

# 함수 하나를 가짜 명령으로 실행한다. 표준 출력과 오류는 state/out, state/err에 남는다.
run_enable() {
  (
    # shellcheck source=../deployment-common.sh
    source "$SCRIPTS_DIR/deployment-common.sh"
    enable_deployment_cache_when_alone "$@"
  ) > "$FAKE_STATE_DIR/out" 2> "$FAKE_STATE_DIR/err"
}

calls() { cat "$FAKE_STATE_DIR/calls" 2>/dev/null; }

# 1. 이전 서비스가 멈춰 있으면 NORMAL을 요청하고, 다시 읽어 확인한다.
reset_state
touch "$FAKE_STATE_DIR/active-mapmory-green"
if run_enable 8090 mapmory \
  && calls | grep -qF 'curl POST http://127.0.0.1:8091/actuator/deploymentcache {"mode":"NORMAL"}' \
  && calls | grep -qF 'curl GET http://127.0.0.1:8091/actuator/deploymentcache' \
  && grep -qF '캐시 모드 NORMAL 확인: 8090' "$FAKE_STATE_DIR/out"; then
  pass "이전 서비스가 inactive면 새 슬롯 관리 포트에 NORMAL을 요청하고 확인한다"
else
  fail "이전 서비스가 inactive면 새 슬롯 관리 포트에 NORMAL을 요청하고 확인한다"
fi

# 2. 이전 서비스가 아직 active면 요청하지 않는다.
reset_state
touch "$FAKE_STATE_DIR/active-mapmory" "$FAKE_STATE_DIR/active-mapmory-green"
if run_enable 8090 mapmory \
  && ! calls | grep -q 'curl' \
  && grep -qF '경고: mapmory가 아직 실행 중이라 캐시를 BYPASS로 둡니다' "$FAKE_STATE_DIR/err"; then
  pass "이전 서비스가 active면 NORMAL을 요청하지 않고 경고만 남긴다"
else
  fail "이전 서비스가 active면 NORMAL을 요청하지 않고 경고만 남긴다"
fi

# 3. 요청이 실패해도 0으로 끝나고 수동 명령을 알려 준다.
reset_state
export FAKE_POST_FAILS=1
if run_enable 8080 mapmory-green \
  && grep -qF '경고: 캐시 NORMAL 전환 요청이 실패해 BYPASS로 둡니다' "$FAKE_STATE_DIR/err" \
  && grep -qF 'http://127.0.0.1:8081/actuator/deploymentcache' "$FAKE_STATE_DIR/err"; then
  pass "NORMAL 요청이 실패해도 exit 0이고 경고와 수동 명령을 남긴다"
else
  fail "NORMAL 요청이 실패해도 exit 0이고 경고와 수동 명령을 남긴다"
fi

# 4. HTTP는 성공했지만 다시 읽은 모드가 NORMAL이 아니면 경고한다.
reset_state
export FAKE_GET_MODE=BYPASS
if run_enable 8080 mapmory-green \
  && grep -qF '경고: NORMAL 전환을 요청했지만 모드가 바뀌지 않았습니다' "$FAKE_STATE_DIR/err" \
  && ! grep -qF '캐시 모드 NORMAL 확인' "$FAKE_STATE_DIR/out"; then
  pass "요청이 성공해도 다시 읽은 모드가 BYPASS면 성공으로 치지 않는다"
else
  fail "요청이 성공해도 다시 읽은 모드가 BYPASS면 성공으로 치지 않는다"
fi

# 5. 다시 읽기가 실패해도 0으로 끝난다.
reset_state
export FAKE_GET_FAILS=1
if run_enable 8080 mapmory-green && grep -qF '경고: 캐시 모드를 다시 읽지 못했습니다' "$FAKE_STATE_DIR/err"; then
  pass "모드 조회가 실패해도 exit 0이고 경고를 남긴다"
else
  fail "모드 조회가 실패해도 exit 0이고 경고를 남긴다"
fi

# 6~7. 부팅 스크립트 전체를 실행한다. Nginx가 가리키는 슬롯만 띄운 뒤 캐시를 켠다.
nginx_conf="$work/mapmory.conf"
printf 'server {\n  location / {\n    proxy_pass http://127.0.0.1:8090;\n  }\n}\n' > "$nginx_conf"
run_boot() {
  MAPMORY_NGINX_CONF="$nginx_conf" bash "$SCRIPTS_DIR/start-active-slot-on-boot.sh" > "$FAKE_STATE_DIR/out" 2> "$FAKE_STATE_DIR/err"
}

reset_state
touch "$FAKE_STATE_DIR/active-mapmory"   # 예전에 enable된 비활성 슬롯이 같이 떴다.
if run_boot \
  && calls | grep -qF 'systemctl start mapmory-green' \
  && calls | grep -qF 'systemctl stop mapmory' \
  && calls | grep -qF 'curl POST http://127.0.0.1:8091/actuator/deploymentcache' \
  && [[ $(calls | grep -n 'systemctl stop mapmory$' | cut -d: -f1) -lt $(calls | grep -n 'curl POST' | cut -d: -f1) ]]; then
  pass "부팅하면 활성 슬롯만 남긴 뒤 그 슬롯의 캐시를 켠다"
else
  calls
  fail "부팅하면 활성 슬롯만 남긴 뒤 그 슬롯의 캐시를 켠다"
fi

reset_state
touch "$FAKE_STATE_DIR/active-mapmory"
export FAKE_STOP_FAILS=1
run_boot
boot_status=$?
if ((boot_status != 0)) && ! calls | grep -q 'curl POST'; then
  pass "부팅 때 비활성 슬롯을 멈추지 못하면 캐시를 켜지 않는다"
else
  fail "부팅 때 비활성 슬롯을 멈추지 못하면 캐시를 켜지 않는다"
fi

if ((failures > 0)); then
  echo "${failures}개 실패"
  exit 1
fi
echo "모두 통과"
