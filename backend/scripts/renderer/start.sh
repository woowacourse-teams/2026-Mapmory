#!/bin/bash
set -euo pipefail

systemctl enable mapmory-render-worker
systemctl restart mapmory-render-worker
