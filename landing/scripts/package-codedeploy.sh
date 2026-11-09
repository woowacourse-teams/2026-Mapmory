#!/usr/bin/env bash
set -Eeuo pipefail

sha="${1:?commit SHA is required}"
output="${2:?output archive is required}"
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid commit SHA' >&2; exit 2; }
landing_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "$landing_root/dist/client/index.html" ]] || { echo 'Build the landing first' >&2; exit 1; }

# The archive contains only static client files and landing-specific deployment hooks.
bundle="$(mktemp -d)"
trap 'rm -rf -- "$bundle"' EXIT
cp -R -- "$landing_root/dist/client" "$bundle/client"
printf '%s\n' "$sha" > "$bundle/client/release.txt"
cp -- "$landing_root/codedeploy/appspec.yml" "$bundle/appspec.yml"
mkdir -- "$bundle/scripts"
cp -- "$landing_root/codedeploy/activate.sh" "$bundle/scripts/activate.sh"
chmod 755 "$bundle/scripts/activate.sh"
tar -czf "$output" -C "$bundle" appspec.yml scripts client
