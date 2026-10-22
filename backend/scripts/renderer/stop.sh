#!/bin/bash
set -euo pipefail

systemctl stop mapmory-render-worker || true
