# CloudWatch 설정

이 폴더는 CloudWatch 설정을 코드로 관리한다(#740).

| 파일 | 적용하는 곳 | 적용 방법 |
|---|---|---|
| `agent.json.tmpl`, `prometheus.yaml.tmpl` | EC2의 CloudWatch Agent | 배포할 때 `scripts/apply-cloudwatch-agent.sh`가 자동으로 적용한다 |
| `alarms.sh` | CloudWatch 알람 | 사람이 CloudShell에서 실행한다 |
| `dashboard.json.tmpl`, `dashboard.sh` | `mapmory-<env>-v2` 대시보드 | 사람이 CloudShell에서 실행한다 |
| `health-check.sh` | Route 53 외부 헬스체크와 그 알람(us-east-1) | 사람이 CloudShell에서 실행한다. 아래 "외부 헬스체크" |

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
| `route53:ListHealthChecks`, `CreateHealthCheck`, `UpdateHealthCheck`, `ChangeTagsForResource` | `health-check.sh` |
| `sns:CreateTopic`, `ListSubscriptionsByTopic`, `Subscribe` (us-east-1) | `health-check.sh` |
| `lambda:GetFunction`, `GetPolicy`, `AddPermission` | `health-check.sh`(Discord 중계 Lambda) |
| `cloudwatch:PutMetricAlarm` (us-east-1) | `health-check.sh` |

## 관리하는 알람

기준은 ADR 0031 3절이고, 숫자는 `alarms.sh` 맨 위 변수에 모여 있다. 설명이 `[깨움]`이면 사람을 깨워야 하는 증상, `[참고]`면 오류 예산이 새는 신호나 원인이다. 알림 채널은 아직 하나라 이 표시로만 구분한다.

| 알람 | 조건 | 데이터 없음 | 알림 |
|---|---|---|---|
| `metrics-missing` [깨움] | `hikaricp_connections_max` SampleCount < 1, 300초 × 2 | 경보 | 경보·정상 |
| `api-5xx-count` [깨움] | 5xx(`/api/v1`·`v2`) Sum ≥ 3, 600초(최근 10분) | 정상 취급 | 경보·정상 |
| `api-5xx-ratio` | 최근 1시간 요청 30건 이상이고 5xx 비율 > 5% | 정상 취급 | 경보·정상 |
| `api-read-latency` | 최근 1시간 조회(`slo_class=read`) 성공 요청 30건 이상이고 0.3초를 넘긴 비율 > 5% | 정상 취급 | 경보·정상 |
| `api-write-latency` | 최근 1시간 저장(`slo_class=write`) 성공 요청 10건 이상이고 2초를 넘긴 비율 > 10% | 정상 취급 | 경보·정상 |
| `db-connection-pending` | `hikaricp_connections_pending` Maximum ≥ 1, 60초, 5개 중 3개 | 정상 취급 | 경보·정상 |
| `cache-bypass` | `mapmory_deployment_cache_bypass` Minimum ≥ 1, 300초 × 6(30분) | 정상 취급 | 경보·정상 |
| `ec2-cpu-usage-high` | CPUUtilization Average ≥ 80, 300초 × 3 | 판단 안 함 | 경보·정상 |
| `ec2-memory-usage-high` | mem_used_percent Average ≥ 85, 300초 × 3 | 판단 안 함 | 경보·정상 |
| `ec2-disk-usage-high` | disk_used_percent(/) Average ≥ 80, 300초 × 3 | 판단 안 함 | 경보·정상 |
| `ec2-status-check-failed` | StatusCheckFailed Maximum ≥ 1, 300초 × 1 | 경보 | 경보·정상 |
| `health-check-failed` [깨움] | Route 53 HealthCheckStatus Minimum < 1, 60초 × 3 (us-east-1, `health-check.sh`) | 경보 | 경보·정상 |

"최근 10분", "최근 1시간"은 슬라이딩이다. CloudWatch는 매분 알람을 평가하고, 그때마다 지난 구간 전체를 다시 본다.

지연 알람은 SLO 카운터(`http_server_requests_slo_total`)로 계산한다. 기준 안에 끝난 수는 `Sum(le=기준)`, 전체는 `Sum(le=+Inf)`이고, 넘긴 비율은 `1 - 둘의 비`다. 성공한 요청만 센다. 실패는 5xx 알람이 맡는다. 앱이 기동할 때 read·write 카운터를 0으로 미리 만들므로 요청이 없어도 0이 들어와 알람은 `OK`다.

5xx 비율의 분모는 SLO 카운터가 아니라 `http_server_requests_seconds_count`의 outcome 차원(`/api/v1`·`v2`)이다. SLO 카운터로 전체 요청을 만들면 slo_class 4 × outcome 3 = 지표 12개라, 알람 계산식의 지표 10개 한도를 넘는다.

알람마다 설명에 "울리면 무엇부터 볼지"를 한 줄 적었다. 알림 메일에도 이 설명이 나온다.

`cache-bypass`는 배포 캐시가 BYPASS로 남은 것을 잡는다. 슬롯은 BYPASS로 시작하고 배포·재부팅 스크립트가 단독 서비스를 확인한 뒤 NORMAL로 켠다(`backend/docs/deployment-cache-control.md` 4단계). 데이터 없음을 정상으로 두므로, 지표가 아직 없을 때 적용해도 `OK`로 보인다. 이 지표가 들어 있는 버전을 배포해 Agent 설정이 다시 적용된 뒤에 `alarms.sh`를 실행하고, 대시보드 "캐시 모드 (BYPASS=1, NORMAL=0)"에 값이 그려지는지 본다.

`application-error`, `api-5xx-rate`, `rds-free-storage-low`는 이 스크립트가 바꾸지 않는다. `alarms.sh`는 실행할 때마다 이 셋의 지금 조건을 표로 출력한다.

## 외부 헬스체크

`health-check.sh`는 밖에서 공개 주소의 `/health`를 부르는 Route 53 헬스체크와 그 실패 알람을 만든다. 서버가 통째로 죽어도 앱 지표는 0으로 보이지 않고 끊기기만 해서, 이전에는 `metrics-missing`으로만 알 수 있었다.

```bash
./health-check.sh dev --dry-run
./health-check.sh dev
```

대상 주소의 기본값은 dev `https://dev-api.map-mory.com/health`, prod `https://api.map-mory.com/health`다. 앱이 부르는 주소라 이미 공개돼 있다. 다른 주소를 보려면 `--url`이나 `HEALTH_CHECK_URL`로 준다. `https://`만 받는다. http는 Nginx가 https로 돌려보내면 그 3xx를 Route 53이 성공으로 쳐서, 앱이 죽어도 정상으로 보일 수 있다.

- **헬스체크**: HTTPS, 30초 간격, 검사 지점마다 1회 실패면 실패. Route 53은 간격으로 10초·30초만 받아 1분은 없다. 연속 판단은 알람이 1분 단위로 한다. 같은 주소·경로를 보는 헬스체크가 있으면 새로 만들지 않고 그것을 쓴다. Route 53 HTTPS 헬스체크는 인증서를 검증하지 않아 인증서 만료는 잡지 못한다.
- **알람은 us-east-1에 있다.** Route 53 헬스체크 지표(`AWS/Route53` `HealthCheckStatus`)가 us-east-1에만 있기 때문이다. 알람이 알리는 SNS 주제도 같은 리전이어야 해서 `mapmory-<env>-alarms-us-east-1` 주제를 만든다. 서울 대시보드의 알람 위젯에는 넣지 않았다.
- **Discord 전달**: 그 주제를 서울의 Discord 중계 Lambda(`mapmory-discord-alerts`)에 구독시킨다(SNS → 다른 리전 Lambda). Lambda 리소스 정책에 `sns.amazonaws.com`이 이 주제에서만 호출할 수 있게 권한을 더한다(문 ID `sns-us-east-1-mapmory-<env>-alarms`). Lambda는 알람 이름 접두사(`mapmory-dev-`, `mapmory-prod-`)로 환경을 정하므로 알람 이름은 `mapmory-<env>-health-check-failed`다.
- **데이터 없음은 경보로 둔다.** 헬스체크를 지우거나 지표가 끊기면 가용성을 잴 수 없기 때문이다. 처음 만들고 지표가 들어오기 전 몇 분 사이에 경보가 한 번 올 수 있다.

### 헬스체커가 막히지 않는지 확인

Route 53 헬스체커는 여러 리전의 공인 IP에서 온다(`ip-ranges.json`의 `ROUTE53_HEALTHCHECKS`). 보안 그룹이나 Nginx가 이 IP를 막으면 서비스가 정상이어도 알람이 울린다.

1. 보안 그룹: EC2의 443 인바운드가 `0.0.0.0/0`이거나, 관리형 접두사 목록 `com.amazonaws.ap-northeast-2.route53-healthchecks`를 허용하는지 본다.
   ```bash
   groups=$(aws ec2 describe-instances --region ap-northeast-2 --filters "Name=tag:Name,Values=ec2-mapmory-dev-new" \
     --query 'Reservations[].Instances[].SecurityGroups[].GroupId' --output text)
   aws ec2 describe-security-groups --region ap-northeast-2 --group-ids $groups \
     --query 'SecurityGroups[].IpPermissions[?ToPort==`443`].[IpRanges[].CidrIp, PrefixListIds[].PrefixListId]'
   ```
2. Nginx: `allow`/`deny`, `limit_req`, User-Agent 차단이 `/health`에 걸리지 않는지 본다. 헬스체크를 만든 뒤 EC2에서 헬스체커 요청이 200으로 찍히는지 본다.
   ```bash
   sudo grep -h 'Amazon-Route53-Health-Check-Service' /var/log/nginx/access.log | tail -5
   ```
3. Route 53이 본 결과: 지점별 마지막 실패 이유를 본다. 비어 있으면 실패가 없었다.
   ```bash
   aws route53 get-health-check-status --health-check-id <ID> \
     --query 'HealthCheckObservations[].[Region,StatusReport.Status]' --output table
   aws route53 get-health-check-last-failure-reason --health-check-id <ID>
   ```

## 적용 확인

적용하고 10분쯤 지난 뒤, 관리하는 알람이 모두 `OK`인지 본다. `metrics-missing`은 판단에 10분(300초 × 2)이 걸린다.

새로 만든 알람은 `INSUFFICIENT_DATA`로 시작한다. 평가가 끝나 `OK`가 되면 정상 메일을 한 번 보낸다. EC2를 교체한 뒤 다시 실행했을 때나 Agent가 잠깐 끊겼다 돌아올 때도, `INSUFFICIENT_DATA`에서 `OK`로 바뀌면 같은 메일이 온다. 바로 앞에 경보 메일이 없었다면 정상 메일은 장애가 풀렸다는 뜻이 아니다.

```bash
aws cloudwatch describe-alarms --region ap-northeast-2 --alarm-name-prefix mapmory-dev- \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
aws cloudwatch describe-alarms --region us-east-1 --alarm-name-prefix mapmory-dev- \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table   # health-check-failed
```

`INSUFFICIENT_DATA`가 남아 있으면 그 알람의 지표가 들어오는지 확인한다. 대시보드는 CloudWatch 콘솔에서 `mapmory-dev-v2`를 열어 빈 위젯이 없는지 본다. "썸네일 backlog (렌더러 서버)"는 렌더러 서버에 Agent를 설치하기 전까지 비어 있는 것이 맞다.
