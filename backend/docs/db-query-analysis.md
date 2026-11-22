# 느린 요청과 쿼리 찾기

느린 요청의 원인이 SQL 수(N+1)인지, SQL 하나인지, DB 밖(S3·외부 API)인지 가르는 방법이다. 결정과 기준의 근거는 ADR 0032에 있다.

| 어디서 | 무엇으로 | 무엇을 본다 |
|---|---|---|
| dev·prod | 앱 로그 `SLOW_REQUEST`, `SLOW_QUERY` + CloudWatch Logs Insights | 요청별 SQL 수·DB 시간, 100ms를 넘은 SQL의 종류·테이블 |
| 로컬 | Docker MySQL 8.4.10 + `performance_schema` | 쿼리(다이제스트)별 총 시간·횟수·훑은 행 수 |
| dev RDS | `EXPLAIN` / `EXPLAIN ANALYZE` | 고른 SQL이 실제 데이터에서 인덱스를 타는지. 설정 변경 없음 |
| dev RDS (선택) | 느린 쿼리 로그, `performance_schema` | 모든 SQL의 순위가 꼭 필요할 때만. prod에는 적용하지 않는다 |

## 1. 앱 로그

### 로그 형식

`SLOW_REQUEST`는 요청이 끝날 때 기준을 넘었으면 한 줄 남는다(INFO).

| 필드 | 뜻 |
|---|---|
| `requestId` | 요청 ID(MDC). 같은 요청의 다른 로그와 잇는다 |
| `method`, `uri` | `uri`는 경로 패턴이다(`/api/v1/travel-records/{travelRecordId}`). 원본 경로는 남기지 않는다 |
| `status` | 응답 상태 코드 |
| `sloClass` | `read`·`write`·`auth`·`other` (ADR 0031) |
| `latencyMs` | 요청 시간 |
| `dbQueryCount` | 이 요청 스레드가 실행한 SQL 수(JDBC 실행 한 번 = 1) |
| `dbTimeMs` | 그 SQL들이 걸린 시간의 합. 커넥션을 기다린 시간은 빠진다 |

`SLOW_QUERY`는 요청 스레드에서 SQL 하나가 기준을 넘으면 한 줄 남는다(INFO).

| 필드 | 뜻 |
|---|---|
| `requestId`, `uri` | 위와 같다. 컨트롤러를 찾기 전(필터 안)에 실행된 SQL은 `uri=UNKNOWN` |
| `elapsedMs` | 그 SQL 하나의 시간 |
| `queryType` | `SELECT`·`INSERT`·`UPDATE`·`DELETE`·`OTHER` |
| `table` | SELECT·DELETE는 첫 `FROM`, INSERT는 `INTO`, UPDATE는 `UPDATE` 바로 뒤의 테이블 |

두 로그 모두 SQL 원문과 파라미터는 남기지 않는다.

### 기준과 설정

| 설정 | 환경변수 | 기본 | 0이면 |
|---|---|---|---|
| `monitoring.db.enabled` | `DB_MONITORING_ENABLED` | true (dev·prod 모두) | `false`면 계측 전체를 뺀다(DataSource를 감싸지 않고 두 로그도 없음) |
| `monitoring.slow-request.latency-threshold` | `SLOW_REQUEST_LATENCY_THRESHOLD` | 비움 = 요청의 SLO 기준(read 300ms·write 2s·auth 1s·other 1s) | 모든 요청을 남긴다 |
| `monitoring.slow-request.query-count-threshold` | `SLOW_REQUEST_QUERY_COUNT_THRESHOLD` | 0 | 쓰지 않는다 |
| `monitoring.slow-query.threshold` | `SLOW_QUERY_THRESHOLD` | 100ms | 모든 SQL을 남긴다 |

### dev에서 모든 요청 남기기

1. 서버의 `/etc/mapmory.env`에 한 줄 더한다.

   ```bash
   SLOW_REQUEST_LATENCY_THRESHOLD=0ms
   ```

2. 지금 트래픽을 받는 슬롯의 서비스를 재시작한다(`mapmory` 또는 `mapmory-green`). dev에서는 재시작하는 동안 요청이 실패해도 된다.
3. 앱 화면 흐름대로 부른다.
4. 아래 Logs Insights 쿼리로 표를 뽑는다.
5. 끝나면 그 줄을 지우고 다시 재시작한다. 기준이 SLO로 돌아온다.

prod에서는 기준을 낮추지 않는다. 요청마다 한 줄씩 남아 로그가 크게 는다.

### 계측 빼기

`event=DB_MONITORING_FAILED` WARN이 계속 나거나 계측 때문에 문제가 의심되면, `/etc/mapmory.env`에 `DB_MONITORING_ENABLED=false`를 넣고 트래픽을 받는 슬롯을 재시작한다. 재배포는 필요 없다. 고친 뒤 그 줄을 지우고 다시 재시작한다.

### CloudWatch Logs Insights

로그 그룹은 `/mapmory/<env>/application`이다.

API별 표(호출 수, 지연, SQL 수, DB 시간):

```
fields @timestamp
| filter event = "SLOW_REQUEST"
| stats count(*) as calls,
        avg(latencyMs) as avgLatencyMs, max(latencyMs) as maxLatencyMs,
        avg(dbQueryCount) as avgQueries, max(dbQueryCount) as maxQueries,
        avg(dbTimeMs) as avgDbTimeMs
  by method, uri
| sort calls desc
```

DB 밖이 느린 요청(요청 시간 중 DB가 30%도 안 되는 요청):

```
fields @timestamp, requestId, method, uri, latencyMs, dbTimeMs, dbQueryCount
| filter event = "SLOW_REQUEST" and latencyMs >= 50
| fields dbTimeMs / latencyMs as dbShare
| filter dbShare < 0.3
| sort latencyMs desc
| limit 50
```

`latencyMs >= 50`은 기준을 0으로 낮췄을 때 몇 ms짜리 요청이 비율 때문에 섞이지 않게 거른다.
나온 `requestId`로 같은 요청의 다른 로그(S3, 외부 API 오류 등)를 찾는다.

uri별 느린 SQL:

```
fields @timestamp
| filter event = "SLOW_QUERY"
| stats count(*) as slowQueries, avg(elapsedMs) as avgMs, max(elapsedMs) as maxMs
  by uri, queryType, table
| sort slowQueries desc
```

## 2. 로컬 MySQL에서 쿼리별로 보기

RDS(db.t4g.micro)와 같은 버전(8.4.10)·버퍼 풀(128M)로 띄우고, RDS에서는 꺼져 있는 `performance_schema`만 켠다.
평소 개발 DB(`db`, 3306)와 따로 3307번에 뜬다.

```bash
cd backend
docker compose --profile analysis up -d db-analysis

# 앱을 이 DB에 붙인다(local 프로파일이 마이그레이션과 로컬 시드를 넣는다).
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/mapmory?serverTimezone=UTC&characterEncoding=UTF-8' \
  ./gradlew bootRun
```

켜졌는지 확인한다.

```sql
SHOW VARIABLES WHERE Variable_name IN ('version', 'performance_schema', 'innodb_buffer_pool_size');
```

### 측정 전에 합계 비우기

```sql
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
```

비운 뒤 잴 흐름만 부른다. 앱 기동 중 Flyway·시드 SQL이 섞이지 않는다.

### 쿼리별 합계 상위 20개

```sql
SELECT
    DIGEST_TEXT,
    COUNT_STAR                                          AS calls,
    ROUND(SUM_TIMER_WAIT / 1e9, 1)                      AS total_ms,
    ROUND(AVG_TIMER_WAIT / 1e9, 2)                      AS avg_ms,
    ROUND(MAX_TIMER_WAIT / 1e9, 1)                      AS max_ms,
    SUM_ROWS_EXAMINED                                   AS rows_examined,
    SUM_ROWS_SENT                                       AS rows_sent,
    ROUND(SUM_ROWS_EXAMINED / NULLIF(SUM_ROWS_SENT, 0)) AS examined_per_sent,
    SUM_NO_INDEX_USED                                   AS no_index_used
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'mapmory'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 20;
```

- 시간 단위는 피코초라 `1e9`로 나눠 ms로 본다.
- `total_ms`가 크면 먼저 본다. 평균이 작아도 많이 불리면 위로 온다(N+1).
- `examined_per_sent`(훑은 행 ÷ 돌려준 행)가 크거나 `no_index_used`가 0이 아니면 인덱스를 못 타는 쿼리다. `EXPLAIN`으로 확인한다.
- UPDATE·DELETE는 돌려준 행이 0이라 `examined_per_sent`가 비고, `SUM_ROWS_AFFECTED`와 견준다.
- `DIGEST_TEXT`는 값이 `?`로 바뀐 SQL이다. 로컬 데이터라도 결과를 저장소나 PR에 붙일 때는 값이 없는지 본다.

## 3. RDS에서 특정 SQL의 인덱스 사용 보기

RDS 설정을 바꾸지 않아도 된다. 앱 로그로 고른 API의 SQL을 실제 데이터에서 본다.

1. 로컬에서 그 API를 부르고 show-sql(`spring.jpa.show-sql=true`)로 실제 SQL을 뽑는다.
2. dev RDS에 붙어 `?` 자리에 값을 넣고 돌린다.

   ```sql
   EXPLAIN ANALYZE SELECT ...;
   ```

   - `type=ALL`(EXPLAIN)이나 `Table scan`(EXPLAIN ANALYZE)이면 인덱스를 못 탄다.
   - 단계별 `actual rows`가 돌려준 행보다 훨씬 크면 많이 훑는다.
3. `EXPLAIN ANALYZE`는 쿼리를 실제로 실행한다. **dev에서 SELECT로만 쓰고 prod에서는 쓰지 않는다.** 실행 계획만 보려면 `EXPLAIN`을 쓴다.

결과를 PR이나 이슈에 붙일 때는 넣은 값을 지운다.

## 4. (선택) dev RDS에서 느린 쿼리 로그·`performance_schema` 켜기

모든 SQL을 모은 순위가 꼭 필요할 때만 dev에서 한다. 특정 SQL은 3절로 충분하다. **prod에는 적용하지 않는다.**

- 느린 쿼리 로그는 SQL을 값까지 그대로 남긴다. 개인정보가 들어간다.
- 새 파라미터 그룹을 붙일 때 한 번 재부팅한다. RDS가 단일 AZ라 그동안(수 분) 서비스가 멈춘다. 그 뒤 동적 값(`slow_query_log`, `long_query_time`)은 재부팅 없이 바뀐다.

필요한 권한: `rds:CreateDBParameterGroup`, `rds:ModifyDBParameterGroup`, `rds:ModifyDBInstance`, `rds:RebootDBInstance`.
로그를 보려면 `rds:DescribeDBLogFiles`, `rds:DownloadDBLogFilePortion`도 필요하다.

1. 기본 그룹(`default.mysql8.4`)은 고칠 수 없으므로 같은 계열(`mysql8.4`)로 새 파라미터 그룹을 만든다.
2. 새 그룹에서 바꾼다.
   - `slow_query_log=1`
   - `long_query_time=0.1`
   - `log_output=FILE`
3. dev DB 인스턴스가 새 그룹을 쓰게 바꾼다.
4. 재부팅한다. 파라미터 그룹을 바꾸면 재부팅해야 반영된다.
5. 콘솔의 로그 탭 또는 `download-db-log-file-portion`으로 `slowquery/mysql-slowquery.log`를 받는다.
6. 끝나면 `slow_query_log=0`으로 바꾼다. 동적 값이라 재부팅하지 않는다. 그룹은 그대로 두면 다음에 재부팅 없이 다시 켤 수 있다.

`performance_schema=1`도 같은 그룹에서 켤 수 있다. 정적 값이라 켜고 끌 때마다 재부팅한다. 1GB 인스턴스에서 메모리를 더 쓰므로, 켜면 CloudWatch `FreeableMemory`를 보고 크게 줄면 끈다.
