#!/usr/bin/env bash
# observability/cloudwatch/health-check.sh를 가짜 aws로 실행해 확인한다. AWS에 접속하지 않는다.
# 실행: backend/scripts/tests/cloudwatch-health-check-test.sh
set -euo pipefail

BACKEND_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SCRIPT="${BACKEND_DIR}/observability/cloudwatch/health-check.sh"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

failures=0
pass() { printf 'ok   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }

mkdir -p "${WORK_DIR}/bin"
cat > "${WORK_DIR}/bin/aws" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${FAKE_LOG_DIR}/calls"
arg() {
  local name=$1
  shift
  while (($# > 0)); do
    if [[ $1 == "$name" ]]; then printf '%s\n' "$2"; return 0; fi
    shift
  done
  return 1
}
case "$1 $2" in
  "sts get-caller-identity") printf '111122223333\n' ;;
  "lambda get-function")
    [[ $FAKE_LAMBDA == present ]] || { printf 'ResourceNotFoundException\n' >&2; exit 254; }
    printf 'arn:aws:lambda:ap-northeast-2:111122223333:function:mapmory-discord-alerts\n' ;;
  "lambda get-policy")
    [[ -f ${FAKE_LOG_DIR}/policy ]] || { printf 'ResourceNotFoundException\n' >&2; exit 254; }
    cat "${FAKE_LOG_DIR}/policy" ;;
  "lambda add-permission")
    jq -nc --arg sid "$(arg --statement-id "$@")" --arg topic "$(arg --source-arn "$@")" \
      '{Statement: [{Sid: $sid, Condition: {ArnLike: {"AWS:SourceArn": $topic}}}]} | tojson' -r > "${FAKE_LOG_DIR}/policy" ;;
  "route53 list-health-checks") printf '%s\n' "$FAKE_HEALTH_CHECKS" ;;
  "route53 create-health-check")
    arg --health-check-config "$@" > "${FAKE_LOG_DIR}/health-check-config"
    printf 'hc-new\n' ;;
  "route53 update-health-check"|"route53 change-tags-for-resource") ;;
  "sns create-topic") printf 'arn:aws:sns:us-east-1:111122223333:mapmory-dev-alarms-us-east-1\n' ;;
  "sns list-subscriptions-by-topic")
    [[ -f ${FAKE_LOG_DIR}/subscribed ]] || { printf '{"Subscriptions":[]}\n'; exit 0; }
    printf '{"Subscriptions":[{"Protocol":"lambda","Endpoint":"arn:aws:lambda:ap-northeast-2:111122223333:function:mapmory-discord-alerts"}]}\n' ;;
  "sns subscribe") touch "${FAKE_LOG_DIR}/subscribed" ;;
  "cloudwatch put-metric-alarm") arg --cli-input-json "$@" > "${FAKE_LOG_DIR}/alarm" ;;
  *) printf 'unexpected aws call: %s\n' "$*" >&2; exit 2 ;;
esac
FAKE
chmod +x "${WORK_DIR}/bin/aws"
export PATH="${WORK_DIR}/bin:${PATH}"

reset_fake() {
  rm -rf "${WORK_DIR}/log"
  mkdir -p "${WORK_DIR}/log"
  export FAKE_LOG_DIR="${WORK_DIR}/log"
  export FAKE_LAMBDA=present
  export FAKE_HEALTH_CHECKS='{"HealthChecks":[]}'
  unset HEALTH_CHECK_URL
}

run() {
  "$SCRIPT" "$@" > "${WORK_DIR}/log/stdout" 2> "${WORK_DIR}/log/stderr" < /dev/null
}

writes() {
  grep -cE '^(route53 (create|update|change)|sns (create-topic|subscribe)|lambda add-permission|cloudwatch put)' \
    "${WORK_DIR}/log/calls" || true
}

existing_check() {
  jq -nc --arg host "$1" --argjson interval "$2" '{HealthChecks: [{Id: "hc-old", HealthCheckConfig:
    {Type: "HTTPS", FullyQualifiedDomainName: $host, ResourcePath: "/health", RequestInterval: $interval}}]}'
}

URL=https://api.example.test/health

# 1. 처음 적용: 헬스체크·주제·권한·구독·알람을 만든다.
reset_fake
if run dev --url "$URL" --yes \
  && grep -qF 'route53 create-health-check' "${WORK_DIR}/log/calls" \
  && [[ $(jq -c . "${WORK_DIR}/log/health-check-config") \
      == '{"Type":"HTTPS","FullyQualifiedDomainName":"api.example.test","Port":443,"ResourcePath":"/health","RequestInterval":30,"FailureThreshold":1,"EnableSNI":true}' ]] \
  && grep -qF 'route53 change-tags-for-resource --resource-type healthcheck --resource-id hc-new --add-tags Key=Name,Value=mapmory-dev-health-check' "${WORK_DIR}/log/calls" \
  && grep -qE '^lambda add-permission --region ap-northeast-2 .*--principal sns.amazonaws.com --source-arn arn:aws:sns:us-east-1:[0-9]+:mapmory-dev-alarms-us-east-1$' "${WORK_DIR}/log/calls" \
  && grep -qE '^sns subscribe --region us-east-1 .*--protocol lambda --notification-endpoint arn:aws:lambda:ap-northeast-2:' "${WORK_DIR}/log/calls" \
  && grep -qE '^cloudwatch put-metric-alarm --region us-east-1 ' "${WORK_DIR}/log/calls"; then
  pass "처음 적용하면 헬스체크(HTTPS, 30초, 실패 1회, SNI)와 us-east-1 주제·Lambda 권한·구독·알람을 만든다"
else
  fail "처음 적용하면 헬스체크(HTTPS, 30초, 실패 1회, SNI)와 us-east-1 주제·Lambda 권한·구독·알람을 만든다"
fi

# 2. 알람 정의가 ADR 0031 3절(3분 연속 실패)과 맞다.
if [[ $(jq -c '[.AlarmName, .Namespace, .MetricName, .Dimensions, .Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .ComparisonOperator, .Threshold, .TreatMissingData]' "${WORK_DIR}/log/alarm") \
      == '["mapmory-dev-health-check-failed","AWS/Route53","HealthCheckStatus",[{"Name":"HealthCheckId","Value":"hc-new"}],"Minimum",60,3,3,"LessThanThreshold",1,"breaching"]' ]] \
  && [[ $(jq -c '[.AlarmActions, .OKActions] | map(map(split(":")[-1]))' "${WORK_DIR}/log/alarm") \
      == '[["mapmory-dev-alarms-us-east-1"],["mapmory-dev-alarms-us-east-1"]]' ]] \
  && jq -e '.AlarmDescription | startswith("[깨움] ") and contains("https://api.example.test/health")' "${WORK_DIR}/log/alarm" > /dev/null; then
  pass "알람은 HealthCheckStatus Minimum < 1, 60초 × 3이고 경보·정상을 us-east-1 주제로 알린다"
else
  jq . "${WORK_DIR}/log/alarm" || true
  fail "알람은 HealthCheckStatus Minimum < 1, 60초 × 3이고 경보·정상을 us-east-1 주제로 알린다"
fi

# 3. 다시 실행: 있는 헬스체크를 쓰고, 권한·구독을 다시 넣지 않는다.
first_alarm=$(cat "${WORK_DIR}/log/alarm")
cp "${WORK_DIR}/log/policy" "${WORK_DIR}/policy"
reset_fake
cp "${WORK_DIR}/policy" "${WORK_DIR}/log/policy"
touch "${WORK_DIR}/log/subscribed"
FAKE_HEALTH_CHECKS=$(existing_check api.example.test 30 | jq -c '.HealthChecks[0].Id = "hc-new"')
if run dev --url "$URL" --yes \
  && ! grep -qE 'route53 create-health-check|lambda add-permission|sns subscribe' "${WORK_DIR}/log/calls" \
  && grep -qF 'route53 update-health-check --health-check-id hc-new --failure-threshold 1 --enable-sni' "${WORK_DIR}/log/calls" \
  && [[ $(cat "${WORK_DIR}/log/alarm") == "$first_alarm" ]]; then
  pass "다시 실행하면 같은 헬스체크·권한·구독을 쓰고 같은 알람을 넣는다"
else
  fail "다시 실행하면 같은 헬스체크·권한·구독을 쓰고 같은 알람을 넣는다"
fi

# 4. --dry-run은 읽기만 하고, 만들 알람을 출력한다.
reset_fake
if run dev --url "$URL" --dry-run \
  && [[ $(writes) == 0 ]] \
  && jq -se '.[1].AlarmName == "mapmory-dev-health-check-failed" and .[0].HealthCheckConfig.ResourcePath == "/health"' "${WORK_DIR}/log/stdout" > /dev/null; then
  pass "--dry-run은 AWS에 쓰지 않고 헬스체크 설정과 알람을 출력한다"
else
  fail "--dry-run은 AWS에 쓰지 않고 헬스체크 설정과 알람을 출력한다"
fi

# 5. 주소는 환경 변수로도 받는다.
reset_fake
if HEALTH_CHECK_URL=$URL run dev --dry-run && grep -qF 'https://api.example.test/health' "${WORK_DIR}/log/stderr"; then
  pass "대상 주소를 HEALTH_CHECK_URL로도 받는다"
else
  fail "대상 주소를 HEALTH_CHECK_URL로도 받는다"
fi

# 5-1. 주소를 주지 않으면 환경별 공개 API 주소를 쓴다.
reset_fake
if run dev --dry-run && jq -se '.[0].HealthCheckConfig.FullyQualifiedDomainName == "dev-api.map-mory.com"' "${WORK_DIR}/log/stdout" > /dev/null \
  && reset_fake && run prod --dry-run \
  && jq -se '.[0].HealthCheckConfig.FullyQualifiedDomainName == "api.map-mory.com" and .[1].AlarmName == "mapmory-prod-health-check-failed"' "${WORK_DIR}/log/stdout" > /dev/null; then
  pass "주소를 주지 않으면 dev는 dev-api, prod는 api 주소의 /health를 본다"
else
  fail "주소를 주지 않으면 dev는 dev-api, prod는 api 주소의 /health를 본다"
fi

# 6. 멈춰야 하는 경우에는 아무것도 쓰지 않는다.
expect_stop() {
  local name=$1 message=$2
  shift 2
  if ! run "$@" && [[ ! -f ${WORK_DIR}/log/calls || $(writes) == 0 ]] && grep -qF "$message" "${WORK_DIR}/log/stderr"; then
    pass "$name"
  else
    cat "${WORK_DIR}/log/stderr"
    fail "$name"
  fi
}
reset_fake; expect_stop "http 주소는 받지 않는다(3xx 리디렉트도 성공으로 친다)" "https://<호스트>/<경로> 형식" dev --url http://api.example.test/health --yes
reset_fake; FAKE_LAMBDA=missing; expect_stop "Discord Lambda가 없으면 멈춘다" "Lambda mapmory-discord-alerts를 찾지 못했습니다" dev --url "$URL" --yes
reset_fake; FAKE_HEALTH_CHECKS=$(existing_check api.example.test 30 | jq -c '.HealthChecks += .HealthChecks')
expect_stop "같은 주소의 헬스체크가 2개면 멈춘다" "헬스체크가 2개입니다" dev --url "$URL" --yes
reset_fake; expect_stop "확인에 답하지 않으면 적용하지 않는다" "적용하지 않았습니다" dev --url "$URL"

# 7. 만든 뒤 바꿀 수 없는 요청 간격이 다르면 경고만 하고 그대로 쓴다.
reset_fake
FAKE_HEALTH_CHECKS=$(existing_check api.example.test 10)
if run dev --url "$URL" --dry-run && grep -qF '요청 간격이 30초가 아닙니다' "${WORK_DIR}/log/stderr"; then
  pass "기존 헬스체크의 요청 간격이 다르면 경고한다"
else
  fail "기존 헬스체크의 요청 간격이 다르면 경고한다"
fi

if ((failures > 0)); then
  printf '%s개 실패\n' "${failures}"
  exit 1
fi
printf '모두 통과\n'
