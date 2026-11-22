# 무중단 배포 중 로컬 캐시 우회

작성일: 2026-11-10

## 목적과 현재 상태

여행 기록 목록과 지도 요약은 각 JVM의 Caffeine 캐시에 저장된다. 같은 DB를 사용하는 두 JVM도 캐시는 공유하지 않는다. 한 프로세스에서 기록이나 태그를 수정하면 다른 프로세스에 남은 캐시가 무효화되지 않는다.

배포 전환 및 롤백 대기 중에는 **두 프로세스 모두 캐시를 우회**한다. 이전 프로세스의 요청 처리가 끝나고 종료된 뒤, 살아남은 프로세스에서 캐시를 다시 사용한다.

이 문서는 코드와 적용 절차를 기록한다. 개발 EC2 적용, Nginx 전환, 운영 배포 및 실제 롤백의 완료 기록은 아니다. 기존에 실행 중인 JAR에는 이 기능이 없으므로 먼저 기능을 포함한 준비 버전을 배포해야 한다.

## 동작

| 모드 | 캐시 읽기 | 캐시 채우기 | 조회 경로 |
| --- | --- | --- | --- |
| `NORMAL` | 사용 | 사용 | 적중 시 캐시, 미스 시 기존 조회 로직 |
| `BYPASS` | 건너뜀 | 건너뜀 | 매번 기존 DB 조회 로직 실행 |

두 캐시는 한 `DeploymentCacheControl`을 공유한다. 이 제어 상태는 **한 프로세스 안에서만** 공유된다. 기존 서버와 Green에는 각각 명령을 보내고 결과를 확인해야 한다.

### 초기화와 진행 중 요청

모드가 바뀔 때 세대 번호를 증가시키고, 캐시 키에 세대를 포함한다. `BYPASS`에서 `NORMAL`로 복귀한 요청은 이전 세대의 값을 읽지 않고 새로 조회한다. 같은 모드로 명령을 재시도하면 세대는 바뀌지 않는다.

이는 캐시 객체를 물리적으로 새로 만드는 대신 **빈 캐시 세대로 전환**하는 방식이다. 따라서 Caffeine의 기존 크기 제한과 적중·미스 메트릭을 유지한다. 기존에 저장된 이전 세대 항목은 접근되지 않으며 기존 TTL/축출 정책에 따라 제거된다. 배포 전부터 진행 중이던 조회가 세대 변경 후 끝나면 해당 조회의 이전 세대 항목도 무효화한다.

우회 조회는 캐시를 호출하지 않으므로 `cache.gets`의 적중·미스 집계에도 포함되지 않는다. 배포 중 적중률만 보고 캐시 사용 여부를 판단하지 말고 관리 API의 모드, 전환 로그, DB 부하를 함께 확인한다.

이전 조회가 늦게 끝나도 새 세대에 오래된 값을 저장하지 않는다. 전환 전에 시작된 요청 자체가 이전 조회 결과를 반환하는 것은 허용하며, 이전 서버 종료 전 요청 처리가 끝나는지 확인하는 절차는 여전히 필요하다.

### 회원 단위 무효화도 세대로 한다

> 2026-11-18 추가: 기록·태그가 바뀌면 그 회원의 캐시를 무효화한다. 처음에는 키를 순회해 지웠다(`asMap().keySet().removeIf`). 하지만 Caffeine은 계산 중인 항목을 순회에 보여 주지 않는다. 그래서 커밋 전에 DB를 읽기 시작해 무효화 뒤에 끝난 조회가 옛 값을 넣어 TTL(30분) 동안 남았다. 방금 저장한 기록이 지도와 목록에 보이지 않았다.
> 배포 세대와 같은 방식으로, 회원마다 세대(`MemberCacheGenerations`)를 두고 키에 넣는다. 무효화는 그 회원의 세대를 올리고, 늦게 끝난 조회는 옛 세대 키에 들어가 읽히지 않는다. 순회해 지우기는 메모리를 비우려고 남긴다.
> 세대 값은 회원마다 0부터 세지 않고 한 번도 쓰지 않은 번호를 받는다. 세대가 만료되어 다시 만들어져도 살아 있는 옛 항목과 키가 겹치지 않는다. 세대는 마지막으로 읽거나 바꾼 뒤 캐시 TTL만큼 두는데, 이 기간은 정확성과 관계없고 먼저 사라지면 그 회원의 다음 조회가 DB를 한 번 더 읽을 뿐이다.

### 시작 모드와 재시작

일반 실행의 기본 시작 모드는 기존 동작을 유지하는 `NORMAL`이다. 무중단 배포에 참여하는 **기존/신규 슬롯의 systemd 실행 인자에는 모두** 다음을 넣어야 한다.

```text
--deployment.cache.initial-mode=BYPASS
```

그러면 프로세스가 재시작되어도 먼저 `BYPASS`로 시작한다. 상태는 메모리에 있으므로 관리 API에서 켠 `NORMAL`은 재시작 후 유지되지 않는다. 단독 서비스 상태를 확인한 뒤에만 다시 켠다. 한 슬롯이라도 기본 `NORMAL`로 재시작할 수 있는 설정이면 공존 중 우회 보장이 깨진다.

### 개발 EC2에 등록한 시작 설정

2026-11-10 사용자가 전달한 EC2 출력으로 아래 설정 등록을 확인했다. 실행 인자와 같은 설정을 환경변수 `DEPLOYMENT_CACHE_INITIAL_MODE=BYPASS`로 전달했다.

- 공통 환경 파일: `/etc/mapmory-deployment-cache.env`
- 기존 서비스 추가 설정: `/etc/systemd/system/mapmory.service.d/50-deployment-cache.conf`
- Green 추가 설정: `/etc/systemd/system/mapmory-green.service.d/50-deployment-cache.conf`
- 각 추가 설정의 `[Service]`에 `EnvironmentFile=/etc/mapmory-deployment-cache.env` 등록
- 두 서비스 모두 `EnvironmentFiles`에 기존 `/etc/mapmory.env` 다음으로 공통 파일이 표시됨
- `systemd-analyze verify`에는 기존 acpid/falcon-sensor의 레거시 경로 경고만 표시됨
- `daemon-reload` 후 두 서비스는 모두 `active`

이 단계에서 프로세스를 재시작하지 않았다. 설정은 다음 시작부터 읽으며, 당시 실행 중인 구버전 JAR에 캐시 제어 기능이 적용됐다는 의미는 아니다. 저장소의 배포 훅은 `mapmory`를 중지/시작하고 JAR를 교체하며, 이 추가 설정 파일을 변경하지 않는다.

## 내부 관리 API

Spring Boot의 HTTP 전용 사용자 정의 Actuator 엔드포인트를 사용한다. JMX 제어 기능은 추가하지 않는다.

| 기능 | 요청 |
| --- | --- |
| 상태 조회 | `GET /actuator/deploymentcache` |
| 모드 변경 | `POST /actuator/deploymentcache`, JSON `{"mode":"BYPASS"}` 또는 `{"mode":"NORMAL"}` |

응답은 `mode`, `generation`, `changedAt`을 포함한다. `changedAt`은 UTC 시각이다. 상태 조회 응답은 Actuator 기본 설정상 캐시되지 않는다.

모드가 없거나 잘못된 값이면 HTTP 400이며 상태는 바뀌지 않는다. 접근 거부 시 403, 앱 포트에 엔드포인트 자체가 없으면 404가 반환될 수 있다.

### 접근 경계

- 별도로 열린 관리 서버의 실제 수신 포트에서만 허용한다.
- 원격 주소가 루프백인 요청만 허용한다. 개발/운영의 관리 서버 바인딩 `127.0.0.1`을 유지한다.
- 관리 서버 바인딩 설정이 루프백 리터럴이 아니면 로컬에서 호출해도 거부한다.
- `local.management.port` 또는 `local.server.port`가 없거나 두 포트가 같으면 거부한다.
- `Host`, `X-Forwarded-Port` 헤더의 포트는 접근 판단에 사용하지 않는다.
- 앱 포트의 요청은 Nginx가 localhost에서 전달해도 제어할 수 없다. 관리 포트로 향하는 프록시 경로를 추가하지 않는다.
- 로컬 서버 접근 권한이 있는 운영자가 사용하는 기능이며, 이 기능에는 사용자 토큰을 보내지 않는다.

기존 사용자·관리자 API의 인증 규칙은 유지한다. 내부 관리 API에만 우선 적용되는 별도 보안 체인을 추가한다.

## 기능 배포 후 사용할 명령

**지금 실행 중인 구버전 JAR에는 사용할 수 없다.** 아래는 준비 버전 배포 및 양쪽 슬롯의 시작 모드 설정 후 EC2 안에서 실행할 명령이다. 공개 주소로 호출하지 않는다.

### 1~2. 전환 전 양쪽 우회 (자동)

`switch-traffic.sh`가 Nginx를 바꾸기 전에 기존 슬롯과 새 슬롯 모두에 `{"mode":"BYPASS"}`를 보내고, 다시 읽어 `"mode":"BYPASS"`인지 확인한다(`require_deployment_cache_bypass`). 기존 슬롯은 배포 중 재시작되지 않으므로 지난 배포의 4단계가 켠 `NORMAL`이 남아 있다. 새 슬롯의 시작 모드만으로는 공존 중 우회가 보장되지 않는다.

한쪽이라도 확인에 실패하면 전환하지 않는다. 스크립트가 실패로 끝나고 기존 ERR 트랩이 기존 경로를 복구한다. 4단계와 달리 정확성 문제라 실패를 삼키지 않는다.

수동으로 확인할 때(관리 포트 `8081`/`8091`):

```bash
for port in 8081 8091; do
  curl --fail --silent --show-error --max-time 5 \
    -H 'Content-Type: application/json' -d '{"mode":"BYPASS"}' \
    "http://127.0.0.1:$port/actuator/deploymentcache"
  curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:$port/actuator/deploymentcache"
done
```

### 3. 전환 및 롤백 대기

새 프로세스 준비 확인 후 Nginx를 전환한다. 롤백 대기 시간(5분) 동안 두 프로세스는 모두 우회를 유지한다. 실패하여 기존 서버로 돌아가는 경우에도 양쪽의 우회 상태를 유지한다. 롤백 뒤 남은 슬롯은 BYPASS로 남고, 30분이 지나면 `cache-bypass` 알람이 울린다.

### 4. 이전 프로세스 종료 확인 후 캐시 복귀 (자동)

> 2026-11-22: 이 단계를 하는 스크립트가 없어 아무도 켜지 않았다. dev는 11/22 09:13 배포 이후, prod는 11/19 10:50 배포 이후 약 3일 동안 BYPASS(generation 0)였다. 그동안 지도 요약·목록 캐시를 모두 건너뛰어 모든 조회가 DB로 갔다. 값은 맞아서 아무도 몰랐다. `cache_gets_total`의 hit·miss가 둘 다 0이었다.

배포와 재부팅 스크립트가 켠다. 공통 함수는 `scripts/deployment-common.sh`의 `enable_deployment_cache_when_alone`이다.

| 경로 | 언제 켜나 | 켜지 않는 경우 |
| --- | --- | --- |
| 배포(`validate-and-retain.sh`) | 5분 관찰이 끝나고 이전 서비스를 멈춘 뒤, 릴리스 정리까지 끝나고 롤백 트랩을 해제한 맨 끝 | 이전 서비스가 아직 active(멈추기 실패) |
| 재부팅(`start-active-slot-on-boot.sh`) | Nginx가 가리키는 슬롯을 띄워 health를 확인하고, 다른 슬롯이 떠 있으면 멈춘 뒤 | 다른 슬롯을 멈추지 못함(스크립트가 실패로 끝난다) |

- 켠 뒤 다시 조회해 `"mode":"NORMAL"`인지 확인한다. HTTP 성공만 보지 않는다.
- 켜지 못해도 배포는 실패시키지 않는다. 트래픽은 이미 새 슬롯에 있고 캐시는 성능 문제다. 경고 로그에 수동 명령을 남긴다.
- 롤백 트랩을 해제한 뒤에 켜는 이유: 앞 단계가 실패하면 롤백이 이전 슬롯을 다시 띄운다. 새 슬롯이 이미 NORMAL이면 두 프로세스가 공존하는 동안 캐시가 켜져 있게 된다.

켜지지 않은 것은 지표로 드러난다.

- 지표: `mapmory_deployment_cache_bypass`(BYPASS=1, NORMAL=0, 차원 service·environment)
- 대시보드: 3번 섹션 "캐시 모드 (BYPASS=1, NORMAL=0)". 배포 중에는 1이고, 끝나면 0으로 내려가야 한다.
- 알람: `mapmory-<env>-cache-bypass`. 모든 프로세스가 30분 넘게 BYPASS면 울린다(Minimum, 5분 × 6). 지표가 끊긴 것은 `metrics-missing`이 맡는다.

경고가 남았거나 알람이 울리면, 활성 슬롯 하나만 실행 중인지 확인하고 그 슬롯의 관리 포트에서 수동으로 켠다. Green(8090)이 활성이면 `8091`, Blue(8080)가 활성이면 `8081`이다.

```bash
systemctl is-active mapmory mapmory-green   # 하나만 active여야 한다
curl --fail --silent --show-error --max-time 5 \
  -H 'Content-Type: application/json' \
  -d '{"mode":"NORMAL"}' \
  http://127.0.0.1:8091/actuator/deploymentcache
curl --fail --silent --show-error --max-time 5 \
  http://127.0.0.1:8091/actuator/deploymentcache   # "mode":"NORMAL" 확인
```

두 서비스가 모두 active면 켜지 않는다. 이전 슬롯을 멈춘 뒤 켠다.

## 확인한 항목과 남은 검증

2026-11-10 로컬 실행 결과: 아래 명령의 관련 테스트 **70개 통과, 실패·오류·건너뜀 0개**. Java 21에서 실행했으며, 관리 API는 실제 분리된 로컬 HTTP 포트로 확인했다. 이는 개발/운영 EC2 적용 결과가 아니다.

로컬 자동 테스트는 다음을 확인한다.

- 목록·지도 모두 우회 중 기존 캐시를 읽거나 새로 채우지 않는다.
- 복귀한 첫 조회는 새 데이터를 읽고, 다음 조회는 새 세대에 저장한 데이터를 사용한다.
- 이전 세대의 조회가 복귀 후 늦게 끝나도 새 캐시를 덮어쓰지 않는다.
- 하나의 제어 상태가 목록·지도에 함께 적용된다.
- 처음부터 우회 모드로 시작할 수 있고, 같은 모드 명령의 재시도는 멱등적이다.
- 실제 별도 로컬 HTTP 포트에서 관리 API 조회·변경이 동작한다.
- 앱 포트로 제어 명령을 보내도 상태가 바뀌지 않는다.
- 잘못된 명령, 외부 원격 주소, 관리 포트 미분리, 위조 프록시 포트 헤더를 거부한다.
- 기존 캐시의 회원별 무효화·용량 제한·적중/미스 메트릭과 관련 서비스 테스트를 유지한다.

실행 명령(backend 디렉터리, Java 21; 기존 인증 통합 테스트에는 Docker 필요):

```bash
./gradlew test \
  --tests 'com.mapmory.backend.common.cache.*' \
  --tests '*TravelRecordListCacheTest' \
  --tests '*MapSummaryCacheTest' \
  --tests '*TravelRecordServiceTest' \
  --tests '*RegionMapSummaryServiceTest' \
  --tests '*SecurityIntegrationTest'
```

실제 개발/운영 환경에서는 캐시를 미리 채운 뒤 전환하고, 통제된 테스트 기록을 수정한 다음 롤백해 최신 목록·지도 결과를 확인해야 한다. 우회 중 DB 부하도 관측한다. 외부 요청 실패 수·실패 구간과 실제 롤백 시작/완료 시각 기록은 별도로 필요하다.

배포·재부팅 스크립트가 1~2단계(전환 전 양쪽 BYPASS 확인)와 4단계(NORMAL 복귀)를 자동으로 한다(2026-11-22). 내부 관리 기능만으로 무중단 배포의 완료 조건이 충족되는 것은 아니다.

## 참고

- [Spring Boot: 사용자 정의 Actuator 엔드포인트와 접근 설정](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot: 관리 포트와 주소 설정](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
