#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/deployment-common.sh"
require_root
require_deployment_id

previous_port=$(state_value previous-port)
previous_slot=$(slot_for_port "$previous_port")
target_slot=$( [[ $previous_slot == blue ]] && echo green || echo blue )
target_port=$(port_for_slot "$target_slot")
target_service=$(service_for_slot "$target_slot")
release_id=${DEPLOYMENT_ID//[^A-Za-z0-9-]/}
release="$MAPMORY_RELEASES/$release_id"
staged_jar="$MAPMORY_ROOT/deploy-staging/mapmory-backend.jar"

[[ -s $staged_jar ]] || { echo "스테이징 JAR가 없거나 비어 있습니다." >&2; exit 1; }
[[ $(read_active_port) == "$previous_port" ]] || {
  echo "배포 중 활성 Nginx 포트가 바뀌어 안전을 위해 중단합니다." >&2
  exit 1
}

systemctl daemon-reload
systemctl stop "$target_service" || true
rm -rf -- "$release"
install -d -o root -g root -m 755 "$release"
install -o ubuntu -g ubuntu -m 644 "$staged_jar" "$release/mapmory-backend.jar"
sha256sum "$release/mapmory-backend.jar" > "$release/sha256"
printf '%s\n' "$DEPLOYMENT_ID" > "$release/deployment-id"
printf 'pending\n' > "$release/.mapmory-release"

temporary="$MAPMORY_SLOTS/$target_slot/.current-$DEPLOYMENT_ID"
ln -s "$release" "$temporary"
mv -Tf "$temporary" "$MAPMORY_SLOTS/$target_slot/current"

printf '%s\n' "$target_slot" > "$MAPMORY_DEPLOYMENT_DIR/target-slot"
printf '%s\n' "$target_port" > "$MAPMORY_DEPLOYMENT_DIR/target-port"
printf '%s\n' "$release" > "$MAPMORY_DEPLOYMENT_DIR/release-path"

if ! systemctl start "$target_service"; then
  printf 'failed\n' > "$release/.mapmory-release"
  restore_previous_route "$previous_port" "$target_port" || true
  exit 1
fi
if ! wait_for_health "$target_port" 180; then
  systemctl stop "$target_service" || true
  printf 'failed\n' > "$release/.mapmory-release"
  restore_previous_route "$previous_port" "$target_port" || true
  exit 1
fi

echo "비활성 슬롯 준비 완료: $target_slot ($target_port), release=$release"
