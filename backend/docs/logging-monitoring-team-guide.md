# 팀원을 위한 Mapmory 로깅·모니터링 안내

## 30초 요약

Mapmory의 관측 기능은 다음 네 가지 역할로 나뉜다.

| 기능 | 쉬운 비유 | 답할 수 있는 질문 |
| --- | --- | --- |
| 로그 | 사건 기록 | 이 요청은 왜 실패했나? |
| Request ID | 요청마다 붙는 번호표 | 섞여 있는 로그 중 같은 요청은 무엇인가? |
| 메트릭 | 숫자로 된 계기판 | 요청이 얼마나 많고, 느리고, 자주 실패하는가? |
| Health Check | 생존 신호 | 애플리케이션과 연결된 구성요소가 살아 있는가? |

애플리케이션이 로그와 메트릭을 **만드는 코드**는 구현되어 있다. 운영 로그는 파일로 남고,
메트릭은 `/actuator/prometheus`에 노출된다.

운영 EC2의 CloudWatch Agent는 `/var/log/mapmory/application.log`와 Prometheus 메트릭을
CloudWatch로 전송한다. 애플리케이션 로그 그룹 `/mapmory/prod/application`의 보존 기간은
7일이고 Prometheus EMF 로그 그룹 `/mapmory/prod/prometheus-emf`의 보존 기간은 14일이다.
`dashboard-mapmory-prod` 대시보드와 EC2·RDS 인프라 알람, `mapmory-prod-alerts` SNS 알림 채널도
구성되어 있다.

## 먼저 알아둘 개념

| 개념 | 짧은 설명 |
| --- | --- |
| 관측 가능성(Observability) | 외부로 드러나는 로그와 메트릭을 이용해 서비스 내부 상태를 이해하는 능력이다. |
| 로그(Log) | 특정 시점에 발생한 사건을 문장과 필드로 기록한 데이터다. 개별 오류의 원인을 조사할 때 사용한다. |
| 메트릭(Metric) | 요청 수, 오류 수, 실행 시간처럼 시간에 따라 변하는 숫자다. 서비스 전체 상태와 추세를 볼 때 사용한다. |
| Request ID | HTTP 요청 하나에 붙이는 고유 번호다. 같은 요청에서 발생한 여러 로그를 연결한다. |
| MDC | 현재 요청의 Request ID 같은 값을 잠시 보관해 같은 스레드에서 출력되는 로그에 자동으로 붙이는 저장 공간이다. |
| 구조화 로그 | 로그를 단순 문장이 아니라 `requestId`, `status` 같은 필드가 있는 JSON으로 기록하는 방식이다. 필드별 검색이 쉽다. |
| 로그 레벨 | 로그의 중요도를 나타내는 `ERROR`, `WARN`, `INFO`, `DEBUG` 구분이다. 운영에서는 중요한 로그만 남도록 조절한다. |
| Spring Boot Actuator | Health와 메트릭처럼 운영에 필요한 정보를 제공하는 Spring Boot 기능이다. |
| Micrometer | Java 코드와 라이브러리에서 메트릭을 측정하고 공통 형식으로 관리하는 도구다. |
| Prometheus 형식 | Micrometer 메트릭을 외부 수집기가 읽을 수 있도록 표현하는 텍스트 형식이다. Mapmory는 `/actuator/prometheus`로 노출한다. |
| Timer | 작업 실행 횟수와 걸린 시간을 함께 기록하는 Micrometer 메트릭이다. API와 중요 내부 작업의 지연 시간을 측정한다. |
| 태그(Tag)·라벨(Label) | 메트릭을 `operation=MEDIA_SYNC`, `outcome=SUCCESS`처럼 분류하는 값이다. Prometheus에서는 주로 라벨이라고 부른다. |
| 카디널리티 | 서로 다른 태그 값 조합의 개수다. 값이 무제한으로 늘면 메모리와 모니터링 비용도 커진다. |
| Summary | 실행 횟수·누적 시간과 애플리케이션에서 계산한 percentile을 함께 노출하는 방식이다. |
| p95 | 전체 요청 중 약 95%가 이 시간 안에 끝났다는 뜻이다. 평균으로 가려지는 느린 요청을 찾는 데 사용한다. |
| Health Check | 애플리케이션이나 연결된 구성요소가 정상인지 간단한 상태로 확인하는 기능이다. |
| CloudWatch | AWS의 메트릭·로그 저장, 검색, 대시보드와 알림 서비스다. 애플리케이션이 만든 데이터를 운영자가 보는 곳이다. |
| CloudWatch Agent | EC2 안에서 시스템 메트릭과 로그 파일을 읽어 CloudWatch로 보내는 프로그램이다. |
| SNS | CloudWatch 알람이 발생했을 때 이메일 같은 채널로 알림을 전달하는 AWS 서비스다. |

도구의 관계만 간단히 정리하면 다음과 같다.

```text
Spring Boot Actuator + Micrometer
    └─ 애플리케이션 메트릭을 만들고 노출

RequestIdFilter + MDC + Logger
    └─ 요청별 로그를 만들고 파일에 기록

CloudWatch Agent
    └─ EC2의 메트릭과 로그를 CloudWatch로 전송

CloudWatch + SNS
    └─ 저장·검색·대시보드·장애 알림
```

## 전체 흐름

```text
사용자 요청
    │
    ▼
RequestIdFilter
    ├─ 요청마다 Request ID 생성
    ├─ 응답 헤더에 X-Request-Id 추가
    └─ 같은 요청의 모든 로그에 Request ID 추가
    │
    ▼
Controller → Service → Repository / Kakao / S3
    │                    │
    │                    └─ 중요한 작업 시간을 메트릭으로 기록
    │
    ├─ 정상 응답
    └─ 예외 → ExceptionHandler가 오류 로그와 안전한 오류 응답 생성

동시에 Micrometer가 요청 수·상태 코드·응답 시간을 집계
    │
    ▼
/actuator/prometheus에서 메트릭 노출

운영 로그 파일과 메트릭
    │
    └─ CloudWatch Agent가 수집 → CloudWatch에서 저장·검색·대시보드·알림
```

## 로그: 개별 사건의 원인을 찾는다

로그는 특정 요청에서 실제로 무슨 일이 있었는지 확인할 때 사용한다.

서버에서는 여러 요청을 동시에 처리하므로 로그가 다음처럼 섞일 수 있다.

```text
14:00:01 [requestId:REQ-123] 여행 기록 수정 요청
14:00:01 [requestId:REQ-456] 지도 요약 조회 요청
14:00:02 [requestId:REQ-456] 지도 요약 조회 완료
14:00:02 [requestId:REQ-123] ERROR 미디어 저장 실패
```

`REQ-123`으로 검색하면 여행 기록 수정 요청만 분리할 수 있다.

```text
[requestId:REQ-123] 여행 기록 수정 요청
[requestId:REQ-123] ERROR 미디어 저장 실패
```

문서의 `REQ-123`은 이해를 위한 짧은 예시다. 실제 Mapmory에서는 중복 가능성이 매우 낮은
UUID를 사용한다.

### Request ID가 붙는 과정

1. 클라이언트가 유효한 `X-Request-Id`를 보내면 그대로 사용한다.
2. 헤더가 없거나 UUID 형식이 아니면 서버가 새 UUID를 만든다.
3. Request ID를 MDC에 넣는다.
4. 요청 처리 중 발생하는 로그에 같은 Request ID가 자동으로 붙는다.
5. 응답의 `X-Request-Id` 헤더에도 같은 값을 넣는다.
6. 요청이 끝나면 MDC 값을 제거한다.

```http
HTTP/1.1 500 Internal Server Error
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
```

사용자가 이 값을 제보하면 해당 요청 로그를 바로 찾을 수 있다. 더 자세한 예시는
[Request ID 로그 안내](request-id-logging-guide.md)에 있다.

### 어떤 오류를 어떤 레벨로 남기는가

| 상황 | 현재 로그 레벨 | 이유 |
| --- | --- | --- |
| 예상하지 못한 서버 예외 | `ERROR` | 개발자가 원인과 스택 트레이스를 확인해야 한다. |
| 컨트롤러 반환값 검증 실패 | `ERROR` | 서버가 잘못된 응답을 만들었다. |
| 카카오 등 의존 시스템 사용 불가 | `ERROR` | 사용자 입력이 아니라 외부 시스템 문제다. |
| 일반적인 잘못된 입력·리소스 없음·토큰 만료 | `DEBUG` | 예상 가능한 사용자 요청 실패다. |

일반적인 4xx를 모두 `ERROR`로 남기지 않는다. 그렇게 하면 실제 서버 장애가 정상적인 사용자
실수 로그에 묻히기 때문이다.

현재는 모든 정상 요청의 시작과 종료를 로그로 남기지 않는다. 요청 수와 응답 시간은 메트릭이
더 적합하며, 모든 요청을 INFO 로그로 남기면 로그 저장량이 크게 증가한다.

### 로컬 로그와 운영 로그

로컬에서는 사람이 읽기 쉬운 문자열 로그를 사용한다.

```text
ERROR [requestId:550e8400-...] Unhandled exception while processing POST /api/...
```

운영에서는 프로그램이 필드별로 검색하기 쉬운 JSON 로그를 사용하며 다음 파일에 기록한다.

```text
/var/log/mapmory/application.log
```

개념상 다음과 같은 정보가 한 로그에 들어간다.

```json
{
  "service": "mapmory-backend",
  "environment": "prod",
  "version": "배포 버전",
  "requestId": "550e8400-...",
  "event": "UNHANDLED_EXCEPTION",
  "status": 500,
  "httpMethod": "POST",
  "uri": "/api/v1/travel-records",
  "message": "Unhandled exception while processing POST /api/v1/travel-records"
}
```

JSON 로그는 CloudWatch에서 정규식 대신 `requestId`, `event`, `status` 같은 필드로 검색할 수
있다. `/mapmory/prod/application` 로그 그룹은 로그를 7일간 보관한 뒤 자동 삭제한다.

## 메트릭: 서비스 전체 상태를 숫자로 본다

메트릭은 개별 요청의 상세 이야기가 아니라 많은 요청을 숫자로 합친 결과다.

```text
로그:    REQ-123 요청이 DB 오류로 실패했다.
메트릭:  최근 5분 동안 요청 1,000건 중 5xx가 30건 발생했다.
```

### 자동으로 수집되는 주요 메트릭

| 메트릭 | 알 수 있는 것 |
| --- | --- |
| `http.server.requests` | API 요청 수, 상태 코드, 전체 응답 시간 |
| `http.client.requests` | 백엔드에서 카카오 API로 보낸 요청의 수와 응답 시간 |
| JVM 메트릭 | Heap 메모리, GC, 스레드 등 Java 프로세스 상태 |
| HikariCP 메트릭 | 사용 중·대기 중인 DB 커넥션 상태 |

Spring Boot 코드에서는 점(`.`)이 들어간 이름을 사용하지만 Prometheus 출력에서는 밑줄(`_`)과
단위가 붙는다.

```text
http.server.requests
→ http_server_requests_seconds_count
→ http_server_requests_seconds_sum
→ http_server_requests_seconds{quantile="0.95"}
```

### 직접 추가한 중요 작업 메트릭

API 전체 시간만 보면 어느 내부 작업이 느린지 알기 어렵다. 그래서 다음 두 작업만 별도로
측정한다.

| operation | 측정하는 작업 |
| --- | --- |
| `MEDIA_SYNC` | 여행 기록의 미디어 목록 동기화와 DB 반영 |
| `MAP_SUMMARY_QUERY` | 지역별 지도 요약 DB 조회 |

공통 메트릭 이름은 다음과 같다.

```text
mapmory.operation.duration
```

각 실행은 성공과 실패로 나뉜다.

```text
operation=MEDIA_SYNC, outcome=SUCCESS
operation=MEDIA_SYNC, outcome=FAILURE
```

예를 들어 지도 API가 느릴 때 `MAP_SUMMARY_QUERY`도 느리면 DB 조회가 원인일 가능성이 높다.
API 전체는 느리지만 이 작업은 빠르다면 인증, DTO 변환 또는 다른 구간을 조사한다.

### p95 Summary를 쉽게 이해하기

애플리케이션은 각 URI와 내부 작업의 최근 실행 시간을 이용해 p95를 계산하고
`quantile="0.95"` 시계열로 노출한다. 예를 들어 p95가 `0.8`이면 해당 태그 조합의 요청 약 95%가
0.8초 안에 끝났다는 뜻이다. 이 값은 서로 다른 URI나 여러 서버 사이에서 더하거나 평균내면
정확하지 않으므로 각 시계열을 따로 본다.

## 로그와 메트릭을 함께 보는 방법

### 상황 1: 사용자가 500 오류를 제보했다

1. 응답의 `X-Request-Id`를 확인한다.
2. CloudWatch Logs에서 같은 `requestId`를 검색한다.
3. `event`, `errorCode`, 스택 트레이스로 실패 원인을 찾는다.

```sql
fields @timestamp, requestId, event, @message
| filter requestId = "550e8400-e29b-41d4-a716-446655440000"
| sort @timestamp asc
```

### 상황 2: 전체적으로 API가 느리다

1. `http.server.requests`에서 느린 URI와 p95를 확인한다.
2. `mapmory.operation.duration`에서 어떤 내부 작업이 느린지 확인한다.
3. 같은 시간대의 ERROR 로그를 확인한다.

### 상황 3: DB 연결이 부족해 보인다

1. HikariCP 활성·대기 커넥션 메트릭을 확인한다.
2. RDS의 CPU, 연결 수와 메모리 메트릭을 함께 확인한다.
3. 커넥션 부족이 발생한 시간대의 애플리케이션 오류 로그를 확인한다.

핵심 순서는 다음과 같다.

```text
메트릭으로 이상 위치 찾기 → Request ID 로그로 구체적인 원인 찾기
```

## Health 엔드포인트 구분

Mapmory에는 이름이 비슷한 두 엔드포인트가 있다.

| 주소 | 역할 |
| --- | --- |
| `/health` | 현재 애플리케이션의 단순 실행 확인 페이지 |
| `/actuator/health` | Spring Boot Actuator가 제공하는 상태 확인 |

운영에서는 Actuator가 일반 API와 다른 로컬 관리 포트를 사용한다.

```text
일반 API: 0.0.0.0:8080
Actuator: 127.0.0.1:8081
```

따라서 외부 사용자가 8081에 접근하도록 보안 그룹이나 Nginx를 열면 안 된다. 같은 서버에서
실행되는 수집기만 접근하는 구조다.

## 메트릭 태그에 넣어도 되는 값

메트릭은 `이름 + 모든 태그 값의 조합`마다 새로운 시계열이 만들어진다.

허용되는 값:

- `SUCCESS`, `FAILURE`처럼 종류가 고정된 값
- HTTP 메서드와 상태 코드
- `MEDIA_SYNC`처럼 enum으로 제한된 작업 이름

넣으면 안 되는 값:

- `requestId`
- `memberId`, `travelRecordId`
- 실제 파일명, S3 object key
- 예외 메시지, 토큰, 실제 사용자 입력

Request ID를 메트릭 태그로 넣으면 요청 수만큼 시계열이 생긴다. Request ID는 로그에서만
사용한다. HTTP URI 태그도 비정상적으로 늘어나는 것을 막기 위해 최대 50종까지만 등록한다.

## 민감정보 로그 금지

다음 값은 디버깅이 필요해도 로그에 남기지 않는다.

- access token, refresh token, 카카오 access token
- JWT secret, AWS 키, DB 비밀번호
- Presigned URL 전체
- 요청·응답 본문 전체
- 여행 제목과 본문 등 불필요한 개인정보

필요한 경우에도 원문 대신 내부 오류 코드나 값의 존재 여부처럼 최소 정보만 기록한다.

## 팀원이 새 로그를 추가할 때

1. 이 로그가 운영 중 실제로 검색할 사건인지 확인한다.
2. 정상 상태 변경은 `INFO`, 조치가 필요한 실패는 `ERROR`를 사용한다.
3. 요청마다 발생하는 정상 시작·종료 로그는 가능하면 메트릭으로 대체한다.
4. 검색할 값은 SLF4J key-value 필드로 추가한다.
5. 토큰, 개인정보, 요청 본문을 기록하지 않는다.
6. 같은 예외의 스택 트레이스를 여러 계층에서 반복 기록하지 않는다.

```java
log.atError()
        .addKeyValue("event", "UNHANDLED_EXCEPTION")
        .addKeyValue("status", 500)
        .addKeyValue("httpMethod", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .setCause(exception)
        .log("Unhandled exception while processing request");
```

`requestId`는 Filter와 MDC가 자동으로 추가하므로 위 코드에서 다시 넣지 않는다.

## 팀원이 새 메트릭을 추가할 때

새 메트릭은 다음 조건을 만족할 때만 추가한다.

- API 전체 시간만으로 병목을 구분할 수 없다.
- DB 또는 외부 시스템처럼 운영 중 따로 확인할 가치가 있다.
- 태그 값의 종류를 작고 고정된 범위로 제한할 수 있다.

중요 내부 작업을 추가할 때는 다음 순서를 사용한다.

1. `MonitoredOperation` enum에 작업을 추가한다.
2. 측정할 최소 구간을 `operationTimer.record(...)`로 감싼다.
3. 성공 결과와 실패 예외가 계측 전과 동일하게 전달되는지 테스트한다.
4. ID나 임의 문자열을 태그로 넣지 않는다.

```java
return operationTimer.record(
        MonitoredOperation.MAP_SUMMARY_QUERY,
        () -> repository.findRegionMapSummaries(...)
);
```

## 로컬에서 확인하기

백엔드를 실행한 후 Health와 Request ID를 확인한다.

```bash
curl -i http://localhost:8080/actuator/health
```

메트릭을 확인한다.

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests|http_client_requests|jvm_memory|hikaricp|mapmory_operation'
```

`mapmory_operation` 메트릭은 해당 기능을 한 번 이상 실행해야 나타난다.

## 구현 완료와 남은 작업

### 애플리케이션에서 완료된 것

- [x] Request ID 생성·응답·MDC 정리
- [x] 예외 종류에 따른 로그 레벨과 구조화 필드
- [x] 운영 JSON 로그와 파일 출력
- [x] Actuator Health와 Prometheus 엔드포인트
- [x] HTTP 서버, JVM, HikariCP 자동 메트릭
- [x] 카카오 RestClient 외부 요청 메트릭과 타임아웃
- [x] 중요 내부 작업 2종의 성공·실패 시간 측정
- [x] 메트릭 공통 태그와 URI 태그 개수 제한
- [x] 관련 단위·통합 테스트

### AWS 운영 상태와 남은 작업

- [x] EC2 IAM Role과 CloudWatch Agent 설정
- [x] `/var/log/mapmory/application.log`를 CloudWatch Logs에 전송
- [x] 애플리케이션 로그 그룹 보존 기간 7일 설정
- [x] Prometheus 메트릭 수집 설정
- [x] Prometheus EMF 로그 그룹 보존 기간 14일 설정
- [x] EC2·RDS 기본 메트릭과 애플리케이션 로그 대시보드 구성
- [x] EC2·RDS 장애 알람과 SNS 알림 채널 구성
- [ ] API 오류율·지연과 내부 작업 메트릭의 서비스 수준 알람 기준 확정
- [ ] 운영 지표에 맞춰 전송 메트릭 allowlist 지속 점검

## 코드 위치

| 내용 | 코드 |
| --- | --- |
| Request ID | [`RequestIdFilter`](../src/main/java/com/mapmory/backend/common/logging/RequestIdFilter.java) |
| 예외 로그 | [`common/handler`](../src/main/java/com/mapmory/backend/common/handler) |
| 메트릭 공통 설정 | [`MetricsConfiguration`](../src/main/java/com/mapmory/backend/common/monitoring/MetricsConfiguration.java) |
| 내부 작업 시간 측정 | [`OperationTimer`](../src/main/java/com/mapmory/backend/common/monitoring/OperationTimer.java) |
| 측정 대상 작업 목록 | [`MonitoredOperation`](../src/main/java/com/mapmory/backend/common/monitoring/MonitoredOperation.java) |
| 공통 설정 | [`application.yaml`](../src/main/resources/application.yaml) |
| 운영 로그·관리 포트 | [`application-prod.yaml`](../src/main/resources/application-prod.yaml) |
| 상세 결정 기록 | [ADR 0014](adr/0014-application-logging-and-metrics.md) |

## 마지막으로 기억할 문장

```text
메트릭은 “어디가 이상한가?”를 찾고,
로그는 “왜 이상한가?”를 찾으며,
Request ID는 “이 로그들이 같은 요청인가?”를 연결한다.
```
