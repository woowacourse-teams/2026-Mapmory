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

# SLO 알람 기준(ADR 0031 3절). 숫자를 바꾸면 ADR 표도 함께 고친다.
# 판단 구간은 슬라이딩이다. 매분 지난 구간 전체를 다시 본다(1시간 알람은 매분 최근 60분).
SLO_5XX_COUNT_WINDOW=600          # 5xx 건수: 10분 동안
SLO_5XX_COUNT_MIN=3               #   3건 이상
SLO_RATIO_WINDOW=3600             # 비율 알람: 1시간 동안
SLO_5XX_RATIO_MIN_REQUESTS=30     # 5xx 비율: 요청 30건 이상일 때만
SLO_5XX_RATIO_PERCENT=5           #   5% 초과(허용 0.5%의 소진 속도 10)
SLO_READ_LE=0.3                   # 조회 지연: 0.3초(SLO 카운터의 le 값)
SLO_READ_MIN_REQUESTS=30          #   성공 요청 30건 이상일 때만
SLO_READ_SLOW_PERCENT=5           #   기준을 넘긴 비율 5% 초과
SLO_WRITE_LE=2                    # 저장 지연: 2초
SLO_WRITE_MIN_REQUESTS=10         #   성공 요청 10건 이상일 때만
SLO_WRITE_SLOW_PERCENT=10         #   기준을 넘긴 비율 10% 초과

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

# 알람 설명은 [깨움] 또는 [참고]로 시작한다(ADR 0031 3절). 알림 채널은 아직 하나라서, 사람을 깨워야 하는 알람인지는 이 표시로만 구분한다.
#   [깨움]: 증상(서비스가 안 되거나 사용자가 오류를 본다). [참고]: 오류 예산이 새는 신호나 원인.

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

# 최근 1시간 5xx 비율. 기존 알람은 분당 10건 이상일 때만 계산해, 평균 분당 0.5건인 prod에서는 사실상 울릴 수 없었다.
# 이름은 그대로 둔다. 대시보드·Discord 안내 문구가 이 이름을 가리키고, 알람 기록도 이어진다.
# 분모는 http_server_requests_seconds_count의 outcome 차원(SUCCESS·CLIENT_ERROR·SERVER_ERROR, /api/v1)이다.
# SLO 카운터로 전체 요청을 만들면 slo_class 4 × outcome 3 = 지표 12개라 알람의 10개 한도를 넘는다.
api_5xx_ratio_alarm() {
  local dims_base
  dims_base=$(dimensions environment "$ENV_NAME" service "$SERVICE")
  jq -nc --arg env "$ENV_NAME" --argjson base "$dims_base" --argjson actions "$ACTIONS_JSON" \
    --argjson window "$SLO_RATIO_WINDOW" --argjson min "$SLO_5XX_RATIO_MIN_REQUESTS" \
    --argjson percent "$SLO_5XX_RATIO_PERCENT" '
    def outcome($id; $outcome):
      {
        Id: $id,
        MetricStat: {
          Metric: {
            Namespace: "Mapmory/Backend",
            MetricName: "http_server_requests_seconds_count",
            Dimensions: ($base + [{Name: "outcome", Value: $outcome}])
          },
          Period: $window,
          Stat: "Sum"
        },
        ReturnData: false
      };
    {
      AlarmName: "mapmory-\($env)-api-5xx-ratio",
      AlarmDescription: "[참고] 최근 1시간 5xx가 요청의 \($percent)%를 넘는다(요청 \($min)건 이상일 때만, 오류 예산 소진 속도 10). 먼저 대시보드 \"버전별 5xx (분당)\"로 배포 직후인지 보고, 로그 위젯 \"WARN / ERROR — event별 건수\"에서 errorCode를 본다.",
      ActionsEnabled: true,
      AlarmActions: $actions,
      OKActions: $actions,
      InsufficientDataActions: [],
      Metrics: [
        outcome("m1"; "SUCCESS"),
        outcome("m2"; "CLIENT_ERROR"),
        outcome("m3"; "SERVER_ERROR"),
        {Id: "total", Expression: "FILL(m1, 0) + FILL(m2, 0) + FILL(m3, 0)", Label: "요청 수", ReturnData: false},
        {Id: "ratio", Expression: "IF(total >= \($min), 100 * FILL(m3, 0) / total, 0)", Label: "5xx 비율 (%)", ReturnData: true}
      ],
      EvaluationPeriods: 1,
      DatapointsToAlarm: 1,
      ComparisonOperator: "GreaterThanThreshold",
      Threshold: $percent,
      TreatMissingData: "notBreaching"
    }'
}

# 지연 SLO 알람: 최근 1시간 성공 요청 중 기준 시간을 넘긴 비율.
# SLO 카운터는 누적 le라, 기준 안에 끝난 수는 Sum(le=기준), 전체는 Sum(le=+Inf)다. 넘긴 비율 = 1 - 둘의 비.
# 성공(SUCCESS)만 센다. 실패는 5xx 알람이 맡는다(ADR 0031 1절).
# 기동할 때 read·write 카운터를 0으로 미리 만들므로 요청이 없어도 0이 들어온다. 그때는 최소 건수 미만이라 0이다.
# latency_slo_alarm 이름 설명 slo_class le 최소_요청 기준_비율
latency_slo_alarm() {
  local dims_base
  dims_base=$(dimensions environment "$ENV_NAME" service "$SERVICE" slo_class "$3" outcome SUCCESS)
  jq -nc --arg name "$1" --arg description "$2" --argjson base "$dims_base" --arg le "$4" \
    --argjson min "$5" --argjson percent "$6" --argjson window "$SLO_RATIO_WINDOW" --argjson actions "$ACTIONS_JSON" '
    def counter($id; $le):
      {
        Id: $id,
        MetricStat: {
          Metric: {
            Namespace: "Mapmory/Backend",
            MetricName: "http_server_requests_slo_total",
            Dimensions: ($base + [{Name: "le", Value: $le}])
          },
          Period: $window,
          Stat: "Sum"
        },
        ReturnData: false
      };
    {
      AlarmName: $name,
      AlarmDescription: $description,
      ActionsEnabled: true,
      AlarmActions: $actions,
      OKActions: $actions,
      InsufficientDataActions: [],
      Metrics: [
        counter("fast"; $le),
        counter("all"; "+Inf"),
        {Id: "total", Expression: "FILL(all, 0)", Label: "성공 요청 수", ReturnData: false},
        {Id: "slow", Expression: "IF(total >= \($min), 100 * (total - FILL(fast, 0)) / total, 0)", Label: "기준을 넘긴 비율 (%)", ReturnData: true}
      ],
      EvaluationPeriods: 1,
      DatapointsToAlarm: 1,
      ComparisonOperator: "GreaterThanThreshold",
      Threshold: $percent,
      TreatMissingData: "notBreaching"
    }'
}

build_alarms() {
  local instance_id=$1 device=$2 fstype=$3 pool_dims
  pool_dims=$(dimensions environment "$ENV_NAME" service "$SERVICE" pool HikariPool-1)

  single_metric_alarm "mapmory-${ENV_NAME}-metrics-missing" \
    "[깨움] 앱 지표가 10분 넘게 들어오지 않는다. 먼저 EC2에서 amazon-cloudwatch-agent 상태와 활성 슬롯의 /actuator/prometheus 응답을 본다." \
    Mapmory/Backend hikaricp_connections_max "$pool_dims" \
    SampleCount 300 2 2 LessThanThreshold 1 breaching

  single_metric_alarm "mapmory-${ENV_NAME}-api-5xx-count" \
    "[깨움] 최근 10분 5xx가 ${SLO_5XX_COUNT_MIN}건 이상이다. 저트래픽이라 비율 대신 건수로 본다. 먼저 대시보드 \"버전별 5xx (분당)\"로 배포 직후인지 보고, 로그 위젯 \"WARN / ERROR — event별 건수\"에서 errorCode를 본다." \
    Mapmory/Backend http_server_requests_seconds_count \
    "$(dimensions environment "$ENV_NAME" service "$SERVICE" outcome SERVER_ERROR)" \
    Sum "$SLO_5XX_COUNT_WINDOW" 1 1 GreaterThanOrEqualToThreshold "$SLO_5XX_COUNT_MIN" notBreaching

  api_5xx_ratio_alarm

  latency_slo_alarm "mapmory-${ENV_NAME}-api-read-latency" \
    "[참고] 최근 1시간 조회 API(지도 요약·목록·상세·Presigned URL) 성공 요청 중 ${SLO_READ_LE}초를 넘긴 비율이 ${SLO_READ_SLOW_PERCENT}%를 넘는다(${SLO_READ_MIN_REQUESTS}건 이상일 때만). 먼저 대시보드 \"Map & Upload API p95\"와 \"Travel Record API p95\"에서 어느 API인지 보고, \"DB 커넥션 풀\"과 \"RDS CPU\"를 본다." \
    read "$SLO_READ_LE" "$SLO_READ_MIN_REQUESTS" "$SLO_READ_SLOW_PERCENT"

  latency_slo_alarm "mapmory-${ENV_NAME}-api-write-latency" \
    "[참고] 최근 1시간 기록 생성·수정 성공 요청 중 ${SLO_WRITE_LE}초를 넘긴 비율이 ${SLO_WRITE_SLOW_PERCENT}%를 넘는다(${SLO_WRITE_MIN_REQUESTS}건 이상일 때만). 먼저 대시보드 \"Travel Record API p95\"와 \"내부 작업 p95\"에서 S3 확인이나 DB 중 어디가 느린지 본다." \
    write "$SLO_WRITE_LE" "$SLO_WRITE_MIN_REQUESTS" "$SLO_WRITE_SLOW_PERCENT"

  # 슬롯은 BYPASS로 시작하고 배포가 끝나면 스크립트가 NORMAL로 켠다. 30분은 배포 관찰(5분)보다 넉넉하게 잡았다.
  # Minimum이라 모든 프로세스가 한 기간 내내 BYPASS일 때만 1이다. 배포 중 두 슬롯이 함께 떠 있어도 새 슬롯이 켜지면 0이 된다.
  # 지표가 끊긴 것은 metrics-missing이 맡으므로 데이터 없음은 정상으로 본다.
  single_metric_alarm "mapmory-${ENV_NAME}-cache-bypass" \
    "[참고] 캐시가 30분 넘게 BYPASS다. 응답은 맞지만 지도·목록 조회가 모두 DB로 간다. 먼저 대시보드 \"캐시 모드 (BYPASS=1, NORMAL=0)\"를 보고, EC2에서 활성 슬롯 관리 포트의 /actuator/deploymentcache로 모드를 확인한 뒤 backend/docs/deployment-cache-control.md 4단계의 수동 명령으로 켠다." \
    Mapmory/Backend mapmory_deployment_cache_bypass "$(dimensions environment "$ENV_NAME" service "$SERVICE")" \
    Minimum 300 6 6 GreaterThanOrEqualToThreshold 1 notBreaching

  single_metric_alarm "mapmory-${ENV_NAME}-db-connection-pending" \
    "[참고] DB 커넥션을 기다리는 요청이 이어진다. 먼저 대시보드 \"DB 커넥션 풀\"의 active·max와 RDS CPU·연결 수, \"내부 작업 p95\"를 본다." \
    Mapmory/Backend hikaricp_connections_pending "$pool_dims" \
    Maximum 60 5 3 GreaterThanOrEqualToThreshold 1 notBreaching

  ec2_alarm "mapmory-${ENV_NAME}-ec2-cpu-usage-high" \
    "[참고] EC2 CPU 사용률이 높다. 먼저 대시보드 \"앱 CPU & GC\"로 JVM 때문인지 보고, 요청 수가 함께 늘었는지 본다." \
    AWS/EC2 CPUUtilization "$(dimensions InstanceId "$instance_id")" "${EC2_CPU_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-memory-usage-high" \
    "[참고] EC2 메모리 사용률이 높다. 먼저 배포 중이라 두 슬롯이 함께 떠 있는지, 대시보드 \"JVM Old Gen\"이 max에 붙었는지 본다." \
    Mapmory/EC2 mem_used_percent "$(dimensions InstanceId "$instance_id")" "${EC2_MEMORY_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-disk-usage-high" \
    "[참고] 루트 디스크가 차고 있다. 먼저 로그 디렉터리(앱·Nginx)와 남아 있는 이전 릴리스의 크기를 본다." \
    Mapmory/EC2 disk_used_percent \
    "$(dimensions path / InstanceId "$instance_id" device "$device" fstype "$fstype")" "${EC2_DISK_CONDITION[@]}"

  ec2_alarm "mapmory-${ENV_NAME}-ec2-status-check-failed" \
    "[참고] EC2 상태 검사가 실패했거나 값이 들어오지 않는다. 먼저 EC2 콘솔의 상태 검사 탭에서 시스템·인스턴스 중 어느 쪽인지 본다." \
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
  ((${#alarms[@]} == 11)) || die "알람 정의를 만들지 못했습니다."

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
