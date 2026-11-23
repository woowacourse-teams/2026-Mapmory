#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/deployment-common.sh"
require_root
require_deployment_id

install -d -o root -g root -m 700 "$MAPMORY_DEPLOYMENT_STATE" "$MAPMORY_DEPLOYMENT_DIR"

active_port=$(read_active_port)
active_slot=$(slot_for_port "$active_port")
active_service=$(service_for_slot "$active_slot")

restore_active_service_on_error() {
  local status=$?
  trap - ERR
  if ! systemctl is-active --quiet "$active_service"; then
    systemctl start "$active_service" || true
    wait_for_health "$active_port" 180 || true
  fi
  exit "$status"
}
trap restore_active_service_on_error ERR

printf '%s\n' "$active_port" > "$MAPMORY_DEPLOYMENT_DIR/previous-port"
printf '%s\n' "$active_slot" > "$MAPMORY_DEPLOYMENT_DIR/previous-slot"

previous_release="$MAPMORY_ROOT/mapmory-backend.jar"
ensure_slot_link "$active_slot" "$previous_release"
inactive_slot=$( [[ $active_slot == blue ]] && echo green || echo blue )
ensure_slot_link "$inactive_slot" "$previous_release"

# 첫 전환에서는 이전 revision의 ApplicationStop이 기존 프로세스를 내렸을 수 있다.
# 새 훅에서 오류가 나도 활성 포트를 다시 살려 다음 조치를 할 수 있도록 복구한다.
if ! systemctl is-active --quiet "$active_service"; then
  systemctl start "$active_service"
  wait_for_health "$active_port" 180
fi

trap - ERR
echo "활성 슬롯 확인 완료: $active_slot ($active_port), 배포 ID=$DEPLOYMENT_ID"

# 지표 수집 설정은 실패해도 배포를 막지 않는다. 종료 코드뿐 아니라 시간도 막아야 하므로
# 앞의 health 대기(최대 180초)와 합쳐도 훅 timeout(300초) 안에 끝나게 90초로 자른다.
timeout --kill-after=10 90 "$(dirname "$0")/apply-cloudwatch-agent.sh" || true
