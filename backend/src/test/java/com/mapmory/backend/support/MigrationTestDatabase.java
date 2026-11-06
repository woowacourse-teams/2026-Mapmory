package com.mapmory.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 마이그레이션 테스트용 빈 데이터베이스.
 *
 * 테스트마다 MySQL 컨테이너를 새로 띄우면 한 번에 10초씩 걸린다.
 * 공용 컨테이너 안에 테스트마다 새 데이터베이스를 만들고, 끝나면 지운다.
 *
 * {@link #jdbcTemplate()}은 커넥션 하나를 계속 쓴다. 쿼리마다 커넥션을 새로 열면
 * 수천 건을 넣는 테스트가 수십 초 걸린다. 커넥션은 {@link #close()}에서 닫는다.
 */
public final class MigrationTestDatabase implements AutoCloseable {

    private final MySQLContainer<?> container;
    private final String name;
    private final List<SingleConnectionDataSource> dataSources = new ArrayList<>();

    private MigrationTestDatabase(MySQLContainer<?> container, String name) {
        this.container = container;
        this.name = name;
    }

    public static MigrationTestDatabase create() {
        MySQLContainer<?> container = MySqlTestContainerSupport.sharedContainer();
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        execute(container, "CREATE DATABASE " + name);
        execute(container, "GRANT ALL PRIVILEGES ON " + name + ".* TO '" + container.getUsername() + "'@'%'");
        return new MigrationTestDatabase(container, name);
    }

    public String getJdbcUrl() {
        String url = container.getJdbcUrl();
        return url.replace("/" + container.getDatabaseName(), "/" + name);
    }

    public String getUsername() {
        return container.getUsername();
    }

    public String getPassword() {
        return container.getPassword();
    }

    public JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(getJdbcUrl(), getUsername(), getPassword(), true);
        dataSources.add(dataSource);
        return new JdbcTemplate(dataSource);
    }

    @Override
    public void close() {
        dataSources.forEach(SingleConnectionDataSource::destroy);
        execute(container, "DROP DATABASE IF EXISTS " + name);
    }

    private static void execute(MySQLContainer<?> container, String sql) {
        try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), "root", container.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
