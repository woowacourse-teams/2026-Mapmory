#!/usr/bin/env bash
# 저장소의 CloudWatch Agent 템플릿을 서버 환경(dev/prod)에 맞게 렌더링해 적용한다.
# 서버 설정과 같으면 아무것도 하지 않고, 다르면 기존 설정을 백업한 뒤 fetch-config로 교체한다.
# fetch-config는 amazon-cloudwatch-agent.d의 기존 설정을 지우고 이 설정 하나만 남기므로
# 예전에 append-config로 붙인 Blue/Green 로그 설정도 함께 정리된다.
#
# 모니터링 설정 때문에 배포가 막히면 안 되므로 어떤 단계가 실패해도 WARN만 남기고 0으로 끝낸다.
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly SCRIPT_DIR
readonly TEMPLATE_DIR=${MAPMORY_CLOUDWATCH_TEMPLATE_DIR:-$SCRIPT_DIR/../observability/cloudwatch}
readonly ENV_FILE=${MAPMORY_ENV_FILE:-/etc/mapmory.env}
readonly AGENT_ROOT=${CLOUDWATCH_AGENT_ROOT:-/opt/aws/amazon-cloudwatch-agent}
readonly BACKUP_ROOT=${MAPMORY_CLOUDWATCH_BACKUP_ROOT:-/var/lib/mapmory-deployments/cloudwatch-backups}
readonly AGENT_CTL="$AGENT_ROOT/bin/amazon-cloudwatch-agent-ctl"
readonly PROMETHEUS_CONFIG="$AGENT_ROOT/var/prometheus.yaml"
readonly AGENT_CONFIG_DIR="$AGENT_ROOT/etc/amazon-cloudwatch-agent.d"
# fetch-config -c file:<경로>/mapmory.json은 설정을 amazon-cloudwatch-agent.d/file_mapmory.json으로 복사한다.
readonly CONFIG_NAME=mapmory
readonly APPLIED_AGENT_CONFIG="$AGENT_CONFIG_DIR/file_$CONFIG_NAME.json"
readonly BACKUPS_TO_KEEP=10

log() {
  echo "[cloudwatch-agent] $*"
}

warn() {
  echo "[cloudwatch-agent] WARN: $*" >&2
}

# /etc/mapmory.env는 systemd EnvironmentFile 형식이고 비밀값이 섞여 있어 source하지 않는다.
# SPRING_PROFILES_ACTIVE 줄만 읽고, 허용 값은 validate-spring-profile.sh와 같이 dev, prod로 제한한다.
read_environment() {
  [[ -r $ENV_FILE ]] || { warn "$ENV_FILE을 읽을 수 없습니다."; return 1; }

  local profile
  profile=$(sed -n -E 's/^[[:space:]]*(export[[:space:]]+)?SPRING_PROFILES_ACTIVE[[:space:]]*=[[:space:]]*//p' "$ENV_FILE" | tail -n 1)
  profile=${profile%$'\r'}
  profile=${profile//\"/}
  profile=${profile//\'/}
  profile=${profile//[[:space:]]/}

  case "$profile" in
    dev|prod) printf '%s\n' "$profile" ;;
    *) warn "SPRING_PROFILES_ACTIVE가 dev 또는 prod가 아닙니다: '${profile}'"; return 1 ;;
  esac
}

render() {
  local template=$1
  local output=$2
  local environment=$3

  [[ -r $template ]] || { warn "템플릿이 없습니다: $template"; return 1; }
  sed "s/\${ENV}/$environment/g" "$template" > "$output" || return 1
  # shellcheck disable=SC2016 # 자리표시자 문자열 자체를 찾는다.
  if grep -qF '${ENV}' "$output"; then
    warn "치환되지 않은 \${ENV}가 남았습니다: $output"
    return 1
  fi
}

# 두 설정 파일이 모두 같고, agent.d에 이 설정 하나만 있을 때만 변경 없음으로 본다.
is_up_to_date() {
  local rendered_prometheus=$1
  local rendered_agent=$2

  cmp -s "$rendered_prometheus" "$PROMETHEUS_CONFIG" || return 1
  cmp -s "$rendered_agent" "$APPLIED_AGENT_CONFIG" || return 1

  local entries
  shopt -s nullglob dotglob
  entries=("$AGENT_CONFIG_DIR"/*)
  shopt -u nullglob dotglob
  [[ ${#entries[@]} -eq 1 && ${entries[0]} == "$APPLIED_AGENT_CONFIG" ]]
}

backup_current() {
  local backup_dir=$1

  install -d -m 700 "$backup_dir" || return 1
  if [[ -f $PROMETHEUS_CONFIG ]]; then
    cp -p "$PROMETHEUS_CONFIG" "$backup_dir/prometheus.yaml" || return 1
  fi
  if [[ -d $AGENT_CONFIG_DIR ]]; then
    cp -pR "$AGENT_CONFIG_DIR" "$backup_dir/amazon-cloudwatch-agent.d" || return 1
  fi
  if [[ -f $AGENT_ROOT/etc/amazon-cloudwatch-agent.toml ]]; then
    cp -p "$AGENT_ROOT/etc/amazon-cloudwatch-agent.toml" "$backup_dir/" || return 1
  fi
}

# 적용에 실패하면 백업한 파일을 되돌리고, Agent가 멈춰 있으면 이전 설정으로 다시 띄워 본다.
restore_backup() {
  local backup_dir=$1

  if [[ -f $backup_dir/prometheus.yaml ]]; then
    cp -p "$backup_dir/prometheus.yaml" "$PROMETHEUS_CONFIG"
  fi
  if [[ -d $backup_dir/amazon-cloudwatch-agent.d ]]; then
    shopt -s dotglob
    rm -rf -- "${AGENT_CONFIG_DIR:?}"/*
    shopt -u dotglob
    cp -pR "$backup_dir/amazon-cloudwatch-agent.d/." "$AGENT_CONFIG_DIR/"
  fi
  if [[ -f $backup_dir/amazon-cloudwatch-agent.toml ]]; then
    cp -p "$backup_dir/amazon-cloudwatch-agent.toml" "$AGENT_ROOT/etc/"
  fi
  if ! "$AGENT_CTL" -a status -m ec2 2>/dev/null | grep -q '"status": *"running"'; then
    "$AGENT_CTL" -a start -m ec2 >/dev/null 2>&1 || warn "이전 설정으로 Agent를 다시 시작하지 못했습니다."
  fi
}

# 백업 폴더 이름이 UTC 시각이라 glob 순서가 곧 시간 순서다. 최근 것만 남긴다.
prune_backups() {
  local backups
  shopt -s nullglob
  backups=("$BACKUP_ROOT"/*/)
  shopt -u nullglob
  local excess=$((${#backups[@]} - BACKUPS_TO_KEEP))
  (( excess > 0 )) || return 0
  rm -rf -- "${backups[@]:0:excess}"
}

main() {
  if [[ ! -x $AGENT_CTL ]]; then
    warn "CloudWatch Agent가 설치돼 있지 않아 설정 적용을 건너뜁니다: $AGENT_CTL"
    return 0
  fi

  local environment
  environment=$(read_environment) || return 1

  local work_dir
  work_dir=$(mktemp -d) || return 1
  # shellcheck disable=SC2064 # work_dir는 지금 값으로 고정해 둔다.
  trap "rm -rf -- '$work_dir'" EXIT

  local rendered_prometheus="$work_dir/prometheus.yaml"
  local rendered_agent="$work_dir/$CONFIG_NAME.json"
  render "$TEMPLATE_DIR/prometheus.yaml.tmpl" "$rendered_prometheus" "$environment" || return 1
  render "$TEMPLATE_DIR/agent.json.tmpl" "$rendered_agent" "$environment" || return 1
  python3 -m json.tool "$rendered_agent" >/dev/null || { warn "렌더링한 Agent 설정이 올바른 JSON이 아닙니다."; return 1; }

  if is_up_to_date "$rendered_prometheus" "$rendered_agent"; then
    log "서버 설정이 저장소와 같아 변경하지 않습니다 (ENV=$environment)."
    return 0
  fi

  local backup_dir
  backup_dir="$BACKUP_ROOT/$(date -u +%Y%m%dT%H%M%SZ)-$$"
  backup_current "$backup_dir" || { warn "기존 설정을 백업하지 못해 적용을 중단합니다."; return 1; }
  log "기존 설정을 백업했습니다: $backup_dir"

  install -d -m 755 "$(dirname "$PROMETHEUS_CONFIG")" || return 1
  if ! install -m 644 "$rendered_prometheus" "$PROMETHEUS_CONFIG"; then
    warn "prometheus.yaml을 쓰지 못했습니다."
    restore_backup "$backup_dir"
    return 1
  fi

  if ! "$AGENT_CTL" -a fetch-config -m ec2 -c "file:$rendered_agent" -s; then
    warn "fetch-config에 실패해 이전 설정으로 되돌립니다."
    restore_backup "$backup_dir"
    return 1
  fi

  prune_backups
  log "CloudWatch Agent 설정을 적용했습니다 (ENV=$environment)."
}

# set -u 오류까지 서브셸 안에서 끝나도록 해 배포 훅에는 항상 0을 돌려준다.
if ! ( main ); then
  warn "CloudWatch Agent 설정을 적용하지 못했습니다. 배포는 계속 진행합니다."
fi
exit 0
