#!/usr/bin/env bash
# observability/cloudwatch/alarms.sh와 dashboard.sh를 가짜 aws로 실행해 확인한다.
# AWS에 접속하지 않는다. FAKE_* 환경 변수로 가짜 aws의 응답을 바꾼다.
set -euo pipefail

BACKEND_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CLOUDWATCH_DIR="${BACKEND_DIR}/observability/cloudwatch"
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

failures=0
FAKE_ACCOUNT_ID=111122223333
FAKE_INSTANCE_ID=i-0123456789abcdef0

mkdir -p "${WORK_DIR}/bin"
cat > "${WORK_DIR}/bin/aws" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${FAKE_LOG_DIR}/calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\n' "$FAKE_ACCOUNT_ID" ;;
  "ec2 describe-instances")
    # shellcheck disable=SC2086 # 공백으로 나눈 ID 목록을 JSON 배열로 바꾼다.
    if [[ $* == *tag:Name* ]]; then jq -nc '$ARGS.positional' --args $FAKE_INSTANCES; else printf '["other-server"]\n'; fi ;;
  "cloudwatch describe-alarms")
    if [[ $* == *AlarmActions* ]]; then printf '%s\n' "$FAKE_ACTIONS"
    elif [[ $* == *"--output json"* ]]; then printf '%s\n' "$FAKE_CURRENT_ALARMS"
    else printf 'unmanaged-report\n'; fi ;;
  "cloudwatch list-metrics") printf '%s\n' "$FAKE_DISK_METRICS" ;;
  "rds describe-db-instances") printf '%s\n' "$FAKE_DB_ID" ;;
  "cloudwatch put-metric-alarm")
    while (($# > 0)); do
      if [[ $1 == --cli-input-json ]]; then printf '%s\n' "$2" >> "${FAKE_LOG_DIR}/alarms"; fi
      shift
    done ;;
  "cloudwatch put-dashboard")
    while (($# > 0)); do
      if [[ $1 == --dashboard-body ]]; then printf '%s\n' "$2" > "${FAKE_LOG_DIR}/dashboard"; fi
      shift
    done
    printf '{"DashboardValidationMessages":[]}\n' ;;
  *) printf 'unexpected aws call: %s\n' "$*" >&2; exit 2 ;;
esac
FAKE
chmod +x "${WORK_DIR}/bin/aws"

reset_fake() {
  rm -rf "${WORK_DIR}/log"
  mkdir -p "${WORK_DIR}/log"
  export FAKE_LOG_DIR="${WORK_DIR}/log"
  export FAKE_ACCOUNT_ID
  export FAKE_INSTANCES="$FAKE_INSTANCE_ID"
  export FAKE_ACTIONS='["arn:aws:sns:ap-northeast-2:111122223333:alerts"]'
  export FAKE_DISK_METRICS='{"Metrics":[{"Dimensions":[{"Name":"path","Value":"/"},{"Name":"InstanceId","Value":"i-0123456789abcdef0"},{"Name":"device","Value":"nvme0n1p1"},{"Name":"fstype","Value":"xfs"}]}]}'
  export FAKE_DB_ID=mapmory-dev-db
  export FAKE_CURRENT_ALARMS='{"MetricAlarms":[{"AlarmName":"mapmory-dev-ec2-cpu-usage-high","AlarmActions":["arn:aws:sns:ap-northeast-2:111122223333:infra-alerts"],"OKActions":[]}]}'
}

run() {
  PATH="${WORK_DIR}/bin:${PATH}" "${CLOUDWATCH_DIR}/$1" "${@:2}" > "${WORK_DIR}/log/stdout" 2> "${WORK_DIR}/log/stderr"
}

pass() { printf 'ok   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }

count_lines() {
  if [[ -f $1 ]]; then wc -l < "$1" | tr -d ' '; else printf '0'; fi
}

# 1. --dry-run은 읽기만 하고 put을 부르지 않는다.
reset_fake
if run alarms.sh dev --dry-run && [[ $(count_lines "${WORK_DIR}/log/alarms") == 0 ]] \
  && [[ $(jq -s length "${WORK_DIR}/log/stdout") == 7 ]]; then
  pass "alarms --dry-run은 알람 7개를 출력하고 쓰지 않는다"
else
  fail "alarms --dry-run은 알람 7개를 출력하고 쓰지 않는다"
fi

# 1-1. 확인 화면에 바뀔 알림 대상을 주제 이름으로만 보여 준다(ARN 전체는 출력하지 않는다).
if grep -qF 'SNS(alerts)' "${WORK_DIR}/log/stderr" \
  && grep -qF 'mapmory-dev-ec2-cpu-usage-high: 경보 → infra-alerts, 정상 → (없음)' "${WORK_DIR}/log/stderr" \
  && grep -qF 'mapmory-dev-api-5xx-ratio: 없음(새로 만든다)' "${WORK_DIR}/log/stderr" \
  && ! grep -qF "arn:aws:sns" "${WORK_DIR}/log/stderr"; then
  pass "확인 화면에 지금과 바뀔 알림 대상을 주제 이름으로 보여 준다"
else
  fail "확인 화면에 지금과 바뀔 알림 대상을 주제 이름으로 보여 준다"
fi

# 2. 두 번 실행해도 같은 정의를 넣는다.
reset_fake
run alarms.sh dev --yes && cp "${WORK_DIR}/log/alarms" "${WORK_DIR}/first"
reset_fake
run alarms.sh dev --yes || true
if [[ $(count_lines "${WORK_DIR}/first") == 7 ]] && cmp -s "${WORK_DIR}/first" "${WORK_DIR}/log/alarms"; then
  pass "alarms를 두 번 실행해도 같은 정의를 넣는다"
else
  fail "alarms를 두 번 실행해도 같은 정의를 넣는다"
fi

# 3. 넣은 정의가 명세와 맞다.
alarm() { jq -c --arg name "$1" 'select(.AlarmName == $name)' "${WORK_DIR}/first"; }
if [[ $(alarm mapmory-dev-metrics-missing | jq -c '[.Statistic, .Period, .EvaluationPeriods, .Threshold, .ComparisonOperator, .TreatMissingData, (.OKActions | length)]') \
      == '["SampleCount",300,2,1,"LessThanThreshold","breaching",1]' ]] \
  && [[ $(alarm mapmory-dev-metrics-missing | jq -c '[.Namespace, .MetricName, (.Dimensions | map("\(.Name)=\(.Value)") | sort | join(","))]') \
      == '["Mapmory/Backend","hikaricp_connections_max","environment=dev,pool=HikariPool-1,service=mapmory-backend"]' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -r '.Metrics[] | select(.ReturnData) | .Expression') \
      == 'IF(total >= 10, 100 * FILL(m3, 0) / total, 0)' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -c '[.Metrics[] | select(.MetricStat) | [.Id, .MetricStat.Metric.Namespace, .MetricStat.Metric.MetricName, (.MetricStat.Metric.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .MetricStat.Period, .MetricStat.Stat]]') \
      == '[["m1","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=SUCCESS,service=mapmory-backend",60,"Sum"],["m2","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=CLIENT_ERROR,service=mapmory-backend",60,"Sum"],["m3","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=SERVER_ERROR,service=mapmory-backend",60,"Sum"]]' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -c '[.Threshold, .ComparisonOperator, .EvaluationPeriods, .DatapointsToAlarm, .TreatMissingData]') \
      == '[5,"GreaterThanThreshold",5,3,"notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-db-connection-pending | jq -c '[.Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData]') \
      == '["Maximum",60,5,3,1,"GreaterThanOrEqualToThreshold","notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-disk-usage-high | jq -c '[.Dimensions[] | .Value]') \
      == '["/","i-0123456789abcdef0","nvme0n1p1","xfs"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-memory-usage-high | jq -c '[.Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData]') \
      == '["Average",300,3,3,85,"GreaterThanOrEqualToThreshold","missing"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-status-check-failed | jq -r .TreatMissingData) == breaching ]] \
  && [[ $(jq -s '[.[] | select(.AlarmDescription | length > 0)] | length' "${WORK_DIR}/first") == 7 ]]; then
  pass "알람 정의가 명세와 맞다"
else
  fail "알람 정의가 명세와 맞다"
fi

# 4. 멈춰야 하는 경우에는 아무것도 쓰지 않는다.
expect_stop() {
  local name=$1
  shift
  if ! run "$@" < /dev/null && [[ $(count_lines "${WORK_DIR}/log/alarms") == 0 && ! -f ${WORK_DIR}/log/dashboard ]]; then
    pass "$name"
  else
    fail "$name"
  fi
}
reset_fake; FAKE_INSTANCES=""; expect_stop "인스턴스가 0개면 멈춘다" alarms.sh dev --yes
reset_fake; FAKE_INSTANCES="i-0123456789abcdef0 i-0fedcba9876543210"; expect_stop "인스턴스가 2개면 멈춘다" alarms.sh dev --yes
reset_fake; FAKE_ACTIONS="null"; expect_stop "SNS 알림 대상이 없으면 멈춘다" alarms.sh dev --yes
reset_fake; FAKE_DISK_METRICS='{"Metrics":[]}'; expect_stop "루트 디스크 지표가 없으면 멈춘다" alarms.sh dev --yes
reset_fake; expect_stop "확인에 답하지 않으면 적용하지 않는다" alarms.sh dev
reset_fake; FAKE_DB_ID="None"; expect_stop "RDS가 없으면 대시보드를 만들지 않는다" dashboard.sh dev --yes

# 5. 대시보드 템플릿을 모두 채운다.
reset_fake
# shellcheck disable=SC2016 # 템플릿 자리표시자 ${를 찾는다.
if run dashboard.sh dev --yes \
  && jq -e . "${WORK_DIR}/log/dashboard" > /dev/null \
  && ! grep -qF '${' "${WORK_DIR}/log/dashboard" \
  && [[ $(grep -o "arn:aws:cloudwatch:ap-northeast-2:${FAKE_ACCOUNT_ID}:alarm:mapmory-dev-" "${WORK_DIR}/log/dashboard" | wc -l | tr -d ' ') -gt 0 ]] \
  && grep -qF "$FAKE_INSTANCE_ID" "${WORK_DIR}/log/dashboard"; then
  pass "대시보드 템플릿의 변수를 모두 채운다"
else
  fail "대시보드 템플릿의 변수를 모두 채운다"
fi

# 6. 대시보드가 가리키는 알람은 alarms.sh가 관리하는 7개와 바꾸지 않는 3개뿐이고, 관리하는 7개는 모두 보인다.
#    알람 이름은 RDS 식별자 같은 다른 값이 아니라 환경으로만 정해져야 한다.
# shellcheck disable=SC2016 # 템플릿 자리표시자 ${ENV}를 찾는다.
dashboard_alarms=$(grep -oE 'alarm:[^"]+' "${CLOUDWATCH_DIR}/dashboard.json.tmpl" | sort -u)
managed=$(jq -r '.AlarmName | sub("^mapmory-dev-"; "alarm:mapmory-${ENV}-")' "${WORK_DIR}/first" | sort -u)
# shellcheck disable=SC2016
unknown=$(comm -23 <(printf '%s\n' "$dashboard_alarms") \
  <(printf '%s\n' "$managed" 'alarm:mapmory-${ENV}-application-error' 'alarm:mapmory-${ENV}-api-5xx-rate' 'alarm:mapmory-${ENV}-rds-free-storage-low' | sort -u))
missing=$(comm -13 <(printf '%s\n' "$dashboard_alarms") <(printf '%s\n' "$managed"))
if [[ -z $unknown && -z $missing ]]; then
  pass "대시보드의 알람 이름이 실제 알람과 맞다"
else
  printf '모르는 알람: %s\n빠진 알람: %s\n' "${unknown:-(없음)}" "${missing:-(없음)}"
  fail "대시보드의 알람 이름이 실제 알람과 맞다"
fi

# 7. 공개 저장소에 계정 번호·인스턴스 ID·계정이 박힌 ARN을 남기지 않는다.
leaks=$(grep -rnE '[0-9]{12}|i-[0-9a-f]{8,}|arn:aws:[a-z0-9-]+:[a-z0-9-]*:[0-9]' "${BACKEND_DIR}/observability" || true)
if [[ -z $leaks ]]; then
  pass "observability/에 계정 번호·인스턴스 ID가 없다"
else
  printf '%s\n' "$leaks"
  fail "observability/에 계정 번호·인스턴스 ID가 없다"
fi

if ((failures > 0)); then
  printf '%s개 실패\n' "${failures}"
  exit 1
fi
printf '모두 통과\n'
