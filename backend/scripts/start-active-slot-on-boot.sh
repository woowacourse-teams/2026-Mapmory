#!/usr/bin/env bash
set -Eeuo pipefail

source "$(dirname "$0")/deployment-common.sh"

if [[ $(id -u) -ne 0 ]]; then
  echo "부팅 슬롯 선택은 root 권한으로 실행해야 합니다." >&2
  exit 1
fi

active_port=$(read_active_port)
active_slot=$(slot_for_port "$active_port")
inactive_slot=$([[ $active_slot == blue ]] && echo green || echo blue)
active_service=$(service_for_slot "$active_slot")
inactive_service=$(service_for_slot "$inactive_slot")

systemctl start "$active_service"
wait_for_health "$active_port" 180

# 과거에 개별 서비스가 enable된 상태가 남았더라도 잘못된 슬롯을 실행하지 않는다.
if systemctl is-active --quiet "$inactive_service"; then
  systemctl stop "$inactive_service"
fi

echo "부팅 슬롯 확인 완료: $active_slot ($active_port)"

# 슬롯은 BYPASS로 시작한다. 비활성 슬롯이 멈춰 활성 슬롯 하나만 서비스할 때 캐시를 켠다.
enable_deployment_cache_when_alone "$active_port" "$inactive_service"
