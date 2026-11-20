# CloudWatch 설정

이 폴더는 CloudWatch 설정을 코드로 관리한다(#740).

| 파일 | 적용하는 곳 | 적용 방법 |
|---|---|---|
| `agent.json.tmpl`, `prometheus.yaml.tmpl` | EC2의 CloudWatch Agent | 배포할 때 `scripts/apply-cloudwatch-agent.sh`가 자동으로 적용한다 |
| `alarms.sh` | CloudWatch 알람 | 사람이 CloudShell에서 실행한다 |
| `dashboard.json.tmpl`, `dashboard.sh` | `mapmory-<env>-v2` 대시보드 | 사람이 CloudShell에서 실행한다 |

공개 저장소라 계정 번호, ARN, 인스턴스 ID는 저장소에 두지 않는다. 스크립트는 실행할 때마다 이 값을 AWS에서 읽는다.
- 계정 번호: `sts get-caller-identity`
- 인스턴스 ID: EC2 Name 태그
- 루트 디스크의 device·fstype: 지금 들어오는 `disk_used_percent` 지표
- 알림 대상: `mapmory-<env>-application-error` 알람의 SNS 주제

`scripts/tests/cloudwatch-alarms-dashboard-test.sh`는 이 폴더에 12자리 숫자나 인스턴스 ID가 들어오면 실패한다.

## 언제 실행하나

- 알람 조건이나 대시보드를 바꾸는 PR이 머지된 뒤
- **EC2를 교체한 뒤.** 알람과 대시보드는 인스턴스 ID를 가리키므로 새 서버를 보도록 다시 실행해야 한다. 새 서버에서 Agent가 지표를 보낸 뒤에 실행한다. 최근 3시간 안에 들어온 루트 디스크 지표를 찾아야 하기 때문이다.

## 실행

AWS 콘솔의 CloudShell에서 **서울 리전(ap-northeast-2)** 으로 실행한다. 스크립트는 리전을 `ap-northeast-2`로 고정해 쓴다.

```bash
git clone --depth 1 -b backend-develop https://github.com/woowacourse-teams/2026-Mapmory.git
cd 2026-Mapmory/backend/observability/cloudwatch
jq --version || sudo dnf install -y jq

./alarms.sh dev --dry-run      # 만들 알람 정의만 출력한다
./alarms.sh dev                # 계정·리전·대상을 보여 주고 y를 받은 뒤 적용한다
./dashboard.sh dev --dry-run
./dashboard.sh dev
```

prod는 `dev` 자리에 `prod`를 쓴다. 순서는 `alarms.sh` → `dashboard.sh`다. 대시보드의 알람 위젯이 알람을 가리키기 때문이다.

| 옵션 | 뜻 |
|---|---|
| `--dry-run` | AWS에서 읽기만 하고 쓰지 않는다. 만들 내용을 출력한다 |
| `--yes` | 확인을 묻지 않는다 |
| `--name-tag <값>` | EC2를 찾을 Name 태그. 기본값은 `ec2-mapmory-<env>-new` |

다음 경우에는 아무것도 쓰지 않고 멈춘다.
- Name 태그가 같고 실행 중인 인스턴스가 0개거나 2개 이상이다
- 최근 3시간 안에 들어온 루트 디스크 지표가 정확히 1개가 아니다
- `application-error` 알람이나 그 SNS 알림 대상이 없다
- RDS `mapmory-<env>-db`가 없다

## 필요한 권한

| 권한 | 쓰는 곳 |
|---|---|
| `sts:GetCallerIdentity` | 계정 번호 |
| `ec2:DescribeInstances` | 인스턴스 ID |
| `cloudwatch:ListMetrics` | 루트 디스크 device·fstype |
| `cloudwatch:DescribeAlarms` | SNS 알림 대상, 바꾸지 않는 알람의 조건 출력 |
| `cloudwatch:PutMetricAlarm` | `alarms.sh` |
| `rds:DescribeDBInstances` | `dashboard.sh`의 RDS 확인 |
| `cloudwatch:PutDashboard` | `dashboard.sh` |

## 관리하는 알람

| 알람 | 조건 | 데이터 없음 | 알림 |
|---|---|---|---|
| `metrics-missing` | `hikaricp_connections_max` SampleCount < 1, 300초 × 2 | 경보 | 경보·정상 |
| `api-5xx-ratio` | 5xx 비율 > 5%(분당 10건 이상일 때만), 60초, 5개 중 3개 | 정상 취급 | 경보 |
| `db-connection-pending` | `hikaricp_connections_pending` Maximum ≥ 1, 60초, 5개 중 3개 | 정상 취급 | 경보 |
| `ec2-cpu-usage-high` | CPUUtilization Average ≥ 80, 300초 × 3 | 판단 안 함 | 경보 |
| `ec2-memory-usage-high` | mem_used_percent Average ≥ 85, 300초 × 3 | 판단 안 함 | 경보 |
| `ec2-disk-usage-high` | disk_used_percent(/) Average ≥ 80, 300초 × 3 | 판단 안 함 | 경보 |
| `ec2-status-check-failed` | StatusCheckFailed Maximum ≥ 1, 300초 × 1 | 경보 | 경보 |

알람마다 설명에 "울리면 무엇부터 볼지"를 한 줄 적었다. 알림 메일에도 이 설명이 나온다.

`application-error`, `api-5xx-rate`, `rds-free-storage-low`는 이 스크립트가 바꾸지 않는다. `alarms.sh`는 실행할 때마다 이 셋의 지금 조건을 표로 출력한다.

## 적용 확인

적용하고 10분쯤 지난 뒤, 관리하는 알람이 모두 `OK`인지 본다. `metrics-missing`은 판단에 10분(300초 × 2)이 걸린다.

```bash
aws cloudwatch describe-alarms --region ap-northeast-2 --alarm-name-prefix mapmory-dev- \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
```

`INSUFFICIENT_DATA`가 남아 있으면 그 알람의 지표가 들어오는지 확인한다. 대시보드는 CloudWatch 콘솔에서 `mapmory-dev-v2`를 열어 빈 위젯이 없는지 본다. "썸네일 backlog (렌더러 서버)"는 렌더러 서버에 Agent를 설치하기 전까지 비어 있는 것이 맞다.
