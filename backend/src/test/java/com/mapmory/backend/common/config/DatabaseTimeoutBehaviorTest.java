package com.mapmory.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.mapmory.backend.support.MigrationTestDatabase;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * prod 앱 연결 주소의 타임아웃 옵션(socketTimeout, 잠금 대기 한도)이 실제 MySQL에서 의도한 순서로 동작하는지 확인한다.
 *
 * <p>{@link DatabaseTimeoutConfigurationTest}는 yaml 값의 크기 관계만 본다. 여기서는 prod yaml의 주소 옵션을 그대로 떼어
 * 테스트 MySQL에 붙여, 잠금 대기 초과(1205)가 소켓 타임아웃보다 먼저 오고 연결이 살아 있는지, 응답 없는 연결이 실제로 몇 초에 끊기는지 본다.
 */
class DatabaseTimeoutBehaviorTest {

    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(3);

    @Test
    void 앱_연결의_잠금_대기_한도는_1초다() throws Exception {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create();
             Connection app = appConnection(mysql);
             Statement statement = app.createStatement();
             ResultSet result = statement.executeQuery("SELECT @@innodb_lock_wait_timeout")) {
            result.next();

            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void 잠긴_행을_기다리면_소켓_타임아웃보다_먼저_1205로_실패하고_연결은_살아_있다() throws Exception {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create()) {
            mysql.jdbcTemplate().execute("CREATE TABLE lock_target (id BIGINT PRIMARY KEY, value INT NOT NULL)");
            mysql.jdbcTemplate().execute("INSERT INTO lock_target VALUES (1, 0)");

            try (Connection holder = plainConnection(mysql); Connection app = appConnection(mysql)) {
                holder.setAutoCommit(false);
                try (Statement lock = holder.createStatement()) {
                    lock.executeQuery("SELECT id FROM lock_target WHERE id = 1 FOR UPDATE").close();
                }

                long start = System.nanoTime();
                SQLException failure;
                try (Statement waiting = app.createStatement()) {
                    failure = catchThrowableOfType(
                            SQLException.class,
                            () -> waiting.executeUpdate("UPDATE lock_target SET value = 1 WHERE id = 1")
                    );
                }
                Duration waited = Duration.ofNanos(System.nanoTime() - start);

                assertThat((Throwable) failure).isNotNull();
                assertThat(failure.getErrorCode()).isEqualTo(MYSQL_LOCK_WAIT_TIMEOUT);
                assertThat(waited.toMillis()).isLessThan(SOCKET_TIMEOUT.toMillis());
                // 1205는 그 문장만 실패시킨다. 연결이 끊겼다면 소켓 타임아웃이 먼저 온 것이다.
                assertThat(app.isValid(1)).isTrue();

                holder.rollback();
            }
        }
    }

    /**
     * 드라이버는 읽기가 socketTimeout에 걸리면 연결을 닫으면서 남은 응답을 한 번 더 읽는다
     * ({@code NetworkResources.forceClose}). 서버가 계속 답이 없으면 그 읽기도 socketTimeout만큼 기다려,
     * 요청 스레드가 풀려나는 것은 3초가 아니라 그 두 배인 6초쯤이다. (2026-11-23 CI에서 6,005ms로 확인)
     */
    @Test
    void 응답이_없으면_소켓_타임아웃의_두_배쯤에_연결을_끊는다() throws Exception {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create(); Connection app = appConnection(mysql)) {
            long start = System.nanoTime();
            SQLException failure;
            try (Statement slow = app.createStatement()) {
                failure = catchThrowableOfType(SQLException.class, () -> slow.executeQuery("SELECT SLEEP(20)"));
            }
            long waitedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat((Throwable) failure).isNotNull();
            assertThat(waitedMillis).isBetween(SOCKET_TIMEOUT.toMillis() - 200, SOCKET_TIMEOUT.toMillis() * 2 + 2_000);
            assertThat(app.isValid(1)).isFalse();
        }
    }

    private static Connection plainConnection(MigrationTestDatabase mysql) throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    /** prod yaml 앱 주소에서 타임아웃 옵션만 떼어 테스트 DB 주소에 붙인다. */
    private static Connection appConnection(MigrationTestDatabase mysql) throws SQLException, IOException {
        String url = mysql.getJdbcUrl();
        String separator = url.contains("?") ? "&" : "?";
        return DriverManager.getConnection(url + separator + timeoutOptions(), mysql.getUsername(), mysql.getPassword());
    }

    private static String timeoutOptions() throws IOException {
        String prodUrl = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yaml"))
                .stream()
                .map(source -> source.getProperty("spring.datasource.url"))
                .filter(value -> value != null)
                .map(String::valueOf)
                .findFirst()
                .orElseThrow();
        String options = prodUrl.substring(prodUrl.indexOf("socketTimeout="));

        assertThat(options).contains("socketTimeout=3000").contains("innodb_lock_wait_timeout=1");
        return options;
    }
}
