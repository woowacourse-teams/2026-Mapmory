package com.mapmory.backend.auth.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.mapmory.backend.support.MigrationTestDatabase;

@DisplayName("refresh 토큰 세션 묶음 마이그레이션 (#467)")
class RefreshTokenFamilyMigrationTest {

    private static final int LONG_CHAIN = 1_500;

    @Test
    @DisplayName("회전 체인의 끝 토큰이 같은 토큰끼리 한 세션으로 묶인다")
    void groupsTokensBySameTerminal() {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create()) {
            migrateTo(mysql, "42");
            JdbcTemplate jdbc = jdbcTemplate(mysql);

            long memberA = insertMember(jdbc);
            long memberB = insertMember(jdbc);

            // 세션 1: 게스트 응답 유실 재발급 모양. a1 → a2 → a3이고, 재발급으로 a1도 a3을 가리킨다.
            long a3 = insertToken(jdbc, memberA, null);
            long a2 = insertToken(jdbc, memberA, a3);
            long a1 = insertToken(jdbc, memberA, a3);
            // 세션 2: 같은 회원의 다른 기기. 토큰 하나.
            long a4 = insertToken(jdbc, memberA, null);
            // 세션 3: 후속이 사라진 토큰. 가리키는 행이 없으면 끝으로 본다.
            long b1 = insertToken(jdbc, memberB, 999_999L);

            migrateAll(mysql);

            assertThat(familyOf(jdbc, a1)).isEqualTo(a3);
            assertThat(familyOf(jdbc, a2)).isEqualTo(a3);
            assertThat(familyOf(jdbc, a3)).isEqualTo(a3);
            assertThat(familyOf(jdbc, a4)).isEqualTo(a4);
            assertThat(familyOf(jdbc, b1)).isEqualTo(b1);
            assertThat(familyMember(jdbc, a3)).isEqualTo(memberA);
            assertThat(familyMember(jdbc, b1)).isEqualTo(memberB);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token_family", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("체인이 재귀 깊이 기본값(1000)보다 길어도 한 세션으로 묶인다")
    void groupsLongChain() {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create()) {
            migrateTo(mysql, "42");
            JdbcTemplate jdbc = jdbcTemplate(mysql);
            long member = insertMember(jdbc);

            long next = insertToken(jdbc, member, null);
            long terminal = next;
            for (int i = 1; i < LONG_CHAIN; i++) {
                next = insertToken(jdbc, member, next);
            }

            migrateAll(mysql);

            assertThat(familyOf(jdbc, next)).isEqualTo(terminal);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_token WHERE family_id = ?", Integer.class, terminal))
                    .isEqualTo(LONG_CHAIN);
        }
    }

    @Test
    @DisplayName("마이그레이션 뒤 새 세션은 기존 세션 id와 겹치지 않는다")
    void newFamilyIdDoesNotCollide() {
        try (MigrationTestDatabase mysql = MigrationTestDatabase.create()) {
            migrateTo(mysql, "42");
            JdbcTemplate jdbc = jdbcTemplate(mysql);
            long member = insertMember(jdbc);
            long token = insertToken(jdbc, member, null);

            migrateAll(mysql);
            jdbc.update("INSERT INTO refresh_token_family (member_id) VALUES (?)", member);
            Long created = jdbc.queryForObject("SELECT MAX(id) FROM refresh_token_family", Long.class);

            assertThat(created).isGreaterThan(token);
        }
    }

    private long insertMember(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO member (uuid, name, nickname) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), "소현", "회원12345");
        return jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
    }

    private long insertToken(JdbcTemplate jdbc, long memberId, Long replacedById) {
        jdbc.update("""
                INSERT INTO refresh_token (member_id, token_hash, expires_at, revoked_at, replaced_by_id)
                VALUES (?, ?, NOW() + INTERVAL 14 DAY, ?, ?)
                """,
                memberId, UUID.randomUUID().toString().replace("-", ""),
                replacedById == null ? null : Timestamp.valueOf("2026-11-01 00:00:00"), replacedById);
        return jdbc.queryForObject("SELECT MAX(id) FROM refresh_token", Long.class);
    }

    private long familyOf(JdbcTemplate jdbc, long tokenId) {
        return jdbc.queryForObject("SELECT family_id FROM refresh_token WHERE id = ?", Long.class, tokenId);
    }

    private long familyMember(JdbcTemplate jdbc, long familyId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT member_id FROM refresh_token_family WHERE id = ?", familyId);
        return ((Number) row.get("member_id")).longValue();
    }

    private void migrateTo(MigrationTestDatabase mysql, String version) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private void migrateAll(MigrationTestDatabase mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private JdbcTemplate jdbcTemplate(MigrationTestDatabase mysql) {
        return new JdbcTemplate(new SingleConnectionDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), true));
    }
}
