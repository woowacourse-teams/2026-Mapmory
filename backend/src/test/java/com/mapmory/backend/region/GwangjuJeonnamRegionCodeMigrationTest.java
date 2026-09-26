package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@DisplayName("광주·전남 Region 코드 복원 마이그레이션")
class GwangjuJeonnamRegionCodeMigrationTest {

    private static final Map<String, String> CODE_MAPPING = Map.ofEntries(
            Map.entry("12210", "29110"),
            Map.entry("12240", "29140"),
            Map.entry("12270", "29155"),
            Map.entry("12300", "29170"),
            Map.entry("12330", "29200"),
            Map.entry("12110", "46110"),
            Map.entry("12130", "46130"),
            Map.entry("12150", "46150"),
            Map.entry("12170", "46170"),
            Map.entry("12190", "46230"),
            Map.entry("12710", "46710"),
            Map.entry("12720", "46720"),
            Map.entry("12730", "46730"),
            Map.entry("12740", "46770"),
            Map.entry("12750", "46780"),
            Map.entry("12760", "46790"),
            Map.entry("12770", "46800"),
            Map.entry("12780", "46810"),
            Map.entry("12790", "46820"),
            Map.entry("12800", "46830"),
            Map.entry("12810", "46840"),
            Map.entry("12820", "46860"),
            Map.entry("12830", "46870"),
            Map.entry("12840", "46880"),
            Map.entry("12850", "46890"),
            Map.entry("12860", "46900"),
            Map.entry("12870", "46910")
    );

    @Test
    @DisplayName("Region ID와 기존 여행 기록을 보존하며 27개 코드를 변경한다")
    void restoresCanonicalCodesWithoutChangingRegionIds() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            migrateToVersion18(mysql);

            JdbcTemplate jdbcTemplate = jdbcTemplate(mysql);
            long memberId = insertMember(jdbcTemplate);
            long gwangjuRegionId = findRegionId(jdbcTemplate, "12210");
            long jeonnamRegionId = findRegionId(jdbcTemplate, "12110");
            long gwangjuRecordId = insertTravelRecord(jdbcTemplate, memberId, gwangjuRegionId, "광주 여행");
            long jeonnamRecordId = insertTravelRecord(jdbcTemplate, memberId, jeonnamRegionId, "목포 여행");

            migrateAll(mysql);

            assertThat(findTravelRecordRegionId(jdbcTemplate, gwangjuRecordId)).isEqualTo(gwangjuRegionId);
            assertThat(findTravelRecordRegionCode(jdbcTemplate, gwangjuRecordId)).isEqualTo("29110");
            assertThat(findTravelRecordRegionId(jdbcTemplate, jeonnamRecordId)).isEqualTo(jeonnamRegionId);
            assertThat(findTravelRecordRegionCode(jdbcTemplate, jeonnamRecordId)).isEqualTo("46110");

            CODE_MAPPING.forEach((previousCode, canonicalCode) -> {
                assertThat(countRegions(jdbcTemplate, previousCode)).isZero();
                assertThat(countRegions(jdbcTemplate, canonicalCode)).isEqualTo(1);
            });
        }
    }

    private void migrateToVersion18(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18"))
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

    private long insertMember(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "INSERT INTO member (uuid, name) VALUES (?, ?)",
                "00000000-0000-0000-0000-000000000019",
                "지역코드 마이그레이션 회원"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE uuid = ?",
                Long.class,
                "00000000-0000-0000-0000-000000000019"
        );
    }

    private long findRegionId(JdbcTemplate jdbcTemplate, String regionCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM region WHERE region_type = 'DISTRICT' AND region_code = ?",
                Long.class,
                regionCode
        );
    }

    private long insertTravelRecord(JdbcTemplate jdbcTemplate, long memberId, long regionId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO travel_record (member_id, region_id, title, content, start_date)
                VALUES (?, ?, ?, ?, ?)
                """,
                memberId,
                regionId,
                title,
                "기존 여행 기록",
                "2026-09-01"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM travel_record WHERE member_id = ? AND region_id = ?",
                Long.class,
                memberId,
                regionId
        );
    }

    private long findTravelRecordRegionId(JdbcTemplate jdbcTemplate, long travelRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT region_id FROM travel_record WHERE id = ?",
                Long.class,
                travelRecordId
        );
    }

    private String findTravelRecordRegionCode(JdbcTemplate jdbcTemplate, long travelRecordId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT region.region_code
                FROM travel_record
                JOIN region ON region.id = travel_record.region_id
                WHERE travel_record.id = ?
                """,
                String.class,
                travelRecordId
        );
    }

    private int countRegions(JdbcTemplate jdbcTemplate, String regionCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM region WHERE region_type = 'DISTRICT' AND region_code = ?",
                Integer.class,
                regionCode
        );
    }
}
