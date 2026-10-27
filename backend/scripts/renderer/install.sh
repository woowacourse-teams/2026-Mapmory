#!/bin/bash
set -euo pipefail

RENDERER_ROOT=/opt/mapmory-renderer
RENDERER_DIRECTORY="$RENDERER_ROOT/video-renderer"

for command in java node npm; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "required command is missing: $command"
    exit 1
  fi
done

if [ ! -f /etc/mapmory-renderer.env ]; then
  echo "renderer environment file is missing: /etc/mapmory-renderer.env"
  exit 1
fi

install -d -o ubuntu -g ubuntu /var/lib/mapmory-renderer/work
install -d -o ubuntu -g ubuntu /var/lib/mapmory-renderer/tmp
install -d -o ubuntu -g ubuntu /var/log/mapmory
chown -R ubuntu:ubuntu "$RENDERER_ROOT"
chmod 600 /etc/mapmory-renderer.env

sudo -H -u ubuntu env HOME=/home/ubuntu sh -e -c '
  cd "$1"
  npm ci --omit=dev
  npm exec -- remotion browser ensure
' sh "$RENDERER_DIRECTORY"

systemctl daemon-reload
