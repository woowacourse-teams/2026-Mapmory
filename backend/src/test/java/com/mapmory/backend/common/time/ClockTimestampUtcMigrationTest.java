package com.mapmory.backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@DisplayName("Clock으로 만든 시각을 UTC로 맞추는 마이그레이션")
class ClockTimestampUtcMigrationTest {

    // 개발 DB의 실제 행처럼 같은 순간을 updated_at은 UTC, Clock으로 만든 시각은 KST로 넣는다. (#502)
    private static final String UTC = "2026-10-27 03:59:07";
    private static final String KST = "2026-10-27 12:59:07";

    @Test
    @DisplayName("KST로 저장된 시각을 9시간 앞당기고 updated_at과 빈 값은 그대로 둔다")
    void shiftsClockTimestampsToUtc() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            migrate(mysql, MigrationVersion.fromVersion("31"));

            JdbcTemplate jdbcTemplate = jdbcTemplate(mysql);
            long promoted = insertMember(jdbcTemplate, "00000000-0000-0000-0000-000000000032", KST);
            long untouched = insertMember(jdbcTemplate, "00000000-0000-0000-0000-000000000033", null);
            jdbcTemplate.update("INSERT INTO launch_waitlist (email, consented_at, created_at, updated_at) VALUES (?, ?, ?, ?)",
                    "user@example.com", KST, UTC, UTC);
            jdbcTemplate.update("INSERT INTO travel_group (name, owner_member_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                    "제주 가족", promoted, UTC, UTC);
            long groupId = jdbcTemplate.queryForObject("SELECT id FROM travel_group", Long.class);
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    groupId, promoted, KST, UTC, UTC);
            jdbcTemplate.update("""
                    INSERT INTO group_invitation (group_id, token, code, inviter_member_id, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, groupId, "token-32", "ABC123", promoted, KST, UTC, UTC);

            migrate(mysql, MigrationVersion.LATEST);

            assertThat(text(jdbcTemplate, "SELECT promoted_at FROM member WHERE id = ?", promoted)).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT updated_at FROM member WHERE id = ?", promoted)).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT promoted_at FROM member WHERE id = ?", untouched)).isNull();
            assertThat(text(jdbcTemplate, "SELECT updated_at FROM member WHERE id = ?", untouched)).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT consented_at FROM launch_waitlist")).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT updated_at FROM launch_waitlist")).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT joined_at FROM group_member")).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT updated_at FROM group_member")).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT expires_at FROM group_invitation")).isEqualTo(UTC);
            assertThat(text(jdbcTemplate, "SELECT updated_at FROM group_invitation")).isEqualTo(UTC);
        }
    }

    private void migrate(MySQLContainer<?> mysql, MigrationVersion target) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private JdbcTemplate jdbcTemplate(MySQLContainer<?> mysql) {
        return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
    }

    private long insertMember(JdbcTemplate jdbcTemplate, String uuid, String promotedAt) {
        jdbcTemplate.update("""
                INSERT INTO member (uuid, name, nickname, provider, promoted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, uuid, "마이그레이션 회원", "회원", "KAKAO", promotedAt, UTC, UTC);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE uuid = ?", Long.class, uuid);
    }

    // DATETIME을 문자열로 읽어 드라이버의 시간대 변환 없이 저장된 값 그대로 비교한다.
    private String text(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql.replaceFirst("SELECT (\\w+)", "SELECT DATE_FORMAT($1, '%Y-%m-%d %H:%i:%s')"),
                String.class, args);
    }
}
