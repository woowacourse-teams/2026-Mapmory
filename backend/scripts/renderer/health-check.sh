#!/bin/bash
set -euo pipefail

for i in $(seq 1 30); do
  if systemctl is-active --quiet mapmory-render-worker; then
    echo "renderer worker is active after $((i * 5))s"
    exit 0
  fi
  sleep 5
done

echo "renderer worker health check failed"
systemctl status mapmory-render-worker --no-pager || true
journalctl -u mapmory-render-worker -n 100 --no-pager || true
exit 1
