#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../../codedeploy/activate.sh"
scenario="${1:?scenario}"
fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
root="$fixture/landing"
bundle="$fixture/bundle"
mkdir -p "$root/releases/old" "$bundle/client" "$fixture/outside"
mkdir -p "$bundle/client/recap"
printf old > "$root/releases/old/index.html"
printf external > "$fixture/outside/index.html"
ln -s "$root/releases/old" "$root/current"
sha="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
printf new > "$bundle/client/index.html"
printf '%s\n' "$sha" > "$bundle/client/release.txt"
printf '<script src="/recap/assets/app.js"></script>' > "$bundle/client/recap/index.html"
printf '%s\n' "$sha" > "$bundle/client/recap/release.txt"
deployment_id=d-TEST123

# Never call host services or the network. Filesystem operations stay under mktemp.
chown() { return 0; }
nginx() { [[ "$scenario" != nginx-failure ]]; }
systemctl() {
  [[ "$*" == 'reload nginx' ]] || return 99
  if [[ "$scenario" == reload-failure && ! -f "$fixture/reload-failed" ]]; then
    touch "$fixture/reload-failed"
    return 1
  fi
}
curl() {
  [[ "$*" == *"--noproxy *"* ]] || return 97
  [[ "$*" == *"--resolve map-mory.com:443:127.0.0.1"* ]] || return 98
  [[ "$scenario" != http-failure ]] || return 22
  if [[ "$*" == *'/recap/'* ]]; then
    [[ "$scenario" != recap-http-failure ]] || return 22
    if [[ "$*" == *release.txt* ]]; then
      if [[ "$scenario" == recap-identity-failure ]]; then printf wrong; else printf '%s' "$sha"; fi
    elif [[ "$scenario" == recap-shell-failure ]]; then
      printf '<html>landing fallback</html>'
    else
      printf '<script src="/recap/assets/app.js"></script>'
    fi
    return
  fi
  if [[ "$*" == *release.txt* ]]; then
    if [[ "$scenario" == identity-failure ]]; then printf wrong; else printf '%s' "$sha"; fi
  fi
}
case "$scenario" in
  proxy-env) export HTTPS_PROXY=http://invalid.proxy.test:9999 ALL_PROXY=http://invalid.proxy.test:9999 ;;
  bad-marker) printf '../bad' > "$bundle/client/release.txt" ;;
  missing-recap) rm -- "$bundle/client/recap/index.html" ;;
  bad-recap-marker) printf wrong > "$bundle/client/recap/release.txt" ;;
  bad-id) deployment_id=../bad ;;
  missing-previous) unlink "$root/current" ;;
  outside-previous) ln -sfnT "$fixture/outside" "$root/current" ;;
  duplicate) mkdir "$root/releases/$sha-$deployment_id" ;;
  symlink-bundle) ln -s "$fixture/outside/index.html" "$bundle/client/escape" ;;
  locked) flock() { return 1; } ;;
esac
set +e
( set -e; activate_release "$root" "$bundle" "$deployment_id" )
status=$?
set -e
if [[ "$scenario" == success || "$scenario" == proxy-env ]]; then
  [[ "$status" == 0 ]]
  [[ "$(readlink -f "$root/current")" == "$root/releases/$sha-$deployment_id" ]]
  [[ "$(cat "$root/current/release.txt")" == "$sha" ]]
else
  [[ "$status" != 0 ]]
  case "$scenario" in
    missing-previous) [[ ! -L "$root/current" ]] ;;
    outside-previous) [[ "$(readlink -f "$root/current")" == "$fixture/outside" ]] ;;
    *) [[ "$(readlink -f "$root/current")" == "$root/releases/old" ]] ;;
  esac
fi
[[ "$(cat "$root/releases/old/index.html")" == old ]]
[[ "$(cat "$fixture/outside/index.html")" == external ]]
echo "PASS $scenario"
