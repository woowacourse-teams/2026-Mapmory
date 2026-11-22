package com.mapmory.backend.common.monitoring.db;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataSource를 거치는 SQL 실행마다 불린다. 요청 스레드에서 실행된 것만 그 요청의 {@link RequestDbUsage}에 센다.
 *
 * <p>JDBC 실행 한 번(execute·executeQuery·executeUpdate·executeBatch)을 1로 센다. 시간은 실행 호출이 돌아올 때까지이고,
 * MySQL 드라이버는 결과를 모두 받은 뒤 돌아오므로 결과 전송까지 들어간다. 커넥션 풀에서 커넥션을 기다린 시간은 들어가지 않는다
 * (Hikari 지표 {@code hikaricp_connections_acquire}가 따로 잰다).
 *
 * <p>SQL 하나가 기준을 넘으면 SLOW_QUERY를 남긴다. SQL 원문과 파라미터는 남기지 않고 종류와 테이블만 남긴다({@link SqlSummary}).
 *
 * <p>datasource-proxy는 리스너의 예외를 잡지 않고 SQL을 실행한 쪽으로 던진다({@code afterQuery}는 {@code finally}에서 불려 SQL의
 * 원래 예외까지 가린다). 계측 때문에 업무 SQL이 실패하면 안 되므로 여기서 잡고 경고만 남긴다.
 */
public class RequestDbUsageListener implements QueryExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RequestDbUsageListener.class);

    private final DbMonitoringProperties.SlowQuery slowQuery;
    private final LongSupplier nanoTime;

    public RequestDbUsageListener(DbMonitoringProperties.SlowQuery slowQuery, LongSupplier nanoTime) {
        this.slowQuery = slowQuery;
        this.nanoTime = nanoTime;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        try {
            RequestDbUsage usage = RequestDbUsage.current();
            if (usage != null) {
                usage.queryStarted(nanoTime.getAsLong());
            }
        } catch (RuntimeException exception) {
            DbMonitoringFailure.warn(log, "beforeQuery", exception);
        }
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        try {
            recordFinished(queryInfoList);
        } catch (RuntimeException exception) {
            DbMonitoringFailure.warn(log, "afterQuery", exception);
        }
    }

    private void recordFinished(List<QueryInfo> queryInfoList) {
        RequestDbUsage usage = RequestDbUsage.current();
        if (usage == null) {
            return;
        }
        long elapsedNanos = usage.queryFinished(nanoTime.getAsLong());
        if (elapsedNanos < 0 || !slowQuery.isSlow(elapsedNanos)) {
            return;
        }
        SqlSummary summary = SqlSummary.of(queryInfoList.isEmpty() ? null : queryInfoList.getFirst().getQuery());
        log.atInfo()
                .addKeyValue("event", "SLOW_QUERY")
                .addKeyValue("uri", usage.uriPattern())
                .addKeyValue("elapsedMs", TimeUnit.NANOSECONDS.toMillis(elapsedNanos))
                .addKeyValue("queryType", summary.queryType())
                .addKeyValue("table", summary.table())
                .log("SQL 하나가 기준보다 오래 걸렸다");
    }
}
