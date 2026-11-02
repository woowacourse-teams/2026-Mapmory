package com.mapmory.backend.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * 한 동작이 DB에 보낸 SQL 수를 센다. Hibernate 통계를 그 동작 동안만 켠다.
 *
 * <p>설정 값(hibernate.generate_statistics)으로 켜면 통합 테스트 컨텍스트를 따로 띄우게 되므로 실행 중에 켜고 끈다.
 * 통계는 컨텍스트 전체가 같이 쓰므로 테스트를 병렬로 돌리면 다른 테스트의 SQL까지 센다.
 */
public class SqlStatementCounter {

    private final Statistics statistics;

    public SqlStatementCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    public long count(Runnable action) {
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            action.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }
}
