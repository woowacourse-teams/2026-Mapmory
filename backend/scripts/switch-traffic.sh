#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/deployment-common.sh"
require_root
require_deployment_id

previous_port=$(state_value previous-port)
target_port=$(state_value target-port)
backup="$MAPMORY_NGINX_BACKUP_ROOT/$DEPLOYMENT_ID"
install -d -o root -g root -m 700 "$MAPMORY_NGINX_BACKUP_ROOT"

config=$(find_nginx_config)
if [[ ! -e $backup ]]; then
  cp -p "$config" "$backup"
fi

rollback_on_error() {
  local status=$?
  trap - ERR
  echo "트래픽 전환 실패, 이전 포트로 복구합니다." >&2
  release=$(state_value release-path)
  if [[ -f $release/.mapmory-release ]]; then
    printf 'failed\n' > "$release/.mapmory-release"
  fi
  restore_previous_route "$previous_port" "$target_port" || true
  exit "$status"
}
trap rollback_on_error ERR

[[ $(read_active_port) == "$previous_port" ]] || {
  echo "배포 시작 때의 활성 포트가 아닙니다. 전환을 중단합니다." >&2
  false
}
# 두 슬롯이 함께 서비스하는 동안(전환 직후, 관찰, 롤백) 어느 쪽도 캐시를 쓰지 않게 한다.
# 기존 슬롯에는 지난 배포가 켠 NORMAL이 남아 있다. 하나라도 BYPASS를 확인하지 못하면 전환하지 않는다.
require_deployment_cache_bypass "$previous_port"
require_deployment_cache_bypass "$target_port"
write_nginx_port "$previous_port" "$target_port"
nginx -t
systemctl reload nginx
[[ $(read_active_port) == "$target_port" ]]
wait_for_health "$target_port" 30

printf 'switched\n' > "$MAPMORY_DEPLOYMENT_DIR/traffic-state"
trap - ERR
echo "트래픽 전환 완료: $previous_port -> $target_port"
