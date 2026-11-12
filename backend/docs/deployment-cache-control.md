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

### 1. 기존 서버 우회

기존 서버 관리 포트가 `8081`일 때:

```bash
curl --fail --silent --show-error --max-time 5 \
  -H 'Content-Type: application/json' \
  -d '{"mode":"BYPASS"}' \
  http://127.0.0.1:8081/actuator/deploymentcache
```

### 2. Green 우회 상태 확인

Green은 시작 인자로 `BYPASS`를 지정한 상태로 기동한다. 관리 포트가 `8091`일 때:

```bash
curl --fail --silent --show-error --max-time 5 \
  http://127.0.0.1:8091/actuator/deploymentcache
```

양쪽 모두 HTTP 200과 `"mode":"BYPASS"`인지 확인한다. 한쪽이라도 확인에 실패하면 트래픽 전환을 진행하지 않는다. 명령의 HTTP 성공만 보지 말고 응답 상태도 확인한다.

### 3. 전환 및 롤백 대기

새 프로세스 준비 확인 후 Nginx를 전환한다. 설계한 롤백 대기 시간(5분) 동안 두 프로세스는 모두 우회를 유지한다. 실패하여 기존 서버로 돌아가는 경우에도 양쪽의 우회 상태를 유지한다.

### 4. 이전 프로세스 종료 확인 후 캐시 복귀

기존 서버의 요청 처리가 끝나고 종료된 것을 확인한 뒤, 현재 서비스 중인 Green의 캐시를 켠다.

```bash
curl --fail --silent --show-error --max-time 5 \
  -H 'Content-Type: application/json' \
  -d '{"mode":"NORMAL"}' \
  http://127.0.0.1:8091/actuator/deploymentcache
```

롤백하여 기존 서버가 남았다면 `8091` 대신 기존 서버의 관리 포트 `8081`에서 수행한다. 마지막 프로세스가 단독으로 서비스하는지 확인하기 전에는 켜지 않는다. 복귀 명령 실패 시 우회 상태를 유지하고 원인을 확인한다.

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

아직 자동 배포 스크립트에 이 순서를 연결하지 않았다. 내부 관리 기능만으로 무중단 배포의 완료 조건이 충족되는 것은 아니다.

## 참고

- [Spring Boot: 사용자 정의 Actuator 엔드포인트와 접근 설정](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot: 관리 포트와 주소 설정](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
