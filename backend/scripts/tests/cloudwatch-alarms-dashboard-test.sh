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
  "rds describe-db-instances")
    if [[ $FAKE_DB_ID == ACCESS_DENIED ]]; then
      printf 'An error occurred (AccessDenied) when calling the DescribeDBInstances operation\n' >&2
      exit 254
    fi
    printf '%s\n' "$FAKE_DB_ID" ;;
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
  && [[ $(jq -s length "${WORK_DIR}/log/stdout") == 11 ]] \
  && [[ $(jq -sc '[.[].AlarmName | select(test("api-5xx-count|api-5xx-ratio|api-read-latency|api-write-latency|cache-bypass"))] | sort' "${WORK_DIR}/log/stdout") \
      == '["mapmory-dev-api-5xx-count","mapmory-dev-api-5xx-ratio","mapmory-dev-api-read-latency","mapmory-dev-api-write-latency","mapmory-dev-cache-bypass"]' ]]; then
  pass "alarms --dry-run은 SLO 알람을 포함한 알람 11개를 출력하고 쓰지 않는다"
else
  fail "alarms --dry-run은 SLO 알람을 포함한 알람 11개를 출력하고 쓰지 않는다"
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
if [[ $(count_lines "${WORK_DIR}/first") == 11 ]] && cmp -s "${WORK_DIR}/first" "${WORK_DIR}/log/alarms"; then
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
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -c '[.Metrics[] | select(.Expression) | [.Id, .Expression, .ReturnData]]') \
      == '[["total","FILL(m1, 0) + FILL(m2, 0) + FILL(m3, 0)",false],["ratio","IF(total >= 30, 100 * FILL(m3, 0) / total, 0)",true]]' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq '[.Metrics[] | select(.ReturnData)] | length') == 1 ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -c '[.Metrics[] | select(.MetricStat) | [.Id, .MetricStat.Metric.Namespace, .MetricStat.Metric.MetricName, (.MetricStat.Metric.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .MetricStat.Period, .MetricStat.Stat]]') \
      == '[["m1","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=SUCCESS,service=mapmory-backend",3600,"Sum"],["m2","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=CLIENT_ERROR,service=mapmory-backend",3600,"Sum"],["m3","Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=SERVER_ERROR,service=mapmory-backend",3600,"Sum"]]' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-ratio | jq -c '[.Threshold, .ComparisonOperator, .EvaluationPeriods, .DatapointsToAlarm, .TreatMissingData]') \
      == '[5,"GreaterThanThreshold",1,1,"notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-db-connection-pending | jq -c '[.Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData]') \
      == '["Maximum",60,5,3,1,"GreaterThanOrEqualToThreshold","notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-disk-usage-high | jq -c '[.Dimensions[] | .Value]') \
      == '["/","i-0123456789abcdef0","nvme0n1p1","xfs"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-memory-usage-high | jq -c '[.Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData]') \
      == '["Average",300,3,3,85,"GreaterThanOrEqualToThreshold","missing"]' ]] \
  && [[ $(alarm mapmory-dev-ec2-status-check-failed | jq -r .TreatMissingData) == breaching ]] \
  && [[ $(alarm mapmory-dev-cache-bypass | jq -c '[.Namespace, .MetricName, (.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData, (.OKActions | length)]') \
      == '["Mapmory/Backend","mapmory_deployment_cache_bypass","environment=dev,service=mapmory-backend","Minimum",300,6,6,1,"GreaterThanOrEqualToThreshold","notBreaching",1]' ]] \
  && [[ $(alarm mapmory-dev-api-5xx-count | jq -c '[.Namespace, .MetricName, (.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .Statistic, .Period, .EvaluationPeriods, .DatapointsToAlarm, .Threshold, .ComparisonOperator, .TreatMissingData]') \
      == '["Mapmory/Backend","http_server_requests_seconds_count","environment=dev,outcome=SERVER_ERROR,service=mapmory-backend","Sum",600,1,1,3,"GreaterThanOrEqualToThreshold","notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-api-read-latency | jq -c '[(.Metrics[] | select(.MetricStat) | [.Id, .MetricStat.Metric.MetricName, (.MetricStat.Metric.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .MetricStat.Period, .MetricStat.Stat]), (.Metrics[] | select(.Expression) | [.Id, .Expression, .ReturnData]), .Threshold, .ComparisonOperator, .EvaluationPeriods, .DatapointsToAlarm, .TreatMissingData]') \
      == '[["fast","http_server_requests_slo_total","environment=dev,le=0.3,outcome=SUCCESS,service=mapmory-backend,slo_class=read",3600,"Sum"],["all","http_server_requests_slo_total","environment=dev,le=+Inf,outcome=SUCCESS,service=mapmory-backend,slo_class=read",3600,"Sum"],["total","FILL(all, 0)",false],["slow","IF(total >= 30, 100 * (total - FILL(fast, 0)) / total, 0)",true],5,"GreaterThanThreshold",1,1,"notBreaching"]' ]] \
  && [[ $(alarm mapmory-dev-api-write-latency | jq -c '[(.Metrics[] | select(.MetricStat) | [.Id, (.MetricStat.Metric.Dimensions | map("\(.Name)=\(.Value)") | sort | join(",")), .MetricStat.Period]), (.Metrics[] | select(.ReturnData) | .Expression), .Threshold]') \
      == '[["fast","environment=dev,le=2,outcome=SUCCESS,service=mapmory-backend,slo_class=write",3600],["all","environment=dev,le=+Inf,outcome=SUCCESS,service=mapmory-backend,slo_class=write",3600],"IF(total >= 10, 100 * (total - FILL(fast, 0)) / total, 0)",10]' ]] \
  && [[ $(jq -s '[.[] | select(.AlarmDescription | length > 0)] | length' "${WORK_DIR}/first") == 11 ]] \
  && [[ $(alarm mapmory-dev-metrics-missing | jq -r .AlarmDescription) == *"10분 넘게"* ]]; then
  pass "알람 정의가 명세와 맞다"
else
  fail "알람 정의가 명세와 맞다"
fi

# 3-1. 관리하는 알람은 모두 경보와 정상을 같은 SNS로 알린다. 콘솔 알람이 둘 다 알리고 있어서, 빠지면 정상 알림이 사라진다.
if [[ $(jq -s 'length' "${WORK_DIR}/first") == 11 ]] \
  && [[ $(jq -s '[.[] | select((.OKActions | length) > 0 and .OKActions == .AlarmActions)] | length' "${WORK_DIR}/first") == 11 ]]; then
  pass "관리하는 알람은 모두 OKActions가 비어 있지 않다"
else
  fail "관리하는 알람은 모두 OKActions가 비어 있지 않다"
fi

# 3-2. 사람을 깨우는 알람은 [깨움], 나머지는 [참고]로 시작한다(ADR 0031 3절). 깨움은 metrics-missing과 5xx 건수뿐이다.
# (외부 헬스체크 알람은 us-east-1에 있어 health-check.sh가 만든다.)
if [[ $(jq -sc '[.[] | select(.AlarmDescription | startswith("[깨움] ")) | .AlarmName] | sort' "${WORK_DIR}/first") \
      == '["mapmory-dev-api-5xx-count","mapmory-dev-metrics-missing"]' ]] \
  && [[ $(jq -s '[.[] | select(.AlarmDescription | startswith("[참고] "))] | length' "${WORK_DIR}/first") == 9 ]]; then
  pass "알람 설명은 [깨움] 또는 [참고]로 시작하고, 깨움은 ADR 표의 증상 알람뿐이다"
else
  fail "알람 설명은 [깨움] 또는 [참고]로 시작하고, 깨움은 ADR 표의 증상 알람뿐이다"
fi

# 3-3. 비율 알람의 식을 실제 값으로 계산해 본다: 요청 0건, 최소 건수 미만, 기준 바로 위, 기준과 같음.
# CloudWatch처럼 0으로 나누면 오류 대신 NaN이 되게 Decimal로 계산한다(IF가 고르지 않는 쪽의 값).
evaluate() {
  python3 -I - "$1" "$2" <<'PY'
import decimal, json, re, sys
alarm, values = json.loads(sys.argv[1]), json.loads(sys.argv[2])
decimal.setcontext(decimal.Context(traps=[]))
env = {k: decimal.Decimal(v) for k, v in values.items()}
env["FILL"] = lambda x, d: d if x is None else x
env["IF"] = lambda c, a, b: a if c else b
for query in alarm["Metrics"]:
    if "MetricStat" in query:
        env.setdefault(query["Id"], None)
    else:
        expression = re.sub(r"\b(\d+)\b", r"decimal.Decimal(\1)", query["Expression"])
        env[query["Id"]] = eval(expression, {"decimal": decimal}, env)
        if query["ReturnData"]:
            result = env[query["Id"]]
breach = {"GreaterThanThreshold": result > alarm["Threshold"]}[alarm["ComparisonOperator"]]
print(f"{float(result):g} {'ALARM' if breach else 'OK'}")
PY
}
cases=(
  'mapmory-dev-api-5xx-ratio|{"m1":0,"m2":0,"m3":0}|0 OK'
  'mapmory-dev-api-5xx-ratio|{"m1":0,"m2":0,"m3":29}|0 OK'
  'mapmory-dev-api-5xx-ratio|{"m1":27,"m2":1,"m3":2}|6.66667 ALARM'
  'mapmory-dev-api-5xx-ratio|{"m1":57,"m2":0,"m3":3}|5 OK'
  'mapmory-dev-api-read-latency|{"fast":0,"all":0}|0 OK'
  'mapmory-dev-api-read-latency|{"fast":0,"all":29}|0 OK'
  'mapmory-dev-api-read-latency|{"fast":28,"all":30}|6.66667 ALARM'
  'mapmory-dev-api-read-latency|{"fast":95,"all":100}|5 OK'
  'mapmory-dev-api-write-latency|{"fast":0,"all":0}|0 OK'
  'mapmory-dev-api-write-latency|{"fast":0,"all":9}|0 OK'
  'mapmory-dev-api-write-latency|{"fast":8,"all":10}|20 ALARM'
  'mapmory-dev-api-write-latency|{"fast":9,"all":10}|10 OK'
)
expression_failures=0
for case in "${cases[@]}"; do
  IFS='|' read -r name values expected <<< "$case"
  actual=$(evaluate "$(alarm "$name")" "$values")
  printf '     %-30s %-28s → %s\n' "${name#mapmory-dev-}" "$values" "$actual"
  [[ $actual == "$expected" ]] || { printf '     기대: %s\n' "$expected"; expression_failures=$((expression_failures + 1)); }
done
if ((expression_failures == 0)); then
  pass "비율 알람 식이 0건·최소 미만·기준 초과·기준과 같음에서 기대한 값을 낸다"
else
  fail "비율 알람 식이 0건·최소 미만·기준 초과·기준과 같음에서 기대한 값을 낸다"
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
reset_fake; FAKE_DB_ID="ACCESS_DENIED"; expect_stop "RDS 권한이 없으면 대시보드를 만들지 않는다" dashboard.sh dev --yes
if grep -qF 'AccessDenied' "${WORK_DIR}/log/stderr"; then
  pass "RDS 확인이 실패하면 aws 오류를 함께 보여 준다"
else
  fail "RDS 확인이 실패하면 aws 오류를 함께 보여 준다"
fi

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

# 6. 대시보드가 가리키는 알람은 alarms.sh가 관리하는 11개와 바꾸지 않는 3개뿐이고, 관리하는 11개는 모두 보인다.
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

# 6-1. 알람 설명이 "따옴표"로 가리키는 위젯 제목은 대시보드에 그대로 있어야 한다. 알림 메일을 받은 사람이 그 제목으로 찾는다.
widget_titles=$(jq -r '.widgets[].properties.title // empty' "${CLOUDWATCH_DIR}/dashboard.json.tmpl" | sort -u)
referenced=$(jq -r '.AlarmDescription | scan("\"([^\"]+)\"")[0]' "${WORK_DIR}/first" | sort -u)
unknown_titles=$(comm -23 <(printf '%s\n' "$referenced") <(printf '%s\n' "$widget_titles"))
if [[ -n $referenced && -z $unknown_titles ]]; then
  pass "알람 설명이 가리키는 위젯이 대시보드에 있다"
else
  printf '대시보드에 없는 위젯 제목: %s\n' "${unknown_titles:-(설명에 위젯 제목이 없음)}"
  fail "알람 설명이 가리키는 위젯이 대시보드에 있다"
fi

# 6-2. 대시보드의 "5xx 비율 (%)" 그래프는 알람과 같은 식으로 그린다. 그래야 그래프와 알람 값을 비교할 수 있다.
alarm_ratio=$(jq -r 'select(.AlarmName == "mapmory-dev-api-5xx-ratio")
  | (.Metrics | map(select(.Id == "total"))[0].Expression) as $total
  | .Metrics | map(select(.Id == "ratio"))[0].Expression | gsub("total"; "(" + $total + ")")' "${WORK_DIR}/first")
widget_ratio=$(jq -r '.widgets[] | select(.properties.title == "5xx 비율 (%)") | .properties.metrics[0][0].expression' \
  "${CLOUDWATCH_DIR}/dashboard.json.tmpl")
# 식이 같아도 m1~m3가 가리키는 지표가 다르면 다른 값이 된다. 그래서 id마다 지표 이름·outcome·통계·기간도 비교한다.
alarm_inputs=$(jq -c 'select(.AlarmName == "mapmory-dev-api-5xx-ratio")
  | [.Metrics[] | select(.MetricStat) | [.Id, .MetricStat.Metric.MetricName,
      (.MetricStat.Metric.Dimensions[] | select(.Name == "outcome") | .Value), .MetricStat.Stat, .MetricStat.Period]] | sort' "${WORK_DIR}/first")
widget_inputs=$(jq -c '[.widgets[] | select(.properties.title == "5xx 비율 (%)") | .properties.metrics[]
  | select(.[0] == "Mapmory/Backend") | [.[-1].id, .[1], .[7], .[-1].stat, .[-1].period]] | sort' \
  "${CLOUDWATCH_DIR}/dashboard.json.tmpl")
if [[ -n $alarm_ratio && $alarm_ratio == "$widget_ratio" && $alarm_inputs == "$widget_inputs" ]]; then
  pass "대시보드 5xx 비율 그래프가 알람과 같은 식이다"
else
  printf '알람: %s %s\n위젯: %s %s\n' "$alarm_ratio" "$alarm_inputs" "$widget_ratio" "$widget_inputs"
  fail "대시보드 5xx 비율 그래프가 알람과 같은 식이다"
fi

# 6-3. 캐시 모드 그래프가 알람과 같은 지표·통계(Minimum)를 보여 준다. 알람 설명이 이 그래프를 가리킨다.
mode_widget=$(jq -c '.widgets[] | select(.properties.title == "캐시 모드 (BYPASS=1, NORMAL=0)")' "${CLOUDWATCH_DIR}/dashboard.json.tmpl")
# shellcheck disable=SC2016 # 템플릿 자리표시자 ${ENV}를 그대로 비교한다.
if [[ -n $mode_widget ]] \
  && [[ $(jq -c '.properties.metrics[0][0:6]' <<< "$mode_widget") == '["Mapmory/Backend","mapmory_deployment_cache_bypass","environment","${ENV}","service","mapmory-backend"]' ]] \
  && [[ $(jq -c '[.properties.metrics[][-1].stat] | sort' <<< "$mode_widget") == '["Maximum","Minimum"]' ]]; then
  pass "대시보드에 알람과 같은 지표로 캐시 모드 그래프가 있다"
else
  fail "대시보드에 알람과 같은 지표로 캐시 모드 그래프가 있다"
fi

# 6-4. 캐시 적중률은 hit+miss가 0이면 빈칸이다. 0/0을 그리면 NaN이 되어 경고 표시가 뜬다.
ratio_expressions=$(jq -r '.widgets[] | select(.properties.title == "캐시 적중률 (%)") | .properties.metrics[][0] | select(.visible != false) | .expression' \
  "${CLOUDWATCH_DIR}/dashboard.json.tmpl")
if [[ $(grep -c . <<< "$ratio_expressions") == 2 ]] \
  && ! grep -vqE '^IF\(FILL\(h[12], 0\) \+ FILL\(m[12], 0\) > 0, 100 \* FILL\(h[12], 0\) / \(FILL\(h[12], 0\) \+ FILL\(m[12], 0\)\)\)$' <<< "$ratio_expressions"; then
  pass "캐시 적중률은 분모가 0이면 값을 내지 않는다"
else
  printf '%s\n' "$ratio_expressions"
  fail "캐시 적중률은 분모가 0이면 값을 내지 않는다"
fi

# 6-5. EMF 허용 목록에 캐시 모드 지표가 service·environment 차원으로 들어 있다. 없으면 CloudWatch에 올라가지 않는다.
if jq -e '.logs.metrics_collected.prometheus.emf_processor.metric_declaration[]
    | select(.metric_selectors == ["^mapmory_deployment_cache_bypass$"] and .dimensions == [["service", "environment"]])' \
    "${CLOUDWATCH_DIR}/agent.json.tmpl" > /dev/null; then
  pass "Agent EMF 허용 목록에 캐시 모드 지표가 있다"
else
  fail "Agent EMF 허용 목록에 캐시 모드 지표가 있다"
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
