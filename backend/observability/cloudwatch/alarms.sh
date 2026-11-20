#!/usr/bin/env bash
# CloudWatch 알람을 코드에 적힌 조건으로 만들거나 덮어쓴다. 여러 번 실행해도 결과가 같다.
#
#   ./alarms.sh <dev|prod> [--dry-run] [--yes] [--name-tag <EC2 Name 태그>]
#
# 알림 대상(SNS)은 mapmory-<env>-application-error 알람의 AlarmActions에서 읽는다.
# application-error, api-5xx-rate, rds-free-storage-low는 바꾸지 않고 지금 조건만 표로 출력한다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

# EC2 알람 조건. 2026-11-20에 콘솔에 있던 조건(dev·prod 같음)을 옮겼다.
# 순서: 통계 기간(초) 평가_횟수 경보에_필요한_횟수 비교 임계값 데이터_없음
# status-check만 데이터 없음을 경보로 둔다. 인스턴스가 멈추면 상태 검사 값도 끊기기 때문이다.
EC2_CPU_CONDITION=(Average 300 3 3 GreaterThanOrEqualToThreshold 80 missing)
EC2_MEMORY_CONDITION=(Average 300 3 3 GreaterThanOrEqualToThreshold 85 missing)
EC2_DISK_CONDITION=(Average 300 3 3 GreaterThanOrEqualToThreshold 80 missing)
EC2_STATUS_CHECK_CONDITION=(Maximum 300 1 1 GreaterThanOrEqualToThreshold 1 breaching)

UNMANAGED_ALARMS=(application-error api-5xx-rate rds-free-storage-low)

ACTIONS_JSON=""

# 알림 대상은 application-error 알람과 같게 맞춘다. ARN은 실행할 때만 메모리에 있고 저장소에 남지 않는다.
read_alarm_actions() {
  local source_alarm="mapmory-${ENV_NAME}-application-error" actions
  actions=$(aws cloudwatch describe-alarms --region "$REGION" --alarm-names "$source_alarm" \
    --query 'MetricAlarms[0].AlarmActions' --output json)
  if ! jq -e 'type == "array" and length > 0 and all(.[]; startswith("arn:aws:sns:"))' <<< "$actions" >/dev/null; then
    die "${source_alarm} 알람이 없거나 SNS 알림 대상이 비어 있습니다."
  fi
  ACTIONS_JSON=$(jq -c . <<< "$actions")
}

# SNS ARN 목록을 주제 이름만 남겨 보여 준다. 계정 번호는 출력하지 않는다.
topic_names() {
  jq -r 'if length == 0 then "(없음)" else map(split(":")[-1]) | join(", ") end' <<< "$1"
}

# 관리하는 알람의 지금 알림 대상을 보여 준다. put-metric-alarm은 알림 대상까지 덮어쓰므로, 적용 전에 무엇이 바뀌는지 본다.
report_current_actions() {
  local current
  current=$(aws cloudwatch describe-alarms --region "$REGION" --alarm-names "$@" --output json)
  log "관리하는 알람의 지금 알림 대상(적용하면 위 SNS로 바뀐다):"
  jq -r --argjson names "$(jq -nc '$ARGS.positional' --args "$@")" '
    (.MetricAlarms // []) as $alarms
    | $names[]
    | . as $name
    | ($alarms | map(select(.AlarmName == $name)) | first) as $alarm
    | if $alarm == null then "  \($name): 없음(새로 만든다)"
      else "  \($name): 경보 → \($alarm.AlarmActions | map(split(":")[-1]) | join(", ") | if . == "" then "(없음)" else . end), 정상 → \($alarm.OKActions | map(split(":")[-1]) | join(", ") | if . == "" then "(없음)" else . end)"
      end' <<< "$current" >&2
}

# dimensions k1 v1 k2 v2 ... → [{"Name":k1,"Value":v1}, ...]
dimensions() {
  jq -nc '$ARGS.positional as $a | [range(0; $a | length; 2) as $i | {Name: $a[$i], Value: $a[$i + 1]}]' --args "$@"
}

# 지표 하나로 판단하는 알람. 경보와 정상 모두 같은 SNS로 알린다.
# single_metric_alarm 이름 설명 네임스페이스 지표 차원JSON 통계 기간 평가 필요 비교 임계값 데이터없음
single_metric_alarm() {
  jq -nc \
    --arg name "$1" --arg description "$2" --arg namespace "$3" --arg metric "$4" \
    --argjson dimensions "$5" \
    --arg statistic "$6" --argjson period "$7" --argjson evaluation "$8" --argjson datapoints "$9" \
    --arg comparison "${10}" --argjson threshold "${11}" --arg missing "${12}" \
    --argjson actions "$ACTIONS_JSON" '
    {
      AlarmName: $name,
      AlarmDescription: $description,
      ActionsEnabled: true,
      AlarmActions: $actions,
      OKActions: $actions,
      InsufficientDataActions: [],
      Namespace: $namespace,
      MetricName: $metric,
      Dimensions: $dimensions,
      Statistic: $statistic,
      Period: $period,
      EvaluationPeriods: $evaluation,
      DatapointsToAlarm: $datapoints,
      ComparisonOperator: $comparison,
      Threshold: $threshold,
      TreatMissingData: $missing
    }'
}

# 조건 배열(통계 기간 평가 필요 비교 임계값 데이터없음)을 받아 EC2 알람을 만든다.
ec2_alarm() {
  local name=$1 description=$2 namespace=$3 metric=$4 dims=$5
  shift 5
  (($# == 7)) || die "${name}의 조건은 값 7개여야 합니다."
  single_metric_alarm "$name" "$description" "$namespace" "$metric" "$dims" "$@"
}

api_5xx_ratio_alarm() {
  local dims_base
  dims_base=$(dimensions environment "$ENV_NAME" service "$SERVICE")
  jq -nc --arg env "$ENV_NAME" --argjson base "$dims_base" --argjson actions "$ACTIONS_JSON" '
    def outcome($id; $outcome):
      {
        Id: $id,
        MetricStat: {
          Metric: {
            Namespace: "Mapmory/Backend",
            MetricName: "http_server_requests_seconds_count",
            Dimensions: ($base + [{Name: "outcome", Value: $outcome}])
          },
          Period: 60,
          Stat: "Sum"
        },
        ReturnData: false
      };
    {
      AlarmName: "mapmory-\($env)-api-5xx-ratio",
      AlarmDescription: "5xx가 요청의 5%를 넘는다(분당 10건 이상일 때만 판단). 먼저 대시보드 \"버전별 5xx (분당)\"로 배포 직후인지 보고, 로그 위젯 \"WARN / ERROR — event별 건수\"에서 errorCode를 본다.",
      ActionsEnabled: true,
      AlarmActions: $actions,
      OKActions: $actions,
      InsufficientDataActions: [],
      Metrics: [
        outcome("m1"; "SUCCESS"),
        outcome("m2"; "CLIENT_ERROR"),
        outcome("m3"; "SERVER_ERROR"),
        {Id: "total", Expression: "FILL(m1, 0) + FILL(m2, 0) + FILL(m3, 0)", Label: "요청 수", ReturnData: false},
        {Id: "ratio", Expression: "IF(total >= 10, 100 * FILL(m3, 0) / total, 0)", Label: "5xx 비율 (%)", ReturnData: true}
      ],
      EvaluationPeriods: 5,
      DatapointsToAlarm: 3,
      ComparisonOperator: "GreaterThanThreshold",
      Threshold: 5,
      TreatMissingData: "notBreaching"
    }'
}

build_alarms() {
  local instance_id=$1 device=$2 fstype=$3 pool_dims
  pool_dims=$(dimensions environment "$ENV_NAME" service "$SERVICE" pool HikariPool-1)

  single_metric_alarm "mapmory-${ENV_NAME}-metrics-missing" \
    "앱 지표가 10분 넘게 들어오지 않는다. 먼저 EC2에서 amazon-cloudwatch-agent 상태와 활성 슬롯의 /actuator/prometheus 응답을 본다." \
    Mapmory/Backend hikaricp_connections_max "$pool_dims" \
    SampleCount 300 2 2 LessThanThreshold 1 breaching

  api_5xx_ratio_alarm

  single_metric_alarm "mapmory-${ENV_NAME}-db-connection-pending" \
    "DB 커넥션을 기다리는 요청이 이어진다. 먼저 대시보드 \"DB 커넥션 풀\"의 active·max와 RDS CPU·연결 수, \"내부 작업 p95\"를 본다." \
    Mapmory/Backend hikaricp_connections_pending "$pool_dims" \
    Maximum 60 5 3 GreaterThanOrEqualToThreshold 1 notBreaching

  ec2_alarm "mapmory-${ENV_NAME}-ec2-cpu-usage-high" \
    "EC2 CPU 사용률이 높다. 먼저 대시보드 \"앱 CPU & GC\"로 JVM 때문인지 보고, 요청 수가 함께 늘었는지 본다." \
    AWS/EC2 CPUUtilization "$(dimensions InstanceId "$instance_id")" "${EC2_CPU_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-memory-usage-high" \
    "EC2 메모리 사용률이 높다. 먼저 배포 중이라 두 슬롯이 함께 떠 있는지, 대시보드 \"JVM Old Gen\"이 max에 붙었는지 본다." \
    Mapmory/EC2 mem_used_percent "$(dimensions InstanceId "$instance_id")" "${EC2_MEMORY_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-disk-usage-high" \
    "루트 디스크가 차고 있다. 먼저 로그 디렉터리(앱·Nginx)와 남아 있는 이전 릴리스의 크기를 본다." \
    Mapmory/EC2 disk_used_percent \
    "$(dimensions path / InstanceId "$instance_id" device "$device" fstype "$fstype")" "${EC2_DISK_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-status-check-failed" \
    "EC2 상태 검사가 실패했거나 값이 들어오지 않는다. 먼저 EC2 콘솔의 상태 검사 탭에서 시스템·인스턴스 중 어느 쪽인지 본다." \
    AWS/EC2 StatusCheckFailed "$(dimensions InstanceId "$instance_id")" "${EC2_STATUS_CHECK_CONDITION[@]}"
}

report_unmanaged() {
  local -a names=()
  local name
  for name in "${UNMANAGED_ALARMS[@]}"; do
    names+=("mapmory-${ENV_NAME}-${name}")
  done
  log "이 스크립트가 바꾸지 않는 알람의 지금 조건:"
  aws cloudwatch describe-alarms --region "$REGION" --alarm-names "${names[@]}" \
    --query 'MetricAlarms[].{Name: AlarmName, Metric: MetricName, Expression: Metrics[?ReturnData].Expression | [0], Stat: Statistic, Period: Period, Eval: EvaluationPeriods, Datapoints: DatapointsToAlarm, Comparison: ComparisonOperator, Threshold: Threshold, Missing: TreatMissingData}' \
    --output table >&2
}

main() {
  parse_args "$@"
  require_tools

  local account instance_id disk device fstype alarm
  local -a alarms=()
  account=$(account_id)
  instance_id=$(find_instance_id)
  disk=$(find_root_disk "$instance_id")
  read -r device fstype <<< "$disk"
  read_alarm_actions

  while IFS= read -r alarm; do
    alarms+=("$alarm")
  done < <(build_alarms "$instance_id" "$device" "$fstype")
  ((${#alarms[@]} == 7)) || die "알람 정의를 만들지 못했습니다."

  local -a alarm_names=()
  for alarm in "${alarms[@]}"; do
    alarm_names+=("$(jq -r .AlarmName <<< "$alarm")")
  done
  report_current_actions "${alarm_names[@]}"

  confirm "$account" \
    "EC2: Name=${NAME_TAG} (${instance_id}), 루트 디스크 ${device}/${fstype}" \
    "경보·정상 알림 대상: mapmory-${ENV_NAME}-application-error와 같은 SNS($(topic_names "$ACTIONS_JSON"))" \
    "만들거나 덮어쓸 알람: ${alarm_names[*]}"

  for alarm in "${alarms[@]}"; do
    if [[ $DRY_RUN == true ]]; then
      jq . <<< "$alarm"
    else
      aws cloudwatch put-metric-alarm --region "$REGION" --cli-input-json "$alarm"
      log "적용: $(jq -r .AlarmName <<< "$alarm")"
    fi
  done

  report_unmanaged
  if [[ $DRY_RUN == false ]]; then
    log "10분 뒤 README의 확인 명령으로 모든 알람이 OK인지 봅니다."
  fi
}

main "$@"
