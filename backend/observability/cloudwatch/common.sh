# shellcheck shell=bash
# alarms.sh와 dashboard.sh가 함께 쓰는 함수. 직접 실행하지 않고 source한다.
#
# 공개 저장소라 계정 번호, ARN, 인스턴스 ID는 저장소에 두지 않는다. 실행할 때마다 AWS에서 읽는다.

readonly REGION=ap-northeast-2
# shellcheck disable=SC2034 # alarms.sh가 쓴다.
readonly SERVICE=mapmory-backend

ENV_NAME=""
DRY_RUN=false
ASSUME_YES=false
NAME_TAG=""

log() {
  printf '[cloudwatch] %s\n' "$*" >&2
}

die() {
  log "중단: $*"
  exit 1
}

usage() {
  printf '사용법: %s <dev|prod> [--dry-run] [--yes] [--name-tag <EC2 Name 태그>]\n' "$(basename "$0")" >&2
  printf '  --dry-run   AWS에 쓰지 않고 만들 내용만 출력한다(읽기는 한다).\n' >&2
  printf '  --yes       실행 전 확인을 묻지 않는다.\n' >&2
  printf '  --name-tag  EC2를 찾을 Name 태그. 기본값은 ec2-mapmory-<env>-new.\n' >&2
}

parse_args() {
  while (($# > 0)); do
    case "$1" in
      dev|prod)
        [[ -z $ENV_NAME ]] || { usage; die "환경은 하나만 줄 수 있습니다."; }
        ENV_NAME=$1
        ;;
      --dry-run) DRY_RUN=true ;;
      --yes) ASSUME_YES=true ;;
      --name-tag)
        (($# >= 2)) || { usage; die "--name-tag 뒤에 값이 필요합니다."; }
        NAME_TAG=$2
        shift
        ;;
      -h|--help) usage; exit 0 ;;
      *) usage; die "알 수 없는 인자: $1" ;;
    esac
    shift
  done
  [[ -n $ENV_NAME ]] || { usage; die "환경(dev 또는 prod)을 주세요."; }
  [[ -n $NAME_TAG ]] || NAME_TAG="ec2-mapmory-${ENV_NAME}-new"
}

require_tools() {
  command -v aws >/dev/null 2>&1 || die "aws CLI가 없습니다."
  command -v jq >/dev/null 2>&1 || die "jq가 없습니다. CloudShell이면 'sudo dnf install -y jq'로 설치합니다."
}

account_id() {
  local account
  account=$(aws sts get-caller-identity --region "$REGION" --query Account --output text)
  [[ $account =~ ^[0-9]{12}$ ]] || die "계정 번호를 읽지 못했습니다: '${account}'"
  printf '%s\n' "$account"
}

# Name 태그가 같고 실행 중인 인스턴스가 정확히 하나일 때만 그 ID를 돌려준다.
# 0개나 2개 이상이면 어느 서버에 알람을 걸어야 할지 모르므로 멈춘다.
find_instance_id() {
  local output names
  local -a ids=()
  output=$(aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Name,Values=${NAME_TAG}" "Name=instance-state-name,Values=running" \
    --query 'Reservations[].Instances[].InstanceId' --output text)
  read -r -a ids <<< "$output"
  if ((${#ids[@]} == 1)); then
    printf '%s\n' "${ids[0]}"
    return 0
  fi
  if ((${#ids[@]} == 0)); then
    # shellcheck disable=SC2016 # JMESPath의 역따옴표다.
    names=$(aws ec2 describe-instances --region "$REGION" \
      --filters "Name=instance-state-name,Values=running" \
      --query 'Reservations[].Instances[].Tags[?Key==`Name`].Value[]' --output text)
    log "실행 중인 인스턴스의 Name 태그: ${names:-(없음)}"
    die "Name 태그가 '${NAME_TAG}'이고 실행 중인 인스턴스가 없습니다. --name-tag로 지정하세요."
  fi
  die "Name 태그가 '${NAME_TAG}'이고 실행 중인 인스턴스가 ${#ids[@]}개입니다. 하나만 남기거나 --name-tag로 좁히세요."
}

# 루트(/) 디스크 지표의 device와 fstype을 지금 들어오는 지표에서 찾는다. 서버마다 값이 달라서 저장소에 두지 않는다.
# list-metrics는 최근 2주 안에 값이 들어온 지표만 보여 준다. 새 서버는 Agent가 지표를 보낸 뒤에 실행한다.
# 출력: "<device> <fstype>"
find_root_disk() {
  local instance_id=$1 metrics count device fstype
  metrics=$(aws cloudwatch list-metrics --region "$REGION" \
    --namespace Mapmory/EC2 --metric-name disk_used_percent \
    --dimensions "Name=InstanceId,Value=${instance_id}" "Name=path,Value=/" --output json)
  count=$(jq '.Metrics | length' <<< "$metrics")
  ((count == 1)) || die "루트 디스크 지표(disk_used_percent, path=/)가 ${count}개입니다. Agent가 지표를 보내는지 확인하세요."
  device=$(jq -r '.Metrics[0].Dimensions[] | select(.Name == "device") | .Value' <<< "$metrics")
  fstype=$(jq -r '.Metrics[0].Dimensions[] | select(.Name == "fstype") | .Value' <<< "$metrics")
  [[ -n $device && -n $fstype ]] || die "루트 디스크 지표에 device 또는 fstype 차원이 없습니다."
  printf '%s %s\n' "$device" "$fstype"
}

# 대상 계정·리전과 바꿀 내용을 보여 주고 확인을 받는다. --dry-run은 아무것도 바꾸지 않으므로 묻지 않는다.
confirm() {
  local answer=""
  log "계정: $1"
  log "리전: ${REGION}"
  log "환경: ${ENV_NAME}"
  shift
  local line
  for line in "$@"; do
    log "$line"
  done
  if [[ $DRY_RUN == true ]]; then
    log "--dry-run이라 AWS에 쓰지 않습니다."
    return 0
  fi
  [[ $ASSUME_YES == true ]] && return 0
  printf '[cloudwatch] 이대로 적용할까요? [y/N] ' >&2
  read -r answer || true
  [[ $answer == y || $answer == Y ]] || die "적용하지 않았습니다."
}
