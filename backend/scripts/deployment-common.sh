#!/usr/bin/env bash
set -Eeuo pipefail

readonly MAPMORY_ROOT=/opt/mapmory
readonly MAPMORY_RELEASES="$MAPMORY_ROOT/releases"
readonly MAPMORY_SLOTS="$MAPMORY_ROOT/slots"
readonly MAPMORY_DEPLOYMENT_STATE=/var/lib/mapmory-deployments
readonly MAPMORY_NGINX_BACKUP_ROOT=/var/lib/mapmory-deployments/nginx-backups
readonly MAPMORY_NGINX_CONF_OVERRIDE=${MAPMORY_NGINX_CONF:-}

require_deployment_id() {
  if [[ ! ${DEPLOYMENT_ID:-} =~ ^d-[A-Za-z0-9]+$ ]]; then
    echo "DEPLOYMENT_ID가 없거나 예상한 CodeDeploy 형식이 아닙니다." >&2
    exit 1
  fi
  export MAPMORY_DEPLOYMENT_DIR="$MAPMORY_DEPLOYMENT_STATE/$DEPLOYMENT_ID"
}

require_root() {
  if [[ $(id -u) -ne 0 ]]; then
    echo "이 배포 단계는 root 권한으로 실행해야 합니다." >&2
    exit 1
  fi
}

state_value() {
  local key=$1
  local file="$MAPMORY_DEPLOYMENT_DIR/$key"
  [[ -f $file ]] || { echo "배포 상태 파일이 없습니다: $key" >&2; exit 1; }
  cat "$file"
}

slot_for_port() {
  case "$1" in
    8080) echo blue ;;
    8090) echo green ;;
    *) echo "지원하지 않는 앱 포트입니다: $1" >&2; return 1 ;;
  esac
}

port_for_slot() {
  case "$1" in
    blue) echo 8080 ;;
    green) echo 8090 ;;
    *) echo "지원하지 않는 슬롯입니다: $1" >&2; return 1 ;;
  esac
}

service_for_slot() {
  case "$1" in
    blue) echo mapmory ;;
    green) echo mapmory-green ;;
    *) echo "지원하지 않는 슬롯입니다: $1" >&2; return 1 ;;
  esac
}

management_port_for_app_port() {
  case "$1" in
    8080) echo 8081 ;;
    8090) echo 8091 ;;
    *) echo "지원하지 않는 앱 포트입니다: $1" >&2; return 1 ;;
  esac
}

find_nginx_config() {
  if [[ -n $MAPMORY_NGINX_CONF_OVERRIDE ]]; then
    [[ -f $MAPMORY_NGINX_CONF_OVERRIDE ]] || {
      echo "MAPMORY_NGINX_CONF 파일이 없습니다." >&2
      return 1
    }
    printf '%s\n' "$MAPMORY_NGINX_CONF_OVERRIDE"
    return
  fi

  python3 - <<'PY'
from pathlib import Path
import re
import sys

matches = []
for path in Path('/etc/nginx/conf.d').glob('*.conf'):
    text = path.read_text()
    if re.search(r'proxy_pass\s+http://127\.0\.0\.1:(8080|8090)\s*;', text):
        matches.append(str(path))

if len(matches) != 1:
    print(f'Nginx 설정에서 Mapmory 앱 대상 파일을 하나로 특정할 수 없습니다: {len(matches)}개', file=sys.stderr)
    sys.exit(1)
print(matches[0])
PY
}

read_active_port() {
  local config
  config=$(find_nginx_config)
  python3 - "$config" <<'PY'
from pathlib import Path
import re
import sys

text = Path(sys.argv[1]).read_text()
matches = re.findall(r'proxy_pass\s+http://127\.0\.0\.1:(8080|8090)\s*;', text)
if len(matches) != 1:
    print(f'Nginx 설정에 Mapmory proxy_pass가 정확히 하나 있어야 합니다. 현재 {len(matches)}개입니다.', file=sys.stderr)
    sys.exit(1)
print(matches[0])
PY
}

write_nginx_port() {
  local expected_port=$1
  local new_port=$2
  local config
  config=$(find_nginx_config)

  python3 - "$config" "$expected_port" "$new_port" <<'PY'
from pathlib import Path
import os
import re
import stat
import sys
import tempfile

path = Path(sys.argv[1])
expected = sys.argv[2]
new = sys.argv[3]
text = path.read_text()
pattern = re.compile(r'proxy_pass\s+http://127\.0\.0\.1:(8080|8090)\s*;')
matches = list(pattern.finditer(text))
if len(matches) != 1 or matches[0].group(1) != expected:
    print('Nginx 대상이 배포 시작 때 기록한 포트와 달라 변경을 중단합니다.', file=sys.stderr)
    sys.exit(1)

updated = pattern.sub(
    lambda m: re.sub(r':'+re.escape(expected)+r'(?=\s*;)', ':'+new, m.group(0)),
    text,
    count=1,
)
metadata = path.stat()
fd, temporary = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
try:
    with os.fdopen(fd, 'w') as output:
        output.write(updated)
    os.chmod(temporary, stat.S_IMODE(metadata.st_mode))
    os.chown(temporary, metadata.st_uid, metadata.st_gid)
    os.replace(temporary, path)
except Exception:
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    raise
PY
}

ensure_slot_link() {
  local slot=$1
  local source_jar=$2
  local slot_dir="$MAPMORY_SLOTS/$slot"
  local current="$slot_dir/current"

  install -d -o root -g root -m 755 "$MAPMORY_RELEASES" "$slot_dir"
  if [[ -e $current || -L $current ]]; then
    [[ -r "$current/mapmory-backend.jar" ]] || {
      echo "$slot 슬롯의 current JAR를 읽을 수 없습니다." >&2
      return 1
    }
    return
  fi

  [[ -r $source_jar ]] || {
    echo "초기 슬롯을 만들 원본 JAR가 없습니다: $source_jar" >&2
    return 1
  }

  local release="$MAPMORY_RELEASES/bootstrap-$slot-$(date -u +%Y%m%dT%H%M%SZ)-${DEPLOYMENT_ID}"
  install -d -o root -g root -m 755 "$release"
  install -o ubuntu -g ubuntu -m 644 "$source_jar" "$release/mapmory-backend.jar"
  printf 'bootstrap\n' > "$release/.mapmory-release"
  local temporary="$slot_dir/.current-$DEPLOYMENT_ID"
  ln -s "$release" "$temporary"
  mv -Tf "$temporary" "$current"
}

restore_previous_route() {
  local previous_port=$1
  local candidate_port=$2

  local previous_slot previous_service
  previous_slot=$(slot_for_port "$previous_port")
  previous_service=$(service_for_slot "$previous_slot")
  systemctl start "$previous_service" || return 1
  wait_for_health "$previous_port" 180 || return 1

  local current_port
  current_port=$(read_active_port) || return 1
  if [[ $current_port == "$candidate_port" ]]; then
    write_nginx_port "$candidate_port" "$previous_port" || return 1
    nginx -t || return 1
    systemctl reload nginx || return 1
  elif [[ $current_port != "$previous_port" ]]; then
    echo "요청하지 않은 Nginx 포트라 복구를 중단합니다: $current_port" >&2
    return 1
  fi

}

wait_for_health() {
  local port=$1
  local timeout_seconds=$2
  local management_port
  management_port=$(management_port_for_app_port "$port")
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    if curl --fail --silent --show-error --max-time 3 \
      "http://127.0.0.1:$management_port/actuator/health" >/dev/null 2>&1; then
      echo "앱 health 확인 완료: $port"
      return 0
    fi
    sleep 2
  done
  echo "앱 health 확인 실패: $port (${timeout_seconds}s)" >&2
  return 1
}
