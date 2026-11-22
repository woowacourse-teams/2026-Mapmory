# ADR 0032. 요청별 SQL 수와 DB 시간을 재고 느린 요청·쿼리를 로그로 남긴다

- 상태: 채택
- 날짜: 2026-11-22
- 관련: ADR 0014, ADR 0031

## 문제

느린 요청이 있어도 원인이 무엇인지 가를 수 없다.

- SQL을 많이 실행해서(N+1)
- SQL 하나가 느려서
- DB 밖(S3, 외부 API)이 느려서

지금 있는 숫자는 요청 단위 지연(p95 Summary, SLO 카운터 `http.server.requests.slo`)뿐이고 DB 쪽 숫자는 없다.
예를 들어 `GET /api/v1/travel-records/statistics`는 지도 화면을 열 때마다 불리고 캐시가 없다(2026-11-22 Nginx 로그).
이런 API가 SQL을 몇 번 실행하고 DB에서 얼마나 쓰는지 다른 API와 비교할 수 없다.

DB 쪽 도구도 쓰기 어렵다. dev·prod RDS는 같은 사양이다.

- db.t4g.micro(2 vCPU, 1GB), MySQL 8.4.10, 기본 파라미터 그룹(`default.mysql8.4`, 수정 불가)
- `innodb_buffer_pool_size` 128MB, `max_connections` 60
- `performance_schema` OFF, `slow_query_log` OFF(`long_query_time` 10초)

둘을 켜려면 새 파라미터 그룹과 재부팅이 필요하다. 1GB 인스턴스라 `performance_schema`의 메모리 부담도 있다.

## 결정

### 1. "느림"의 세 기준

| 무엇 | 기준 | 근거 |
|---|---|---|
| 요청 하나 | 그 요청의 `slo_class` SLO 기준: read 300ms, write 2초, auth 1초, other 1초 | ADR 0031 (prod 3주 실측). other는 SLO가 없어 로그 기준으로만 1초를 쓴다 |
| SQL 하나 | 100ms | 조회 SLO 300ms의 1/3. 지금 조회 API 평균이 11~30ms라 100ms짜리 SQL은 비정상이다 |
| 요청 하나의 SQL 수 | 정하지 않는다 (기본 꺼 둠) | API별 SQL 수를 아직 모른다. dev에서 잰 뒤 "평소 최댓값의 2배"로 정한다 |

- 로그를 남기는 기준과 고치는 기준은 다르다.
  - 로그 기준은 측정·부하 테스트 때 설정으로 0까지 낮춘다(`monitoring.slow-request.latency-threshold`, `monitoring.slow-query.threshold`).
  - 고치는 기준, 곧 인덱스·쿼리 작업의 합격선은 SLO(ADR 0031) 그대로다.
- 요청 기준값은 SLO와 한 곳(`SloClass`)에서 읽는다. ADR 0031 표를 바꾸면 로그 기준도 같이 바뀐다.

이 기준을 ADR 0031 2절(측정 방식)에 넣지 않고 따로 적는다.

- ADR 0031 2절은 SLO를 계산하는 지표와 알람을 다룬다.
- 여기 기준은 원인을 찾는 로그의 기준이라 알람·오류 예산과 관계가 없다.
- 도구 선택(아래 2절)과 SQL 수 기준처럼 다음 단계에서 바뀔 결정이 함께 있다. SLO 문서를 바꾸지 않고 이 문서만 고친다.

### 2. 요청마다 SQL 실행 수와 DB 시간을 datasource-proxy로 센다

- DataSource 빈을 datasource-proxy(`net.ttddyy:datasource-proxy`)로 감싼다(`DbMonitoringConfiguration`).
- 서버 요청 Observation이 시작될 때 요청 스레드에 `RequestDbUsage`를 두고, 끝날 때 지운다.
  - Observation은 `http.server.requests`·SLO 카운터와 같은 것이다.
  - SQL이 실행될 때마다 리스너가 지금 스레드의 `RequestDbUsage`에 1과 걸린 시간을 더한다.
  - 그래서 동시에 들어온 요청끼리 섞이지 않는다.
  - 요청 스레드 밖(아웃박스 워커, 배치, Flyway)의 SQL은 세지 않는다.
- JDBC 실행 한 번(`execute`·`executeQuery`·`executeUpdate`·`executeBatch`)을 1로 센다.
  - 시간은 실행 호출이 돌아올 때까지다. MySQL 드라이버는 결과를 모두 받은 뒤 돌아오므로 결과 전송까지 들어간다.
  - 커넥션 풀에서 커넥션을 기다린 시간은 들어가지 않는다. Hikari 지표(`hikaricp_connections_acquire`)가 따로 잰다.
- 범위는 요청 스레드다. 비동기 작업 계측은 하지 않는다.

비교한 것:

| 방식 | 요청 스레드 단위로 정확히 세나 | 운영 오버헤드 | 기존 Micrometer·Hikari 지표 | SQL 하나의 시간·종류 |
|---|---|---|---|---|
| **datasource-proxy (고름)** | 세다. 스레드에 둔 값에 더한다. Hibernate 밖 JDBC(Bucket4j 등)도 센다 | 실행마다 프록시 호출 한 번과 `System.nanoTime` 두 번 | 그대로다. Hikari는 JDBC `unwrap`으로 안쪽 풀을 찾는다(테스트로 확인) | 실행마다 시간과 SQL이 있어 SLOW_QUERY를 남길 수 있다 |
| Hibernate Statistics (`generate_statistics`) | 못 센다. SessionFactory 전체의 합계라 요청별로 나눌 수 없다 | 모든 세션·엔티티·쿼리의 통계를 모은다. 요청마다 쓰지 않는 값까지 모은다 | 바뀌지 않는다 | 쿼리별 합계뿐이다. 요청과 이을 수 없다 |
| Hibernate `SessionEventListener` | 세션 단위로 센다. `open-in-view: false`라 한 요청이 세션 여러 개를 쓰므로 결국 스레드에 다시 모아야 한다. Hibernate 밖 JDBC는 못 센다 | 작다 | 바뀌지 않는다 | 시간은 있지만 SQL이 없어 종류·테이블을 모른다 |
| Observation (datasource-micrometer) | 세다 | 커넥션·쿼리·결과마다 Observation을 만들어 datasource-proxy만 쓸 때보다 무겁다 | `jdbc.*` 지표가 새로 생겨 시계열과 EMF 비용 검토가 따로 필요하다 | 있다 |

- Hibernate 6.2부터 있는 `hibernate.log_slow_query`도 쓰지 않는다. 느린 SQL의 원문을 로그에 남기고, 요청 단위로 묶지 않는다.

### 3. SLOW_REQUEST 로그

- 요청이 끝날 때 기준을 넘었으면 한 줄 남긴다(INFO). 둘 중 하나라도 넘으면 남긴다.
  - 요청 시간이 1절의 요청 기준을 넘었다.
  - SQL 수가 `monitoring.slow-request.query-count-threshold`를 넘었다(0이면 쓰지 않는다).
- 필드: `event=SLOW_REQUEST`, `requestId`(MDC), `method`, `uri`, `status`, `sloClass`, `latencyMs`, `dbQueryCount`, `dbTimeMs`.
  - `uri`는 Observation이 정한 경로 패턴이다. 원본 경로를 쓰지 않아 초대 토큰이 남지 않는다.
  - SQL 원문과 파라미터는 남기지 않는다.
- INFO로 남겨 WARN·ERROR 대시보드 위젯과 Discord 알림에 섞이지 않게 한다.

### 4. SLOW_QUERY 로그

- 요청 스레드에서 SQL 하나가 `monitoring.slow-query.threshold`(기본 100ms)를 넘으면 한 줄 남긴다(INFO).
- 필드: `event=SLOW_QUERY`, `requestId`(MDC), `uri`(경로 패턴), `elapsedMs`, `queryType`(SELECT·INSERT·UPDATE·DELETE·OTHER), `table`.
  - `table`은 SELECT·DELETE는 첫 `FROM`, INSERT는 `INTO`, UPDATE는 `UPDATE` 바로 뒤의 이름이다. 조인한 테이블은 남기지 않는다.
  - 컨트롤러를 찾기 전(필터 안)에 실행된 SQL은 `uri=UNKNOWN`이다.
  - SQL 원문과 파라미터는 개인정보가 들어갈 수 있어 남기지 않는다.

### 5. 지표로는 내보내지 않는다

- 요청당 SQL 수 분포를 Micrometer 지표로 만들지 않는다.
  - 다음 단계에 필요한 표(API별 호출 수, 평균·최대 SQL 수, 평균 DB 시간)는 로그 기준을 0으로 낮추고 Logs Insights로 뽑을 수 있다.
  - 지표로 두면 `uri`별 시계열이 생겨 CloudWatch 비용(ADR 0031과 같은 기준)이나 Prometheus 시계열 수를 따로 따져야 한다.
- 알람이 필요해지면 그때 다시 본다.

### 6. 쿼리별 분석은 로컬 MySQL에서 한다

- RDS 설정은 바꾸지 않는다. 쿼리별 합계(`performance_schema.events_statements_summary_by_digest`)는 로컬 Docker MySQL에서 본다.
  - 버전은 RDS와 같은 8.4.10이고, `innodb_buffer_pool_size`도 RDS와 같은 128M이다.
  - `performance_schema`는 켜 둔다(`docker-compose.yml`의 `db-analysis`).
- RDS에서 느린 쿼리 로그를 켜는 절차는 선택으로만 문서에 둔다(`docs/db-query-analysis.md`). prod에는 적용하지 않는다.
  - 느린 쿼리 로그는 SQL을 값까지 그대로 남긴다.
  - 단일 AZ라 재부팅하는 동안 서비스가 멈춘다.

### 7. dev·prod 모두 켜고, 설정 하나로 뺄 수 있게 한다

- 계측은 dev·prod 모두 항상 돈다. 측정할 때만 dev에서 로그 기준을 0으로 낮추고, prod는 기준(SLO·100ms)을 넘은 것만 남긴다.
  - SLOW_REQUEST가 원인을 가르는 데 쓰이는 곳은 prod다. dev에서만 켜면 prod에서 느린 요청이 났을 때 가를 숫자가 없다.
- 운영의 모든 SQL이 지나는 길에 끼우는 코드라, 재배포 없이 뺄 수 있어야 한다.
  - `monitoring.db.enabled`(`DB_MONITORING_ENABLED`, 기본 `true`)가 `false`면 설정 전체가 빠진다. DataSource를 감싸지 않고 두 로그도 남기지 않는다.
  - `/etc/mapmory.env`에 `DB_MONITORING_ENABLED=false`를 넣고 재시작하면 된다. 값을 비우면 켜진 것으로 본다.
- 계측 코드의 예외는 업무 SQL·요청으로 올리지 않는다.
  - datasource-proxy는 리스너의 예외를 잡지 않고 SQL을 실행한 쪽으로 던진다. `afterQuery`는 `finally`에서 불려 SQL의 원래 예외까지 가린다.
  - Observation 핸들러의 예외는 요청을 처리하는 필터로 올라간다.
  - 그래서 리스너와 핸들러에서 잡고 `event=DB_MONITORING_FAILED`를 WARN으로 남긴다. WARN이라 알림에 잡힌다. 계속 나면 위 설정으로 빼고 고친다.

## 고려한 대안

- **RDS에서 `performance_schema`·느린 쿼리 로그를 켠다**: 파라미터 그룹을 새로 만들고 재부팅해야 한다. 1GB 인스턴스에서 메모리를 더 쓴다. 느린 쿼리 로그에는 SQL이 값까지 남는다. 요청과 이을 수도 없다(requestId가 없다). dev에서 필요할 때만 선택으로 켠다.
- **나머지 계측 방식**: 2절 표.

## 결과

- 느린 요청이 SQL을 많이 실행해서인지(`dbQueryCount`), DB에서 오래 걸려서인지(`dbTimeMs`), DB 밖이 느려서인지(`dbTimeMs ÷ latencyMs`가 낮음) 로그 한 줄로 가를 수 있다.
- 평소에는 SLO를 넘은 요청과 100ms를 넘은 SQL만 남아 로그가 거의 늘지 않는다.
- DataSource를 감싸는 비용이 실행마다 붙는다. 로컬에서 잰 차이는 PR에 적는다.
- 계측이 실패해도 SQL과 요청은 그대로 진행하고, 계측을 재시작만으로 뺄 수 있다(7절).
- DataSource 빈의 실제 타입이 `ProxyDataSource`가 된다. `HikariDataSource`로 주입받으면 기동이 실패하므로, 풀 설정이 필요하면 `DataSource`로 받아 `unwrap(HikariDataSource.class)`한다.

## 할 일

- [ ] dev에서 요청 기준을 0으로 낮추고 API별 SQL 수·DB 시간 표를 만든다(대용량 시드·인덱스 단계의 출발점)
- [ ] 그 표로 SQL 수 기준("평소 최댓값의 2배")을 정해 `query-count-threshold`에 넣는다
