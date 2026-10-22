package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@DisplayName("회원 닉네임 추가 마이그레이션")
class MemberNicknameMigrationTest {

    @Test
    @DisplayName("기존 회원은 이름과 관계없이 기본 이름을 닉네임으로 갖고, 이름은 그대로다")
    void fillsNicknameWithGeneratedName() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            migrateToVersion23(mysql);

            JdbcTemplate jdbcTemplate = jdbcTemplate(mysql);
            insertMember(jdbcTemplate, "00000000-0000-0000-0000-000000000024", "소현");
            insertMember(jdbcTemplate, "00000000-0000-0000-0000-000000000025", "회원12345");
            insertMember(jdbcTemplate, "00000000-0000-0000-0000-000000000026", "  ");

            migrateAll(mysql);

            assertThat(nickname(jdbcTemplate, "00000000-0000-0000-0000-000000000024")).matches("회원[1-9]\\d{4}");
            assertThat(nickname(jdbcTemplate, "00000000-0000-0000-0000-000000000025")).matches("회원[1-9]\\d{4}");
            assertThat(nickname(jdbcTemplate, "00000000-0000-0000-0000-000000000026")).matches("회원[1-9]\\d{4}");
            assertThat(name(jdbcTemplate, "00000000-0000-0000-0000-000000000024")).isEqualTo("소현");
        }
    }

    private void migrateToVersion23(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("23"))
                .load()
                .migrate();
    }

    private void migrateAll(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private JdbcTemplate jdbcTemplate(MySQLContainer<?> mysql) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        );
        return new JdbcTemplate(dataSource);
    }

    private void insertMember(JdbcTemplate jdbcTemplate, String uuid, String name) {
        jdbcTemplate.update("INSERT INTO member (uuid, name) VALUES (?, ?)", uuid, name);
    }

    private String nickname(JdbcTemplate jdbcTemplate, String uuid) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM member WHERE uuid = ?", String.class, uuid);
    }

    private String name(JdbcTemplate jdbcTemplate, String uuid) {
        return jdbcTemplate.queryForObject("SELECT name FROM member WHERE uuid = ?", String.class, uuid);
    }
}
