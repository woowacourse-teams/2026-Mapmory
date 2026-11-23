#!/usr/bin/env bash
set -Eeuo pipefail

case "${SPRING_PROFILES_ACTIVE:-}" in
  dev|prod)
    ;;
  *)
    echo "SPRING_PROFILES_ACTIVE는 서버 환경에 맞게 dev 또는 prod로 지정해야 합니다." >&2
    exit 1
    ;;
esac
