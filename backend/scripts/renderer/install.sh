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

# 썸네일 스크립트(#631)는 Python 3.12 가상환경에 고정한 버전으로 설치한다. 렌더러(Amazon Linux 2023)의 기본 python3는 3.9라
# Pillow 13(3.11 이상)을 쓸 수 없어 `sudo dnf install -y python3.12`로 따로 설치해 둔다. Amazon Linux는 venv가 Python
# 패키지에 들어 있고, pillow-heif wheel 안에 libheif가 들어 있어 따로 설치할 것은 없다.
# 설치하지 못해도 배포는 계속한다. 썸네일 워커는 기본으로 꺼져 있고, 켜도 처음 돌 때 준비되지 않았다고 알리고 멈출 뿐
# 공유 영상 렌더러는 그대로 돈다.
THUMBNAIL_PYTHON=python3.12
if sudo -H -u ubuntu env HOME=/home/ubuntu sh -e -c '
  cd "$1"
  command -v "$2" >/dev/null 2>&1
  # 다른 Python 버전으로 만든 가상환경이 남아 있으면 새로 만든다.
  if ! .venv/bin/python -c "import sys; assert sys.version_info[:2] == (3, 12)" >/dev/null 2>&1; then
    "$2" -m venv --clear .venv
  fi
  .venv/bin/pip install --quiet --upgrade pip
  .venv/bin/pip install --quiet -r requirements.txt
' sh "$THUMBNAIL_DIRECTORY" "$THUMBNAIL_PYTHON"; then
  echo "thumbnail environment is ready: $THUMBNAIL_DIRECTORY/.venv"
else
  echo "WARNING: thumbnail environment was not installed. run 'sudo dnf install -y $THUMBNAIL_PYTHON', then deploy again." >&2
fi

systemctl daemon-reload
