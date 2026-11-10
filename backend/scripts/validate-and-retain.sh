#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/deployment-common.sh"
require_root
require_deployment_id

previous_port=$(state_value previous-port)
target_port=$(state_value target-port)
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
        elif status == "failed" and release.stat().st_mtime < time.time() - 7 * 24 * 60 * 60:
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
    print(f"7일 지난 실패 릴리스 정리: {release.name}")
PY

trap - ERR
echo "배포 검증 완료. 최근 성공 릴리스 3개와 양 슬롯의 current 릴리스를 보존했습니다."
