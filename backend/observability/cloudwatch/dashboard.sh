#!/usr/bin/env bash
# dashboard.json.tmpl을 채워 mapmory-<env>-v2 대시보드를 만들거나 덮어쓴다.
#
#   ./dashboard.sh <dev|prod> [--dry-run] [--yes] [--name-tag <EC2 Name 태그>]
#
# 알람 위젯이 알람을 가리키므로 alarms.sh를 먼저 실행한다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

readonly TEMPLATE="${SCRIPT_DIR}/dashboard.json.tmpl"

# 템플릿의 ${이름}을 값으로 바꾼다. 아는 변수만 바꾸고, 남은 ${가 있으면 멈춘다.
# 값은 sed에 그대로 들어가므로 영문·숫자·._/- 만 허용한다.
render_dashboard() {
  local value body
  for value in "$ENV_NAME" "$@"; do
    [[ $value =~ ^[A-Za-z0-9._/-]+$ ]] || die "템플릿에 넣을 수 없는 값입니다: '${value}'"
  done
  # shellcheck disable=SC2016 # ${이름}은 bash 변수가 아니라 템플릿 자리표시자다.
  body=$(sed \
    -e "s|\${ENV}|${ENV_NAME}|g" \
    -e "s|\${ACCOUNT_ID}|$1|g" \
    -e "s|\${INSTANCE_ID}|$2|g" \
    -e "s|\${DB_ID}|$3|g" \
    -e "s|\${DISK_DEVICE}|$4|g" \
    -e "s|\${DISK_FSTYPE}|$5|g" \
    "$TEMPLATE")
  # shellcheck disable=SC2016
  [[ $body != *'${'* ]] || die "템플릿에 채우지 않은 변수가 남았습니다."
  jq -c . <<< "$body" || die "템플릿을 채운 결과가 올바른 JSON이 아닙니다."
}

# 권한 없음·세션 만료 같은 aws 오류는 감추지 않고 함께 보여 준다. RDS 식별자가 틀린 경우와 구분하기 위해서다.
check_db_instance() {
  local found error=""
  found=$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$1" \
    --query 'DBInstances[0].DBInstanceIdentifier' --output text 2>&1) || { error=$found; found=""; }
  [[ $found == "$1" ]] || die "RDS 인스턴스 '$1'을 확인하지 못했습니다. ${error}"
}

main() {
  parse_args "$@"
  require_tools

  local account instance_id disk device fstype db_id body result name
  name="mapmory-${ENV_NAME}-v2"
  db_id="mapmory-${ENV_NAME}-db"
  account=$(account_id)
  instance_id=$(find_instance_id)
  disk=$(find_root_disk "$instance_id")
  read -r device fstype <<< "$disk"
  check_db_instance "$db_id"
  body=$(render_dashboard "$account" "$instance_id" "$db_id" "$device" "$fstype")

  confirm "$account" \
    "EC2: Name=${NAME_TAG} (${instance_id}), 루트 디스크 ${device}/${fstype}" \
    "RDS: ${db_id}" \
    "만들거나 덮어쓸 대시보드: ${name}"

  if [[ $DRY_RUN == true ]]; then
    jq . <<< "$body"
    return 0
  fi
  result=$(aws cloudwatch put-dashboard --region "$REGION" --dashboard-name "$name" \
    --dashboard-body "$body" --output json)
  if ! jq -e '(.DashboardValidationMessages // []) | length == 0' <<< "$result" >/dev/null; then
    log "CloudWatch가 대시보드에 경고를 남겼습니다:"
    jq . <<< "$result" >&2
    die "경고를 확인하세요."
  fi
  log "적용: ${name}"
}

main "$@"
