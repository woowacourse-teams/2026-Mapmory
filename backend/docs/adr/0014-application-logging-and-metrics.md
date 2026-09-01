# ADR 0014. 애플리케이션 로그와 메트릭에 공통 관측 규칙을 적용한다

- 상태: 채택
- 날짜: 2026-08-23
- 최종 갱신: 2026-08-31
- 관련: ADR 0001, ADR 0002, ADR 0006

---

## 문제

Mapmory는 Spring Boot 기본 로깅과 Micrometer·Actuator·Prometheus Registry를 사용한다.
현재 예상하지 못한 예외와 일부 비즈니스 예외는 로그로 남고, HTTP·JVM 메트릭은
`/actuator/prometheus`에 노출된다.

그러나 다음 운영 질문에 일관되게 답하기에는 정보가 부족하다.

- 같은 HTTP 요청에서 발생한 여러 로그를 어떻게 연결하는가?
- 어떤 사건을 `ERROR`, `WARN`, `INFO`, `DEBUG`로 기록하는가?
- 어떤 API와 내부 작업이 느리거나 실패했는가?
- 메트릭 태그가 무제한으로 늘어나는 것을 어떻게 방지하는가?
- 로그와 메트릭을 CloudWatch에서 안정적으로 검색·집계하려면 어떤 형식이 필요한가?

이 ADR은 애플리케이션의 관측 데이터 생성 규칙과 운영 CloudWatch 수집 경계를 결정한다.
운영 대시보드와 인프라 장애 알림은 구성되어 있으며, 서비스 수준 알람 기준은 후속 작업으로
남긴다.

## 현재 구현 요약

| 영역 | 현재 구현 | 확인 위치 |
| --- | --- | --- |
| 외부 HTTP | Spring Boot가 자동 구성한 `RestClient.Builder`를 사용해 연결·읽기 타임아웃과 `http.client.requests`를 적용한다. | `KakaoClientConfig`, `application.yaml` |
| 요청 추적 | 모든 요청에 UUID 형식의 `X-Request-Id`를 부여하고 MDC의 `requestId`에 저장한 뒤 요청 종료 시 제거한다. | `RequestIdFilter` |
| 예외 로그 | 미처리 예외, 응답값 검증 실패, `SERVICE_UNAVAILABLE`을 원인 예외와 함께 `ERROR`로 기록한다. 예상 가능한 비즈니스 예외는 `DEBUG`로 기록한다. | `common/handler` |
| 로그 형식 | 로컬은 사람이 읽는 콘솔 패턴, 운영은 Spring Boot Logstash JSON 형식을 사용한다. | `application.yaml`, `application-prod.yaml` |
| HTTP 메트릭 | `http.server.requests`의 p95를 애플리케이션에서 계산하고 `uri` 태그 값은 최대 50개까지만 등록한다. | `MetricsConfiguration`, `application.yaml` |
| 내부 작업 메트릭 | `mapmory.operation.duration`으로 미디어 동기화와 지도 요약 쿼리 시간을 성공·실패별로 기록한다. | `OperationTimer`, `MonitoredOperation` |
| 메트릭 노출 | `health`, `prometheus`만 읽기 전용으로 노출한다. 운영에서는 `127.0.0.1:8081`로 관리 포트를 분리한다. | `application.yaml`, `application-prod.yaml` |
| 운영 수집 | EC2의 CloudWatch Agent가 애플리케이션 로그와 Prometheus 메트릭을 CloudWatch로 전송한다. 애플리케이션 로그 그룹은 7일, Prometheus EMF 로그 그룹은 14일간 보관한다. | EC2 CloudWatch Agent, `/mapmory/prod/application`, `/mapmory/prod/prometheus-emf` |
| 대시보드·알림 | 운영 대시보드, EC2·RDS 인프라 알람과 SNS 알림 채널을 구성한다. | `dashboard-mapmory-prod`, `mapmory-prod-alerts` |

애플리케이션 로그와 Prometheus 메트릭의 CloudWatch 수집은 운영 환경에 적용되어 있다.
CloudWatch 대시보드와 인프라 장애 알림도 적용되어 있다.

## 결정

### 로그와 메트릭의 책임을 분리한다

- **로그**는 개별 사건의 문맥과 실패 원인을 조사하는 데 사용한다.
- **메트릭**은 요청 수, 오류율, 지연 시간처럼 시간에 따른 수치를 집계하는 데 사용한다.
- **Prometheus 엔드포인트**는 Micrometer가 만든 메트릭을 외부 수집기가 읽을 수 있는
  형식으로 노출한다. Prometheus 서버를 EC2에 설치하거나 데이터를 저장하는 역할은 하지 않는다.

정상 요청마다 시작·종료 로그를 남겨 응답 시간을 계산하지 않는다. 전체 요청의 응답 시간과
성공률은 메트릭으로 측정하고, 로그는 중요한 상태 변경과 실패에 집중한다.

### 로그 레벨 경계를 통일한다

| 레벨 | 경계 | Mapmory 예시 |
| --- | --- | --- |
| `ERROR` | 요청이 서버 내부 또는 의존 시스템 문제로 완료되지 못했고 개발자 조치가 필요하다. 원인 예외를 함께 기록한다. | 미처리 예외, DB·S3 실패, 컨트롤러 반환값 검증 실패, 카카오 장애로 인한 503 |
| `WARN` | 서버는 동작하지만 보안·용량·반복 발생 여부를 관찰해야 한다. | 폐기된 refresh 토큰 재사용, 재시도 발생, DB 연결 대기 |
| `INFO` | 정상 흐름 중 운영상 의미 있는 상태 변경이다. | 여행 기록 생성·수정·삭제, 신규 회원 생성, 서버 시작·종료 |
| `DEBUG` | 예상 가능한 실패 또는 개발 중 필요한 상세 정보다. 운영 기본 설정에서는 출력하지 않는다. | 입력 오류, 리소스 없음, 일반적인 토큰 만료, 내부 분기 결과 |

HTTP 상태만으로 로그 레벨을 기계적으로 정하지 않는다. 일반적인 4xx는 서버 장애가 아니므로
`WARN`이나 `ERROR`로 남기지 않는다. 같은 예외의 스택 트레이스는 최종 처리 경계에서 한 번만
기록한다.

### 모든 HTTP 요청에 Request ID를 부여한다

Servlet Filter에서 요청별 Request ID를 결정한다.

1. 요청의 `X-Request-Id`가 허용된 길이와 형식이면 사용한다.
2. 헤더가 없거나 유효하지 않으면 서버가 UUID를 생성한다.
3. 값을 MDC의 `requestId`에 넣어 같은 요청에서 발생하는 모든 로그에 자동으로 포함한다.
4. 응답의 `X-Request-Id` 헤더에도 같은 값을 넣는다.
5. `MDC.putCloseable()`과 try-with-resources를 사용해 요청 처리가 끝나면 MDC 값을 제거한다.
   정상·예외 흐름 모두에서 정리하여 스레드 풀의 다음 요청으로 값이 누출되지 않게 한다.

예를 들어 여행 기록 생성 중 미디어 저장이 실패하면 다음 로그가 같은 요청임을 알 수 있다.

```text
INFO  requestId=550e8400-e29b-41d4-a716-446655440000 ...
ERROR requestId=550e8400-e29b-41d4-a716-446655440000 event=BUSINESS_EXCEPTION errorCode=SERVICE_UNAVAILABLE
```

클라이언트가 오류와 함께 `X-Request-Id: 550e8400-e29b-41d4-a716-446655440000`을 전달하면
개발자는 같은 `requestId`로 요청 로그를 검색할 수 있다. Request ID는 추적용 로그 필드이며
메트릭 태그로 사용하지 않는다.

### 운영 로그는 구조화된 JSON으로 출력한다

로컬 환경은 사람이 읽기 쉬운 기본 콘솔 형식을 유지하고, 운영 환경은 Spring Boot가 지원하는
Logstash JSON 형식을 사용한다. 별도 로깅 인코더 의존성은 추가하지 않는다.

공통 필드는 다음과 같다.

| 필드 | 용도 |
| --- | --- |
| `service`, `environment`, `version` | 실행 환경과 배포 버전 식별 |
| `requestId` | 단일 요청 추적 |
| `event` | `BUSINESS_EXCEPTION`, `UNHANDLED_EXCEPTION`, `RESPONSE_VALIDATION_FAILED`와 같은 사건 식별 |
| `errorCode` | 비즈니스 실패 유형 식별 |
| `status`, `httpMethod`, `uri` | 실패한 HTTP 요청의 최소 문맥 |
| `stack_trace` | 원인 예외가 필요한 `ERROR` 로그의 스택 트레이스 |

문장 안에 값을 섞은 비구조 로그와 달리 JSON 로그는 수집기가 정규식 없이 필드별 검색과
집계를 할 수 있다. MDC 값과 SLF4J fluent API의 key-value를 JSON 필드로 기록한다.

현재 구조화 필드는 예외 처리 경계부터 적용한다. 업무 이벤트 로그와 `memberId`,
`travelRecordId` 같은 추가 문맥 필드는 실제 운영 검색 요구가 확인된 뒤 최소 범위로 확장한다.

다음 값은 로그에 기록하지 않는다.

- access token, refresh token, 카카오 access token
- Presigned URL과 요청·응답 전체 본문
- 여행 제목·본문, 닉네임과 같은 불필요한 개인정보
- 비밀번호, DB 연결 문자열, 암호화 키

### HTTP 메트릭은 기존 자동 계측을 사용한다

Spring MVC가 자동 생성하는 `http.server.requests`를 요청 수, HTTP 상태, 전체 응답 시간의
기준으로 사용한다. URI는 `/travel-records/{id}`처럼 템플릿으로 집계하고 실제 ID가 포함된
경로를 태그로 만들지 않는다.

CloudWatch Agent의 Prometheus-to-CloudWatch 경로는 Histogram을 버리고 Counter, Gauge, Summary만
지원한다. 따라서 SLO 누적 버킷 대신 `0.95` 클라이언트 percentile을 설정해 p95를 Summary로
노출한다. 실행 횟수와 누적 시간은 `_count`, `_sum`, 최대 시간은 `_max`, p95는
`quantile="0.95"`인 기본 시계열로 확인한다.

클라이언트 percentile은 태그나 여러 인스턴스 사이에서 합산할 수 없다. 현재는 운영 EC2 한 대의
URI별 지연을 보는 용도로 사용한다. 다중 인스턴스로 확장할 때는 Histogram을 지원하는 수집·조회
경로로 전환하거나 지연 시간 집계 방식을 다시 결정한다.
`http.server.requests`의 `uri` 태그는 `MeterFilter.maximumAllowableTags`로 서로 다른 값 50개까지만
등록한다. 51번째 새로운 URI 값부터는 요청 자체를 막지 않고 해당 메트릭 등록과 측정만 거부한다.
이 제한은 비정상 URI 유입으로 인한 메모리·시계열 증가를 막는 안전장치이며, URI 템플릿 집계가
깨진 근본 원인을 대신하지 않는다.

### 중요한 내부 작업은 Micrometer Timer로 측정한다

전체 API 응답 시간만으로 병목 지점을 알 수 없는 작업에 공통 `OperationTimer`를 적용한다.
`Supplier<T>`로 작업을 전달하고 `Timer.Sample`로 시간을 시작한다. 정상 반환 시 `SUCCESS`,
`RuntimeException` 또는 `Error` 발생 시 `FAILURE` Timer를 종료한 뒤 원래 결과나 예외를 그대로
전달한다. 따라서 계측이 기존 업무 흐름을 변경하지 않는다.

공통 메트릭 이름은 `mapmory.operation.duration`으로 하고 다음의 제한된 태그만 사용한다.

- `operation`: `MonitoredOperation` enum의 `MEDIA_SYNC`, `MAP_SUMMARY_QUERY`
- `outcome`: `SUCCESS`, `FAILURE`

현재 측정 경계는 다음과 같다.

| operation | 시작과 종료 | 포함하지 않는 범위 | 주의점 |
| --- | --- | --- | --- |
| `MEDIA_SYNC` | 미디어 동기화 시작부터 `travelRecordRepository.flush()` 완료까지 | 앞선 여행 기록·미디어 조회와 검증, 응답 DTO 변환 | `flush()`는 현재 영속성 컨텍스트의 다른 미반영 변경도 함께 DB에 반영할 수 있다. |
| `MAP_SUMMARY_QUERY` | 지도 요약 Repository 호출 시작부터 조회 결과 반환까지 | 부모 Region 검증, 조회 결과의 응답 DTO 변환 | DB 조회 병목만 분리해 본다. |

내부 작업에도 `0.95` 클라이언트 percentile을 적용해 CloudWatch Agent가 지원하는 Summary로
노출한다. p95는 각 `operation`, `outcome` 조합별로 계산하며 서로 합산하지 않는다.

새 측정 대상은 모든 private 메서드에 일괄 적용하지 않는다. 외부 시스템, DB, 중요한 업무 단계처럼
운영 중 병목 원인을 분리할 가치가 있는 구간만 `MonitoredOperation` enum에 추가한다.

Timer는 요청별 원본 시간을 영구 저장하지 않고 호출 횟수, 총 시간, 최대값과 최근 관측값을
이용한 p95를 집계한다. `operation`과 `outcome`은 메트릭 집계용이며 `requestId`는 넣지 않는다.
개별 실패의 상세 원인은 같은 시간대의 `requestId`가 있는 구조화 로그에서 확인한다.

### 메트릭의 카디널리티를 제한한다

Micrometer가 JVM 안에서 메트릭을 계산하거나 `/actuator/prometheus`로 노출하는 것 자체에는
CloudWatch 요금이 발생하지 않는다. 다만 메트릭 시계열은 애플리케이션 메모리를 사용하고,
후속 단계에서 CloudWatch로 전송하면 커스텀 메트릭과 데이터 수집 비용이 발생할 수 있다.

메트릭 시계열은 대략 `메트릭 이름 × 태그 값 조합 × 통계 종류`만큼 늘어난다. 따라서 값의
종류가 제한된 low-cardinality 태그만 허용한다.

허용:

- `operation`의 고정 enum 값
- `SUCCESS`·`FAILURE`
- HTTP 메서드, 상태 코드 또는 상태 코드 그룹

금지:

- `requestId`, `memberId`, `travelRecordId`
- 실제 URI, 파일명, object key
- 예외 메시지나 임의 문자열

클라이언트 percentile은 운영 판단에 필요한 HTTP 요청과 중요 내부 Timer에만 적용한다.
CloudWatch로 전송하는 메트릭과 시계열 수를 정기적으로 확인하고 수집 대상 allowlist를
제한한다.

### Prometheus 서버는 현재 EC2에 설치하지 않는다

운영 EC2는 `t4g.small` 한 대에서 Nginx와 Spring Boot를 실행할 계획이다. Prometheus 서버는
수집뿐 아니라 로컬 시계열 DB와 WAL을 관리하므로 같은 인스턴스에 추가하지 않는다.

애플리케이션은 운영 관리 포트의 `/actuator/prometheus`만 로컬 주소에 노출한다.
운영 EC2의 CloudWatch Agent가 이 엔드포인트를 주기적으로 읽어 설정된 메트릭을 전송한다.

## 확인 방법

### Request ID

로컬 서버 실행 후 응답 헤더를 확인한다.

```bash
curl -i http://localhost:8080/health
```

응답에 UUID 형식의 `X-Request-Id`가 있어야 한다. 유효한 UUID를 요청 헤더로 전달하면 응답에도
같은 값이 반환되어야 한다.

```bash
curl -i \
  -H 'X-Request-Id: 550e8400-e29b-41d4-a716-446655440000' \
  http://localhost:8080/health
```

### Prometheus 메트릭

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests_seconds|mapmory_operation_duration_seconds'
```

`mapmory.operation.duration`은 해당 업무 기능을 한 번 이상 실행한 뒤 생성된다. 주요 출력은 다음과
같다.

| Prometheus suffix | 의미 |
| --- | --- |
| `_count` | 작업 실행 횟수 |
| `_sum` | 누적 실행 시간(초) |
| `_max` | 관측 구간의 최대 실행 시간(초) |
| suffix 없음 + `quantile="0.95"` | 해당 태그 조합의 p95 실행 시간(초) |

내부 작업은 `operation`, `outcome` 태그로 구분한다.

```text
mapmory_operation_duration_seconds_count{operation="MEDIA_SYNC",outcome="SUCCESS",...} 3
mapmory_operation_duration_seconds_count{operation="MAP_SUMMARY_QUERY",outcome="FAILURE",...} 1
```

### 새 내부 작업을 계측할 때

1. `MonitoredOperation`에 종류가 제한된 enum 값을 추가한다.
2. 병목을 구분할 수 있는 최소 코드 구간만 `operationTimer.record(...)`로 감싼다.
3. 사용자 ID, 실제 URI, object key, 예외 메시지를 태그로 넣지 않는다.
4. 성공 시 결과가 그대로 반환되고 실패 시 원래 예외가 다시 전달되는지 테스트한다.
5. `/actuator/prometheus`에서 `operation`, `outcome`, `quantile="0.95"`를 확인한다.

## 구현 상태

- [x] 로그 레벨과 민감정보 정책을 예외 처리기에 반영
- [x] Request ID Filter와 요청 종료 시 MDC 정리
- [x] 운영 프로필의 Logstash JSON 구조화 로그
- [x] 자동 구성된 `RestClient` 외부 HTTP 메트릭과 타임아웃
- [x] `http.server.requests`의 p95 Summary와 URI 태그 상한
- [x] `mapmory.operation.duration`과 초기 2개 내부 작업 계측
- [x] 성공·실패 Timer, p95 설정, URI 카디널리티 테스트
- [x] CloudWatch Agent의 애플리케이션 로그·Prometheus 메트릭 수집 설정
- [x] `/mapmory/prod/application` 로그 그룹 보존 기간 7일 설정
- [x] `/mapmory/prod/prometheus-emf` 로그 그룹 보존 기간 14일 설정
- [x] CloudWatch 운영 대시보드와 EC2·RDS 인프라 알람·SNS 채널 구성
- [ ] API 오류율·지연과 내부 작업 메트릭의 서비스 수준 알람 기준 확정

## 검토한 대안

### 모든 API의 시작과 종료를 INFO 로그로 남긴다

채택하지 않았다. 정상 요청량에 비례해 로그 용량이 증가하고 중요한 사건이 묻힌다. 요청 수와
응답 시간은 메트릭으로 집계한다.

### Request ID와 사용자 ID를 메트릭 태그로 사용한다

채택하지 않았다. 요청과 사용자 수만큼 시계열이 증가해 애플리케이션 메모리와 후속 수집 비용이
통제되지 않는다. 고유 식별자는 로그에서만 검색한다.

### 모든 메서드 실행 시간을 AOP로 자동 측정한다

채택하지 않았다. 의미 없는 Timer와 태그 조합이 증가하고 병목 분석에 필요한 경계가 흐려진다.
외부 시스템, DB, 중요한 업무 단계만 명시적으로 측정한다.

### Prometheus와 Grafana를 운영 EC2에 함께 설치한다

채택하지 않았다. 단일 2 GiB EC2에서 애플리케이션과 모니터링 저장소가 메모리·디스크를
경쟁하고 운영 복잡도가 증가한다. Prometheus 형식만 유지하고 수집·저장은 관리형 CloudWatch로
분리한다.

## 결과

### 장점

- 메트릭의 `operation`·`outcome`으로 이상 구간을 찾고, 같은 시간대와 요청 경로의 Request ID
  로그로 개별 실패 원인을 좁힐 수 있다.
- 로그 레벨이 운영 의미에 맞게 통일되고 일반적인 4xx가 장애 로그를 오염시키지 않는다.
- API 전체 지연과 내부 병목 지점을 함께 관찰할 수 있다.
- 메트릭 태그와 percentile 적용 대상을 제한해 JVM 메모리와 후속 CloudWatch 비용을 통제한다.
- 애플리케이션 관측 규칙과 CloudWatch 인프라 구성을 분리해 단계적으로 도입할 수 있다.

### 비용과 주의점

- Filter, MDC, 구조화 로그와 내부 Timer 구현 및 테스트가 추가된다.
- 비동기 실행으로 요청 문맥을 넘길 경우 MDC 전파를 별도로 처리해야 한다.
- 로그에 내부 ID를 포함할 때도 접근 권한과 보관 기간을 제한해야 한다.
- p95 계산은 JVM 메모리와 시계열을 추가로 사용하며 여러 인스턴스나 태그 조합 사이에서
  합산할 수 없다.
- 로그만으로 완전한 감사 이력을 보장하지 않는다. 감사 로그 요구사항은 별도로 결정한다.

## 검증 원칙

- 요청에 `X-Request-Id`가 없으면 생성하고, 있으면 유효한 값만 수용한다.
- 정상·예외 응답 모두 동일한 Request ID를 응답하고 요청 종료 후 MDC를 비운다.
- 미처리 예외에는 Request ID와 원인 예외가 남고, 응답에는 내부 정보가 노출되지 않는다.
- 운영 로그가 한 사건당 하나의 JSON 객체로 출력되며 민감정보를 포함하지 않는다.
- 성공과 실패 모두 내부 Timer에 기록된다.
- 메트릭 태그에 고유 식별자나 실제 URI가 포함되지 않는다.
- `/actuator/prometheus`는 외부에 공개하지 않고 운영 관리 포트의 로컬 주소에서만 접근한다.

## 참고

- [Spring Boot: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Actuator: Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Micrometer: Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html)
- [Prometheus: Storage](https://prometheus.io/docs/prometheus/latest/storage/)
- [Amazon CloudWatch: Metrics concepts](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/cloudwatch_concepts.html)
- [Amazon CloudWatch Agent: Prometheus metric type conversion](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/ContainerInsights-Prometheus-metrics-conversion.html)
- [Amazon CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/)
