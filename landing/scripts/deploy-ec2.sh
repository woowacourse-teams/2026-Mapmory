#!/usr/bin/env bash

set -Eeuo pipefail

release_id="${1:?release id is required}"
archive_path="${2:?archive path is required}"

if [[ ! "$release_id" =~ ^[0-9a-f]{40}-[0-9]+(-[0-9]+)?$ ]]; then
  echo "Invalid release id: $release_id" >&2
  exit 2
fi

if [[ ! "$archive_path" =~ ^/tmp/mapmory-landing-[0-9]+-[0-9]+\.tar\.gz$ ]]; then
  echo "Invalid archive path: $archive_path" >&2
  exit 2
fi

release_root="/var/www/mapmory/releases"
release_dir="$release_root/$release_id"
current_link="/var/www/mapmory/current"
previous_target=""

if [[ -L "$current_link" ]]; then
  previous_target="$(readlink -f -- "$current_link")"
fi

cleanup() {
  sudo rm -f -- "$archive_path"
}

rollback() {
  if [[ -n "$previous_target" && -d "$previous_target" ]]; then
    sudo ln -sfnT "$previous_target" "$current_link"
    sudo nginx -t
    sudo systemctl reload nginx
  fi
}

trap cleanup EXIT

if sudo test -e "$release_dir"; then
  echo "Release already exists: $release_dir" >&2
  exit 1
fi

sudo mkdir -p "$release_dir"
sudo tar -xzf "$archive_path" -C "$release_dir"

if ! sudo test -f "$release_dir/index.html"; then
  echo "Release is missing index.html" >&2
  exit 1
fi

sudo chown -R root:root "$release_dir"
sudo chmod -R a+rX "$release_dir"
sudo ln -sfnT "$release_dir" "$current_link"

if ! sudo nginx -t; then
  rollback
  exit 1
fi

sudo systemctl reload nginx

if ! curl \
  --fail \
  --silent \
  --show-error \
  --connect-timeout 5 \
  --max-time 15 \
  --resolve map-mory.com:443:127.0.0.1 \
  https://map-mory.com/ \
  --output /dev/null; then
  rollback
  exit 1
fi

echo "Activated landing release: $release_id"
