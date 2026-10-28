package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.domain.Region;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@DisplayName("일반구 Region 통합 마이그레이션")
class CityDistrictRegionMigrationTest {

    @Test
    @DisplayName("기존 일반구 기록을 canonical 시로 이관한 뒤 일반구를 삭제한다")
    void migratesExistingDistrictRecordToCanonicalCity() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            migrateToVersion17(mysql);

            JdbcTemplate jdbcTemplate = jdbcTemplate(mysql);
            long memberId = insertMember(jdbcTemplate);
            long suwonsiGwonseonGuId = findRegionId(jdbcTemplate, "41113");
            long travelRecordId = insertTravelRecord(jdbcTemplate, memberId, suwonsiGwonseonGuId);

            migrateAll(mysql);

            assertThat(findTravelRecordRegionCode(jdbcTemplate, travelRecordId)).isEqualTo("41110");
            assertThat(countRegions(jdbcTemplate, "41110")).isEqualTo(1);
            assertThat(countRegions(jdbcTemplate, "41113")).isZero();
        }
    }

    private void migrateToVersion17(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("17"))
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
                "00000000-0000-0000-0000-000000000018",
                "마이그레이션 회원"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE uuid = ?",
                Long.class,
                "00000000-0000-0000-0000-000000000018"
        );
    }

    private long findRegionId(JdbcTemplate jdbcTemplate, String regionCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM region WHERE region_type = 'DISTRICT' AND region_code = ?",
                Long.class,
                regionCode
        );
    }

    private long insertTravelRecord(JdbcTemplate jdbcTemplate, long memberId, long regionId) {
        jdbcTemplate.update(
                """
                INSERT INTO travel_record (member_id, region_id, title, content, start_date)
                VALUES (?, ?, ?, ?, ?)
                """,
                memberId,
                regionId,
                "수원 여행",
                "권선구에서 작성한 기존 기록",
                "2026-08-01"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM travel_record WHERE member_id = ?",
                Long.class,
                memberId
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
