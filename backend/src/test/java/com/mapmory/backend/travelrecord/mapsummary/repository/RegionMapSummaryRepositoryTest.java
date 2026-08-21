package com.mapmory.backend.travelrecord.mapsummary.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.travelrecord.TravelRecord;
import com.mapmory.backend.travelrecordtag.TravelRecordTag;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@DisplayName("지역 지도 요약 Repository")
class RegionMapSummaryRepositoryTest extends MySqlTestContainerSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RegionMapSummaryRepository regionMapSummaryRepository;

    @Test
    @DisplayName("선택한 태그가 연결된 기록만 지역별로 합산한다")
    void filtersRecordsByTag() {
        Member member = member("태그 필터 회원");
        SavedRegion country = country("T1", "태그 테스트 국가");
        Tag selectedTag = persist(Tag.of(member, "연인"));
        Tag otherTag = persist(Tag.of(member, "친구"));
        TravelRecord selectedRecord = persist(record(member, country, "선택 기록"));
        TravelRecord otherRecord = persist(record(member, country, "다른 기록"));
        persist(TravelRecordTag.of(selectedRecord, selectedTag));
        persist(TravelRecordTag.of(otherRecord, otherTag));
        flushAndClear();

        List<RegionMapSummaryQueryResult> results = regionMapSummaryRepository.findRegionMapSummaries(
                member.getId(),
                null,
                selectedTag.getId()
        );

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getRegionId()).isEqualTo(country.id());
            assertThat(result.getRecordCount()).isEqualTo(1L);
        });
    }

    @Nested
    @DisplayName("루트 지역별 지도 요약을 조회할 때")
    class FindRootRegionMapSummaries {

        private RootSummaryScenario scenario;

        @BeforeEach
        void setUp() {
            scenario = createRootSummaryScenario();
        }

        @Test
        @DisplayName("현재 회원의 국가·시도·시군구 기록만 국가별로 합산한다")
        void aggregatesCurrentMemberRecordsByCountry() {
            List<RegionMapSummaryQueryResult> results = findSummaries(scenario.member(), null);

            assertSummaries(results, scenario.expectedSummaries());
        }

        @Test
        @DisplayName("여행 기록이 없는 회원은 빈 결과를 반환한다")
        void returnsEmptyResultForMemberWithoutRecords() {
            List<RegionMapSummaryQueryResult> results = findSummaries(scenario.memberWithoutRecords(), null);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("직속 하위 지역별 지도 요약을 조회할 때")
    class FindChildRegionMapSummaries {

        private ChildSummaryScenario scenario;

        @BeforeEach
        void setUp() {
            scenario = createChildSummaryScenario();
        }

        @Test
        @DisplayName("다른 국가·다른 회원·미방문 시도를 제외하고 시도별로 합산한다")
        void aggregatesRecordsByProvince() {
            List<RegionMapSummaryQueryResult> results =
                    findSummaries(scenario.member(), scenario.country());

            assertSummaries(results, scenario.provinceSummaries());
        }

        @Test
        @DisplayName("형제 시군구의 기록을 서로 섞지 않고 시군구별로 합산한다")
        void aggregatesRecordsByDistrict() {
            List<RegionMapSummaryQueryResult> results =
                    findSummaries(scenario.member(), scenario.secondProvince());

            assertSummaries(results, scenario.districtSummaries());
        }

        @Test
        @DisplayName("말단 시군구를 부모로 조회하면 빈 결과를 반환한다")
        void returnsEmptyResultForDistrictParent() {
            List<RegionMapSummaryQueryResult> results =
                    findSummaries(scenario.member(), scenario.firstDistrict());

            assertThat(results).isEmpty();
        }
    }

    private RootSummaryScenario createRootSummaryScenario() {
        Member member = member("회원");
        Member memberWithoutRecords = member("기록 없는 회원");
        Member otherMember = member("다른 회원");

        SavedRegion firstCountry = country("X1", "첫 번째 방문 국가");
        SavedRegion emptyCountry = country("X2", "현재 회원의 미방문 국가");
        SavedRegion secondCountry = country("X3", "두 번째 방문 국가");

        SavedRegion firstProvince = province(firstCountry, "X1-P1", "첫 번째 국가의 시도");
        SavedRegion firstDistrict = district(firstProvince, "X1-D1", "첫 번째 국가의 시군구");
        SavedRegion secondProvince = province(secondCountry, "X3-P1", "두 번째 국가의 시도");
        SavedRegion secondDistrict = district(secondProvince, "X3-D1", "두 번째 국가의 시군구");

        long firstCountryRecordCount = saveRecords(member, firstCountry, 1)
                + saveRecords(member, firstProvince, 1)
                + saveRecords(member, firstDistrict, 1);
        long secondCountryRecordCount = saveRecords(member, secondDistrict, 1);

        saveRecords(otherMember, firstDistrict, 1);
        saveRecords(otherMember, emptyCountry, 1);
        flushAndClear();

        return new RootSummaryScenario(
                member,
                memberWithoutRecords,
                List.of(
                        summary(firstCountry, firstCountryRecordCount),
                        summary(secondCountry, secondCountryRecordCount)
                )
        );
    }

    private ChildSummaryScenario createChildSummaryScenario() {
        Member member = member("회원");
        Member otherMember = member("다른 회원");

        SavedRegion country = country("Y1", "조회 국가");
        SavedRegion otherCountry = country("Y2", "다른 국가");

        SavedRegion firstProvince = province(country, "Y1-P1", "첫 번째 방문 시도");
        SavedRegion secondProvince = province(country, "Y1-P2", "두 번째 방문 시도");
        SavedRegion emptyProvince = province(country, "Y1-P3", "현재 회원의 미방문 시도");
        SavedRegion otherCountryProvince = province(otherCountry, "Y2-P1", "다른 국가의 시도");

        SavedRegion firstProvinceDistrict = district(firstProvince, "Y1-P1-D1", "첫 번째 시도의 시군구");
        SavedRegion firstDistrict = district(secondProvince, "Y1-D1", "첫 번째 방문 시군구");
        SavedRegion secondDistrict = district(secondProvince, "Y1-D2", "두 번째 방문 시군구");
        SavedRegion emptyDistrict = district(secondProvince, "Y1-D3", "현재 회원의 미방문 시군구");
        SavedRegion otherCountryDistrict = district(otherCountryProvince, "Y2-D1", "다른 국가의 시군구");

        long firstProvinceRecordCount = saveRecords(member, firstProvinceDistrict, 1);
        long secondProvinceDirectRecordCount = saveRecords(member, secondProvince, 1);
        long firstDistrictRecordCount = saveRecords(member, firstDistrict, 2);
        long secondDistrictRecordCount = saveRecords(member, secondDistrict, 1);
        long secondProvinceRecordCount = secondProvinceDirectRecordCount
                + firstDistrictRecordCount
                + secondDistrictRecordCount;
        saveRecords(member, otherCountryDistrict, 1);

        saveRecords(otherMember, firstDistrict, 1);
        saveRecords(otherMember, emptyProvince, 1);
        saveRecords(otherMember, emptyDistrict, 1);
        flushAndClear();

        return new ChildSummaryScenario(
                member,
                country,
                secondProvince,
                firstDistrict,
                List.of(
                        summary(firstProvince, firstProvinceRecordCount),
                        summary(secondProvince, secondProvinceRecordCount)
                ),
                List.of(
                        summary(firstDistrict, firstDistrictRecordCount),
                        summary(secondDistrict, secondDistrictRecordCount)
                )
        );
    }

    private List<RegionMapSummaryQueryResult> findSummaries(Member member, SavedRegion parent) {
        Long parentRegionId = null;
        if (parent != null) {
            parentRegionId = parent.id();
        }
        return regionMapSummaryRepository.findRegionMapSummaries(member.getId(), parentRegionId, null);
    }

    private void assertSummaries(
            List<RegionMapSummaryQueryResult> results,
            List<ExpectedSummary> expectedSummaries
    ) {
        assertThat(results)
                .extracting(ExpectedSummary::from)
                .containsExactlyElementsOf(expectedSummaries);
    }

    private ExpectedSummary summary(SavedRegion region, long recordCount) {
        return ExpectedSummary.of(region, recordCount);
    }

    private Member member(String name) {
        return persist(Member.of(name, UUID.randomUUID()));
    }

    private SavedRegion country(String code, String name) {
        Region region = persist(Region.of(null, null, code, name, RegionType.COUNTRY));
        return new SavedRegion(region, null, code, name, RegionType.COUNTRY);
    }

    private SavedRegion province(SavedRegion country, String code, String name) {
        Region region = persist(Region.of(
                country.entity(),
                country.entity(),
                code,
                name,
                RegionType.PROVINCE
        ));
        return new SavedRegion(region, country, code, name, RegionType.PROVINCE);
    }

    private SavedRegion district(SavedRegion province, String code, String name) {
        Region region = persist(Region.of(
                province.entity(),
                province.root().entity(),
                code,
                name,
                RegionType.DISTRICT
        ));
        return new SavedRegion(region, province.root(), code, name, RegionType.DISTRICT);
    }

    private long saveRecords(Member member, SavedRegion region, int count) {
        for (int sequence = 1; sequence <= count; sequence++) {
            persist(TravelRecord.of(
                    member,
                    region.entity(),
                    "테스트 기록 " + sequence,
                    "내용",
                    LocalDate.of(2026, 8, 11),
                    null
            ));
        }
        return count;
    }

    private TravelRecord record(Member member, SavedRegion region, String title) {
        return TravelRecord.of(
                member,
                region.entity(),
                title,
                "내용",
                LocalDate.of(2026, 8, 11),
                null
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record SavedRegion(
            Region entity,
            SavedRegion root,
            String code,
            String name,
            RegionType type
    ) {

        private Long id() {
            return entity.getId();
        }
    }

    private record ExpectedSummary(
            Long regionId,
            String code,
            String name,
            String type,
            long recordCount
    ) {

        private static ExpectedSummary of(SavedRegion region, long recordCount) {
            return new ExpectedSummary(
                    region.id(),
                    region.code(),
                    region.name(),
                    region.type().name(),
                    recordCount
            );
        }

        private static ExpectedSummary from(RegionMapSummaryQueryResult result) {
            return new ExpectedSummary(
                    result.getRegionId(),
                    result.getRegionCode(),
                    result.getName(),
                    result.getRegionType(),
                    result.getRecordCount()
            );
        }
    }

    private record RootSummaryScenario(
            Member member,
            Member memberWithoutRecords,
            List<ExpectedSummary> expectedSummaries
    ) {
    }

    private record ChildSummaryScenario(
            Member member,
            SavedRegion country,
            SavedRegion secondProvince,
            SavedRegion firstDistrict,
            List<ExpectedSummary> provinceSummaries,
            List<ExpectedSummary> districtSummaries
    ) {
    }
}
