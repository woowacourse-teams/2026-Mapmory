#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/deployment-common.sh"
require_root
require_deployment_id

previous_port=$(state_value previous-port)
target_port=$(state_value target-port)
previous_slot=$(slot_for_port "$previous_port")
previous_service=$(service_for_slot "$previous_slot")
release=$(state_value release-path)

rollback_on_error() {
  local status=$?
  trap - ERR
  echo "배포 검증 실패, 이전 트래픽 대상으로 복구합니다." >&2
  if [[ -n ${release:-} && -f $release/.mapmory-release ]]; then
    printf 'failed\n' > "$release/.mapmory-release"
  fi
  restore_previous_route "$previous_port" "$target_port" || true
  exit "$status"
}
trap rollback_on_error ERR

[[ $(read_active_port) == "$target_port" ]]
wait_for_health "$target_port" 30
curl --fail --silent --show-error --max-time 5 \
  "http://127.0.0.1:$target_port/actuator/health" >/dev/null

# 전환 후 5분 동안 새 슬롯을 관찰한다. 2초 간격 상태 확인이 5회 연속 실패하거나
# Nginx가 다른 포트를 가리키면 이전 슬롯으로 복구한다.
soak_seconds=300
probe_interval_seconds=2
max_consecutive_failures=5
failure_count=0
soak_started_at=$(date --iso-8601=seconds)
printf '%s\n' "$soak_started_at" > "$MAPMORY_DEPLOYMENT_DIR/soak-started-at"
soak_deadline=$((SECONDS + soak_seconds))
while (( SECONDS < soak_deadline )); do
  if [[ -f "$MAPMORY_DEPLOYMENT_DIR/request-rollback" ]]; then
    echo "운영자 롤백 요청 파일을 확인했습니다." >&2
    false
  fi

  if [[ $(read_active_port) != "$target_port" ]]; then
    echo "관찰 중 Nginx 대상 포트가 변경됐습니다." >&2
    false
  fi

  if curl --fail --silent --show-error --max-time 3 \
    "http://127.0.0.1:$target_port/actuator/health" >/dev/null 2>&1; then
    failure_count=0
  else
    failure_count=$((failure_count + 1))
    echo "health probe 실패: $failure_count/$max_consecutive_failures"
    if (( failure_count >= max_consecutive_failures )); then
      echo "health probe가 연속 실패해 이전 슬롯으로 복구합니다." >&2
      false
    fi
  fi

  sleep "$probe_interval_seconds"
done

printf '%s\n' "$(date --iso-8601=seconds)" > "$MAPMORY_DEPLOYMENT_DIR/soak-completed-at"

# 관찰이 끝나면 이전 프로세스는 종료하고 JAR와 current 링크는 롤백용으로 보존한다.
if systemctl is-active --quiet "$previous_service"; then
  if ! systemctl stop "$previous_service"; then
    echo "이전 프로세스를 정상 종료하지 못했습니다. 트래픽은 새 슬롯에 유지합니다." >&2
  fi
fi

[[ -f "$release/mapmory-backend.jar" && -f "$release/sha256" ]]
(cd "$release" && sha256sum --check --status sha256)
printf 'success\n' > "$release/.mapmory-release"
printf 'success\n' > "$MAPMORY_DEPLOYMENT_DIR/status"

# 성공한 릴리스 중 최근 3개를 보존한다. 두 슬롯의 current가 참조하는 JAR는
# 개수와 관계없이 보호한다. 실패한 후보는 진단을 위해 7일 보존한다.
python3 - "$MAPMORY_RELEASES" "$MAPMORY_SLOTS" <<'PY'
from pathlib import Path
import shutil
import sys
import time

releases = Path(sys.argv[1])
slots = Path(sys.argv[2])
protected = set()
for link in slots.glob("*/current"):
    try:
        protected.add(link.resolve(strict=True))
    except FileNotFoundError:
        pass

successful = []
failed = []
for release in releases.iterdir():
    if release.is_dir() and (release / ".mapmory-release").exists():
        status = (release / ".mapmory-release").read_text().strip()
        if status == "success":
            successful.append(release)
        elif status in {"pending", "failed"} and release.stat().st_mtime < time.time() - 7 * 24 * 60 * 60:
            failed.append(release)

successful.sort(key=lambda path: path.stat().st_mtime, reverse=True)
protected.update(path.resolve() for path in successful[:3])

for release in successful:
    if release.resolve() in protected:
        continue
    shutil.rmtree(release)
    print(f"오래된 성공 릴리스 정리: {release.name}")

for release in failed:
    if release.resolve() in protected:
        continue
    shutil.rmtree(release)
    print(f"7일 지난 미완료 릴리스 정리: {release.name}")
PY

trap - ERR
echo "배포 검증 완료. 최근 성공 릴리스 3개와 양 슬롯의 current 릴리스를 보존했습니다."
