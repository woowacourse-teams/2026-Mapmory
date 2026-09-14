#!/usr/bin/env bash
set -Eeuo pipefail

# Root/bundle arguments make the same activation logic testable in an isolated fixture.
# The CodeDeploy entry point below always pins the production root and application.
activate_release() (
  set -Eeuo pipefail
  local root="$1" bundle="$2" deployment_id="$3" sha release previous link staged switched=0
  [[ "$deployment_id" =~ ^d-[A-Za-z0-9]+$ ]] || { echo 'Invalid deployment id' >&2; exit 2; }
  [[ -f "$bundle/client/index.html" && -f "$bundle/client/release.txt" ]] || {
    echo 'Missing static landing files' >&2; exit 2;
  }
  [[ -f "$bundle/client/recap/index.html" && -f "$bundle/client/recap/release.txt" ]] || {
    echo 'Missing static recap files' >&2; exit 2;
  }
  sha="$(cat -- "$bundle/client/release.txt")"
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid release marker' >&2; exit 2; }
  [[ "$(cat -- "$bundle/client/recap/release.txt")" == "$sha" ]] || {
    echo 'Recap release identity mismatch' >&2; exit 2;
  }
  [[ -z "$(find "$bundle/client" -type l -print -quit)" ]] || {
    echo 'Symlinks are not allowed in the static bundle' >&2; exit 2;
  }
  root="$(realpath -e -- "$root")"
  [[ -d "$root/releases" && ! -L "$root/releases" ]] || {
    echo 'Provision the landing release directory first' >&2; exit 2;
  }
  link="$root/current"
  # Acquire the lock before reading the previous target, so rollback cannot be stale.
  exec 9>"$root/.codedeploy.lock"
  flock -n 9 || { echo 'Another landing activation is running' >&2; exit 1; }
  # Production is already provisioned; require a known-good first-deploy rollback target.
  [[ -L "$link" ]] || { echo 'Missing previous landing release' >&2; exit 2; }
  previous="$(readlink -f -- "$link")"
  [[ "$previous" == "$root/releases/"* && -f "$previous/index.html" ]] || {
    echo 'Previous release is outside the landing release directory' >&2; exit 2;
  }
  release="$root/releases/$sha-$deployment_id"
  staged="$root/.current-$deployment_id"
  [[ ! -e "$release" && ! -L "$release" && ! -e "$staged" && ! -L "$staged" ]] || {
    echo 'Release or staging link already exists' >&2; exit 2;
  }
  restore_previous() {
    local status="$1"
    trap - ERR INT TERM
    if [[ "$switched" == 1 ]]; then
      echo 'Activation failed; restoring previous landing release' >&2
      ln -sfnT -- "$previous" "$staged" && mv -Tf -- "$staged" "$link"
      nginx -t && systemctl reload nginx
    fi
    exit "$status"
  }
  trap 'restore_previous $?' ERR
  trap 'restore_previous 130' INT
  trap 'restore_previous 143' TERM

  nginx -t
  mkdir -- "$release"
  cp -R -- "$bundle/client/." "$release/"
  chown -R root:root "$release"
  chmod -R a+rX "$release"
  ln -s -- "$release" "$staged"
  # rename(2) replaces the link atomically: there is no missing-current interval.
  switched=1
  mv -Tf -- "$staged" "$link"
  systemctl reload nginx
  local served_sha
  if ! served_sha="$(curl --noproxy '*' --fail --silent --show-error --connect-timeout 5 --max-time 15 \
    --retry 2 --retry-delay 1 --resolve map-mory.com:443:127.0.0.1 \
    "https://map-mory.com/release.txt?deployment=$deployment_id")"; then
    echo 'Local release request failed' >&2
    false
  fi
  [[ "$served_sha" == "$sha" ]] || { echo 'Release identity check failed' >&2; false; }
  curl --noproxy '*' --fail --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve map-mory.com:443:127.0.0.1 https://map-mory.com/ --output /dev/null
  local recap_sha recap_html
  recap_sha="$(curl --noproxy '*' --fail --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve map-mory.com:443:127.0.0.1 "https://map-mory.com/recap/release.txt?deployment=$deployment_id")"
  [[ "$recap_sha" == "$sha" ]] || { echo 'Served recap release identity mismatch' >&2; false; }
  recap_html="$(curl --noproxy '*' --fail --silent --show-error --connect-timeout 5 --max-time 15 \
    --resolve map-mory.com:443:127.0.0.1 https://map-mory.com/recap/)"
  [[ "$recap_html" == *'src="/recap/assets/'* ]] || {
    echo 'Recap URL did not serve the campaign shell' >&2; false;
  }
  trap - ERR INT TERM
  echo "Activated landing release: $sha ($deployment_id)"
)

if [[ "${BASH_SOURCE[0]:-$0}" == "$0" ]]; then
  [[ "${APPLICATION_NAME:-}" == mapmory-landing && \
     "${DEPLOYMENT_GROUP_NAME:-}" == mapmory-landing-production ]] || {
    echo 'This hook may run only in the landing CodeDeploy application/group' >&2; exit 2;
  }
  bundle="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
  activate_release /var/www/mapmory "$bundle" "${DEPLOYMENT_ID:?deployment id is required}"
fi
