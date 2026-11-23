package com.mapmory.backend.common.monitoring.db;

import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * 요청 하나가 DB에 보낸 SQL 실행 수와 그 시간의 합.
 *
 * <p>요청이 시작될 때 그 요청 스레드에 하나 만들고({@link #start}), 끝날 때 지운다({@link #finish}). DataSource를 감싼
 * {@link RequestDbUsageListener}가 SQL을 실행할 때마다 지금 스레드의 것({@link #current})에 더한다. 그래서 동시에 들어온 요청끼리
 * 섞이지 않고, 요청 스레드 밖(아웃박스 워커, 배치, 기동 중 Flyway)의 SQL은 세지 않는다.
 *
 * <p>한 스레드에서만 쓰므로 동기화하지 않는다.
 */
final class RequestDbUsage {

    static final String UNKNOWN_URI = "UNKNOWN";

    private static final ThreadLocal<RequestDbUsage> CURRENT = new ThreadLocal<>();

    private final ServerRequestObservationContext context;
    private final long startNanos;
    private int queryCount;
    private long dbTimeNanos;
    private long queryStartNanos;
    private boolean queryRunning;

    private RequestDbUsage(ServerRequestObservationContext context, long startNanos) {
        this.context = context;
        this.startNanos = startNanos;
    }

    static RequestDbUsage start(ServerRequestObservationContext context, long nowNanos) {
        RequestDbUsage usage = new RequestDbUsage(context, nowNanos);
        CURRENT.set(usage);
        return usage;
    }

    static RequestDbUsage current() {
        return CURRENT.get();
    }

    void finish() {
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }

    void queryStarted(long nowNanos) {
        queryStartNanos = nowNanos;
        queryRunning = true;
    }

    /**
     * SQL 하나가 끝났다. 실행 수에 1을 더하고 걸린 시간을 돌려준다. 시작을 보지 못한 실행이면 세지 않고 -1을 돌려준다.
     */
    long queryFinished(long nowNanos) {
        if (!queryRunning) {
            return -1;
        }
        queryRunning = false;
        long elapsedNanos = nowNanos - queryStartNanos;
        queryCount++;
        dbTimeNanos += elapsedNanos;
        return elapsedNanos;
    }

    /**
     * 지금 처리 중인 요청의 경로 패턴. 컨트롤러를 찾기 전(필터 안)이면 {@value #UNKNOWN_URI}다. 원본 경로는 돌려주지 않는다.
     */
    String uriPattern() {
        String pattern = context.getPathPattern();
        return pattern == null ? UNKNOWN_URI : pattern;
    }

    long startNanos() {
        return startNanos;
    }

    int queryCount() {
        return queryCount;
    }

    long dbTimeNanos() {
        return dbTimeNanos;
    }
}
