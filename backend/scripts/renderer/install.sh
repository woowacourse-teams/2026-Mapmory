#!/bin/bash
set -euo pipefail

RENDERER_ROOT=/opt/mapmory-renderer
RENDERER_DIRECTORY="$RENDERER_ROOT/video-renderer"
THUMBNAIL_DIRECTORY="$RENDERER_ROOT/thumbnail"

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
install -d -o ubuntu -g ubuntu /var/lib/mapmory-renderer/thumbnails
install -d -o ubuntu -g ubuntu /var/lib/mapmory-renderer/tmp
install -d -o ubuntu -g ubuntu /var/log/mapmory
chown -R ubuntu:ubuntu "$RENDERER_ROOT"
chmod 600 /etc/mapmory-renderer.env

sudo -H -u ubuntu env HOME=/home/ubuntu sh -e -c '
  cd "$1"
  npm ci --omit=dev
  npm exec -- remotion browser ensure
' sh "$RENDERER_DIRECTORY"

# 썸네일 스크립트(#631)는 가상환경에 고정한 버전으로 설치한다. pillow-heif wheel 안에 libheif가 들어 있어 apt로 따로 설치할 것은 없다.
# 설치하지 못해도 배포는 계속한다. 썸네일 워커는 기본으로 꺼져 있고, 켜도 처음 돌 때 준비되지 않았다고 알리고 멈출 뿐
# 공유 영상 렌더러는 그대로 돈다. Ubuntu 22.04는 python3-venv 패키지가 있어야 venv를 만든다.
if sudo -H -u ubuntu env HOME=/home/ubuntu sh -e -c '
  cd "$1"
  python3 -m venv .venv
  .venv/bin/pip install --quiet --upgrade pip
  .venv/bin/pip install --quiet -r requirements.txt
' sh "$THUMBNAIL_DIRECTORY"; then
  echo "thumbnail environment is ready: $THUMBNAIL_DIRECTORY/.venv"
else
  echo "WARNING: thumbnail environment was not installed. install python3 and python3-venv, then deploy again." >&2
fi

systemctl daemon-reload
