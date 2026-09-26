package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Region 마스터 데이터")
class RegionMasterDataIntegrationTest extends IntegrationTest {

    private static final List<ExpectedCity> CANONICAL_CITIES = List.of(
            new ExpectedCity("41", "41110", "수원시"),
            new ExpectedCity("41", "41130", "성남시"),
            new ExpectedCity("41", "41170", "안양시"),
            new ExpectedCity("41", "41190", "부천시"),
            new ExpectedCity("41", "41270", "안산시"),
            new ExpectedCity("41", "41280", "고양시"),
            new ExpectedCity("41", "41460", "용인시"),
            new ExpectedCity("41", "41590", "화성시"),
            new ExpectedCity("43", "43110", "청주시"),
            new ExpectedCity("44", "44130", "천안시"),
            new ExpectedCity("45", "52110", "전주시"),
            new ExpectedCity("47", "47110", "포항시"),
            new ExpectedCity("48", "48120", "창원시")
    );

    private static final List<String> DEPRECATED_DISTRICT_CODES = List.of(
            "41111", "41113", "41115", "41117",
            "41131", "41133", "41135",
            "41171", "41173",
            "41192", "41194", "41196",
            "41271", "41273",
            "41281", "41285", "41287",
            "41461", "41463", "41465",
            "41591", "41593", "41595", "41597",
            "43111", "43112", "43113", "43114",
            "44131", "44133",
            "52111", "52113",
            "47111", "47113",
            "48121", "48123", "48125", "48127", "48129"
    );

    private static final List<ExpectedDistrict> GWANGJU_JEONNAM_DISTRICTS = List.of(
            new ExpectedDistrict("29", "29110", "동구"),
            new ExpectedDistrict("29", "29140", "서구"),
            new ExpectedDistrict("29", "29155", "남구"),
            new ExpectedDistrict("29", "29170", "북구"),
            new ExpectedDistrict("29", "29200", "광산구"),
            new ExpectedDistrict("46", "46110", "목포시"),
            new ExpectedDistrict("46", "46130", "여수시"),
            new ExpectedDistrict("46", "46150", "순천시"),
            new ExpectedDistrict("46", "46170", "나주시"),
            new ExpectedDistrict("46", "46230", "광양시"),
            new ExpectedDistrict("46", "46710", "담양군"),
            new ExpectedDistrict("46", "46720", "곡성군"),
            new ExpectedDistrict("46", "46730", "구례군"),
            new ExpectedDistrict("46", "46770", "고흥군"),
            new ExpectedDistrict("46", "46780", "보성군"),
            new ExpectedDistrict("46", "46790", "화순군"),
            new ExpectedDistrict("46", "46800", "장흥군"),
            new ExpectedDistrict("46", "46810", "강진군"),
            new ExpectedDistrict("46", "46820", "해남군"),
            new ExpectedDistrict("46", "46830", "영암군"),
            new ExpectedDistrict("46", "46840", "무안군"),
            new ExpectedDistrict("46", "46860", "함평군"),
            new ExpectedDistrict("46", "46870", "영광군"),
            new ExpectedDistrict("46", "46880", "장성군"),
            new ExpectedDistrict("46", "46890", "완도군"),
            new ExpectedDistrict("46", "46900", "진도군"),
            new ExpectedDistrict("46", "46910", "신안군")
    );

    private static final List<String> DEPRECATED_GWANGJU_JEONNAM_CODES = List.of(
            "12210", "12240", "12270", "12300", "12330",
            "12110", "12130", "12150", "12170", "12190",
            "12710", "12720", "12730", "12740", "12750", "12760", "12770",
            "12780", "12790", "12800", "12810", "12820", "12830", "12840",
            "12850", "12860", "12870"
    );

    @Autowired
    private RegionResolver regionResolver;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("일반구가 있는 13개 시를 canonical DISTRICT로 조회한다")
    void resolvesCanonicalCities() {
        assertThat(CANONICAL_CITIES).allSatisfy(expected -> {
            Region city = regionResolver.resolve("KR", expected.provinceCode(), expected.regionCode());

            assertThat(city.getName()).isEqualTo(expected.name());
            assertThat(city.getRegionType()).isEqualTo(RegionType.DISTRICT);
            assertThat(findParentCode(city.getId())).isEqualTo(expected.provinceCode());
        });
    }

    @Test
    @DisplayName("통합한 일반구 코드는 Region 마스터에 남기지 않는다")
    void removesDeprecatedDistricts() {
        assertThat(DEPRECATED_DISTRICT_CODES).allSatisfy(code ->
                assertThat(regionRepository.existsByRegionTypeAndRegionCode(RegionType.DISTRICT, code))
                        .isFalse()
        );
    }

    @Test
    @DisplayName("광주·전남 시군구를 클라이언트 canonical 코드로 조회한다")
    void resolvesCanonicalGwangjuJeonnamDistricts() {
        assertThat(GWANGJU_JEONNAM_DISTRICTS).allSatisfy(expected -> {
            Region district = regionResolver.resolve("KR", expected.provinceCode(), expected.regionCode());

            assertThat(district.getName()).isEqualTo(expected.name());
            assertThat(district.getRegionType()).isEqualTo(RegionType.DISTRICT);
            assertThat(findParentCode(district.getId())).isEqualTo(expected.provinceCode());
        });
    }

    @Test
    @DisplayName("잘못 선반영한 광주·전남 12xxx 코드를 Region 마스터에 남기지 않는다")
    void removesDeprecatedGwangjuJeonnamCodes() {
        assertThat(DEPRECATED_GWANGJU_JEONNAM_CODES).allSatisfy(code ->
                assertThat(regionRepository.existsByRegionTypeAndRegionCode(RegionType.DISTRICT, code))
                        .isFalse()
        );
    }

    @Test
    @DisplayName("최종 Region 마스터 개수가 전체 코드표와 일치한다")
    void matchesDocumentedRegionCounts() {
        assertThat(countRegions(RegionType.COUNTRY)).isEqualTo(249);
        assertThat(countRegions(RegionType.PROVINCE)).isEqualTo(17);
        assertThat(countRegions(RegionType.DISTRICT)).isEqualTo(230);
    }

    private int countRegions(RegionType regionType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM region WHERE region_type = ?",
                Integer.class,
                regionType.name()
        );
    }

    private String findParentCode(Long regionId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT parent.region_code
                FROM region child
                JOIN region parent ON parent.id = child.parent_id
                WHERE child.id = ?
                """,
                String.class,
                regionId
        );
    }

    private record ExpectedCity(
            String provinceCode,
            String regionCode,
            String name
    ) {
    }

    private record ExpectedDistrict(
            String provinceCode,
            String regionCode,
            String name
    ) {
    }
}
