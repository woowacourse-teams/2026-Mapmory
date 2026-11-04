package com.mapmory.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.testcontainers.containers.MySQLContainer;

/**
 * 마이그레이션 테스트용 빈 데이터베이스.
 *
 * 테스트마다 MySQL 컨테이너를 새로 띄우면 한 번에 10초씩 걸린다.
 * 공용 컨테이너 안에 테스트마다 새 데이터베이스를 만들고, 끝나면 지운다.
 */
public final class MigrationTestDatabase implements AutoCloseable {

    private final MySQLContainer<?> container;
    private final String name;

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

    @Override
    public void close() {
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
