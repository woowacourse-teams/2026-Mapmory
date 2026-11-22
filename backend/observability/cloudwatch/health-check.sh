#!/usr/bin/env bash
# 밖에서 /health를 부르는 Route 53 헬스체크와 그 실패 알람을 만들거나 맞춘다. 여러 번 실행해도 결과가 같다(ADR 0031).
#
#   ./health-check.sh <dev|prod> --url https://<공개 주소>/health [--dry-run] [--yes]
#   HEALTH_CHECK_URL=https://<공개 주소>/health ./health-check.sh <dev|prod>
#
# 대상 주소는 저장소에 두지 않고 실행할 때 받는다.
#
# Route 53 헬스체크 지표(AWS/Route53 HealthCheckStatus)는 us-east-1에만 있다. 그래서 알람과 알람이 알리는 SNS 주제도
# us-east-1에 만든다. 그 주제를 서울의 Discord 중계 Lambda에 구독시켜(리전 간 구독), 다른 알람과 같은 곳으로 알린다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

# 외부 헬스체크 기준(ADR 0031 3절 "외부 헬스체크 실패: 3분 연속 실패"). 숫자를 바꾸면 ADR 표도 함께 고친다.
# Route 53은 요청 간격으로 10초와 30초만 받는다(1분은 없다). 30초(기본, 추가 요금 없음)로 두고, 판단은 1분 단위 지표로 한다.
HEALTH_CHECK_INTERVAL=30
# 검사 지점마다 한 번 실패하면 그 지점은 실패로 본다. 연속 판단은 알람(1분 × 3)이 한다.
# 전체 상태는 지점 18% 넘게 성공이면 정상이라, 한 지점의 일시 실패로는 실패가 되지 않는다.
HEALTH_CHECK_FAILURE_THRESHOLD=1
HEALTH_ALARM_PERIOD=60
HEALTH_ALARM_EVALUATION=3

readonly ALARM_REGION=us-east-1
DISCORD_LAMBDA_NAME=${DISCORD_LAMBDA_NAME:-mapmory-discord-alerts}
TARGET_URL=${HEALTH_CHECK_URL:-}

TARGET_HOST=""
TARGET_PATH=""
HEALTH_CHECK_ID=""
TOPIC_ARN=""
LAMBDA_ARN=""

# --url만 여기서 읽고 나머지는 공통 인자로 넘긴다.
parse_health_check_args() {
  local -a rest=()
  while (($# > 0)); do
    if [[ $1 == --url ]]; then
      (($# >= 2)) || die "--url 뒤에 주소가 필요합니다."
      TARGET_URL=$2
      shift 2
      continue
    fi
    rest+=("$1")
    shift
  done
  parse_args "${rest[@]}"
}

# https://<호스트>/<경로>만 받는다. http는 Nginx가 https로 돌려보내면 3xx도 성공으로 쳐서 앱이 죽어도 정상으로 보일 수 있다.
parse_target_url() {
  [[ -n $TARGET_URL ]] || die "대상 주소가 없습니다. --url https://<공개 주소>/health 또는 HEALTH_CHECK_URL로 주세요."
  [[ $TARGET_URL =~ ^https://([A-Za-z0-9.-]+)(/[^?#[:space:]]*)$ ]] \
    || die "대상 주소는 https://<호스트>/<경로> 형식이어야 합니다(포트·쿼리 없이): ${TARGET_URL}"
  TARGET_HOST=${BASH_REMATCH[1]}
  TARGET_PATH=${BASH_REMATCH[2]}
}

desired_config() {
  jq -nc --arg host "$TARGET_HOST" --arg path "$TARGET_PATH" \
    --argjson interval "$HEALTH_CHECK_INTERVAL" --argjson threshold "$HEALTH_CHECK_FAILURE_THRESHOLD" '
    {
      Type: "HTTPS",
      FullyQualifiedDomainName: $host,
      Port: 443,
      ResourcePath: $path,
      RequestInterval: $interval,
      FailureThreshold: $threshold,
      EnableSNI: true
    }'
}

# 같은 주소·경로를 보는 HTTPS 헬스체크를 찾는다. 0개면 새로 만들고, 2개 이상이면 어느 쪽에 알람을 걸지 몰라 멈춘다.
find_health_check() {
  local checks count
  checks=$(aws route53 list-health-checks --output json | jq -c --arg host "$TARGET_HOST" --arg path "$TARGET_PATH" '
    [.HealthChecks[] | select(.HealthCheckConfig.Type == "HTTPS"
      and .HealthCheckConfig.FullyQualifiedDomainName == $host
      and .HealthCheckConfig.ResourcePath == $path)]')
  count=$(jq length <<< "$checks")
  ((count <= 1)) || die "${TARGET_HOST}${TARGET_PATH}를 보는 헬스체크가 ${count}개입니다. 하나만 남기고 다시 실행하세요."
  if ((count == 1)); then
    jq -c '.[0]' <<< "$checks"
  fi
}

lambda_arn() {
  local arn
  if ! arn=$(aws lambda get-function --region "$REGION" --function-name "$DISCORD_LAMBDA_NAME" \
    --query Configuration.FunctionArn --output text); then
    die "${REGION}에서 Lambda ${DISCORD_LAMBDA_NAME}를 찾지 못했습니다."
  fi
  [[ $arn == arn:aws:lambda:* ]] || die "Lambda ARN을 읽지 못했습니다: ${arn}"
  printf '%s\n' "$arn"
}

# 같은 이름의 주제가 있으면 create-topic은 그 주제를 돌려준다.
topic_name() {
  printf 'mapmory-%s-alarms-%s\n' "$ENV_NAME" "$ALARM_REGION"
}

permission_statement_id() {
  printf 'sns-%s-mapmory-%s-alarms\n' "$ALARM_REGION" "$ENV_NAME"
}

has_invoke_permission() {
  local policy
  policy=$(aws lambda get-policy --region "$REGION" --function-name "$DISCORD_LAMBDA_NAME" \
    --query Policy --output text 2>/dev/null) || return 1
  jq -e --arg sid "$(permission_statement_id)" --arg topic "$TOPIC_ARN" '
    .Statement[] | select(.Sid == $sid and .Condition.ArnLike["AWS:SourceArn"] == $topic)' <<< "$policy" >/dev/null
}

is_subscribed() {
  aws sns list-subscriptions-by-topic --region "$ALARM_REGION" --topic-arn "$TOPIC_ARN" --output json 2>/dev/null \
    | jq -e --arg lambda "$LAMBDA_ARN" '.Subscriptions[] | select(.Protocol == "lambda" and .Endpoint == $lambda)' >/dev/null
}

alarm_definition() {
  jq -nc --arg env "$ENV_NAME" --arg id "$HEALTH_CHECK_ID" --arg topic "$TOPIC_ARN" \
    --arg url "https://${TARGET_HOST}${TARGET_PATH}" \
    --argjson period "$HEALTH_ALARM_PERIOD" --argjson evaluation "$HEALTH_ALARM_EVALUATION" '
    {
      AlarmName: "mapmory-\($env)-health-check-failed",
      AlarmDescription: "[깨움] 밖에서 부른 \($url)이 \($evaluation)분 연속 실패했다. 먼저 같은 주소를 직접 열어 보고, 대시보드 \"요청 수 (분당)\"가 함께 끊겼는지와 EC2 상태 검사·Nginx·두 슬롯의 서비스 상태를 본다.",
      ActionsEnabled: true,
      AlarmActions: [$topic],
      OKActions: [$topic],
      InsufficientDataActions: [],
      Namespace: "AWS/Route53",
      MetricName: "HealthCheckStatus",
      Dimensions: [{Name: "HealthCheckId", Value: $id}],
      Statistic: "Minimum",
      Period: $period,
      EvaluationPeriods: $evaluation,
      DatapointsToAlarm: $evaluation,
      ComparisonOperator: "LessThanThreshold",
      Threshold: 1,
      TreatMissingData: "breaching"
    }'
}

main() {
  parse_health_check_args "$@"
  require_tools
  parse_target_url

  local account existing desired caller_reference
  account=$(account_id)
  LAMBDA_ARN=$(lambda_arn)
  TOPIC_ARN="arn:aws:sns:${ALARM_REGION}:${account}:$(topic_name)"
  desired=$(desired_config)
  existing=$(find_health_check)

  local check_plan="새로 만든다" permission_plan="추가한다" subscription_plan="추가한다"
  if [[ -n $existing ]]; then
    HEALTH_CHECK_ID=$(jq -r .Id <<< "$existing")
    check_plan="있음(${HEALTH_CHECK_ID}), 실패 기준·SNI를 맞춘다"
    [[ $(jq -r .HealthCheckConfig.RequestInterval <<< "$existing") == "$HEALTH_CHECK_INTERVAL" ]] \
      || log "경고: 기존 헬스체크의 요청 간격이 ${HEALTH_CHECK_INTERVAL}초가 아닙니다. 간격은 만든 뒤 바꿀 수 없어 그대로 둡니다."
  fi
  if has_invoke_permission; then permission_plan="있음"; fi
  if is_subscribed; then subscription_plan="있음"; fi

  confirm "$account" \
    "대상: https://${TARGET_HOST}${TARGET_PATH} (HTTPS, ${HEALTH_CHECK_INTERVAL}초 간격, 지점마다 ${HEALTH_CHECK_FAILURE_THRESHOLD}회 실패면 실패)" \
    "헬스체크: ${check_plan}" \
    "알람: mapmory-${ENV_NAME}-health-check-failed (${ALARM_REGION})" \
    "SNS 주제: $(topic_name) (${ALARM_REGION}, 없으면 만든다)" \
    "Lambda ${DISCORD_LAMBDA_NAME}(${REGION}) 호출 권한: ${permission_plan}, 구독: ${subscription_plan}"

  if [[ $DRY_RUN == true ]]; then
    jq -n --argjson config "$desired" '{HealthCheckConfig: $config}'
    HEALTH_CHECK_ID=${HEALTH_CHECK_ID:-"(새로 만든 헬스체크 ID)"}
    alarm_definition | jq .
    return 0
  fi

  if [[ -z $existing ]]; then
    caller_reference="mapmory-${ENV_NAME}-health-check-$(date +%s)"
    HEALTH_CHECK_ID=$(aws route53 create-health-check --caller-reference "$caller_reference" \
      --health-check-config "$desired" --query HealthCheck.Id --output text)
    log "헬스체크를 만들었습니다: ${HEALTH_CHECK_ID}"
  else
    aws route53 update-health-check --health-check-id "$HEALTH_CHECK_ID" \
      --failure-threshold "$HEALTH_CHECK_FAILURE_THRESHOLD" --enable-sni >/dev/null
  fi
  aws route53 change-tags-for-resource --resource-type healthcheck --resource-id "$HEALTH_CHECK_ID" \
    --add-tags "Key=Name,Value=mapmory-${ENV_NAME}-health-check"

  TOPIC_ARN=$(aws sns create-topic --region "$ALARM_REGION" --name "$(topic_name)" --query TopicArn --output text)
  if [[ $permission_plan != "있음" ]]; then
    aws lambda add-permission --region "$REGION" --function-name "$DISCORD_LAMBDA_NAME" \
      --statement-id "$(permission_statement_id)" --action lambda:InvokeFunction \
      --principal sns.amazonaws.com --source-arn "$TOPIC_ARN" >/dev/null
  fi
  if [[ $subscription_plan != "있음" ]]; then
    aws sns subscribe --region "$ALARM_REGION" --topic-arn "$TOPIC_ARN" \
      --protocol lambda --notification-endpoint "$LAMBDA_ARN" >/dev/null
  fi

  aws cloudwatch put-metric-alarm --region "$ALARM_REGION" --cli-input-json "$(alarm_definition)"
  log "적용: mapmory-${ENV_NAME}-health-check-failed (${ALARM_REGION})"
  log "5분 뒤 README의 확인 명령으로 헬스체크가 정상이고 알람이 OK인지 봅니다."
}

main "$@"
