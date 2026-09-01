package com.mapmory.backend.travelrecord.statistics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.RecordMedia;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import com.mapmory.backend.travelrecord.TravelRecord;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@DisplayName("여행 통계 Repository")
class TravelStatisticsRepositoryTest extends MySqlTestContainerSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TravelStatisticsRepository travelStatisticsRepository;

    @Test
    @DisplayName("현재 회원의 기록·미디어·방문 국가·국내 시군구를 중복 없이 집계한다")
    void aggregatesCurrentMemberStatistics() {
        Scenario scenario = createScenario();

        TravelStatisticsSummaryQueryResult summary =
                travelStatisticsRepository.findSummary(scenario.member().getId());
        List<String> countryCodes =
                travelStatisticsRepository.findVisitedCountryCodes(scenario.member().getId());

        assertThat(summary.getRecordCount()).isEqualTo(7L);
        assertThat(summary.getMediaCount()).isEqualTo(4L);
        assertThat(summary.getVisitedKoreaDistrictCount()).isEqualTo(3L);
        assertThat(countryCodes).containsExactly("JP", "KR", "US");
    }

    @Test
    @DisplayName("국내 기록은 시도로, 해외 기록은 국가로 올려 기록 수 상위 3개를 반환한다")
    void findsTopThreeAggregateRegions() {
        Scenario scenario = createScenario();

        List<TopRegionQueryResult> topRegions =
                travelStatisticsRepository.findTopRegions(scenario.member().getId());

        assertThat(topRegions).hasSize(3);
        assertRegion(topRegions.get(0), scenario.seoul(), 3L);
        assertRegion(topRegions.get(1), scenario.japan(), 2L);
        assertRegion(topRegions.get(2), scenario.busan(), 1L);
    }

    @Test
    @DisplayName("기록이 없는 회원은 0과 빈 목록을 반환한다")
    void returnsZerosAndEmptyListsForMemberWithoutRecords() {
        Member member = persist(Member.of("기록 없는 회원", UUID.randomUUID()));
        flushAndClear();

        TravelStatisticsSummaryQueryResult summary = travelStatisticsRepository.findSummary(member.getId());

        assertThat(summary.getRecordCount()).isZero();
        assertThat(summary.getMediaCount()).isZero();
        assertThat(summary.getVisitedKoreaDistrictCount()).isZero();
        assertThat(travelStatisticsRepository.findVisitedCountryCodes(member.getId())).isEmpty();
        assertThat(travelStatisticsRepository.findTopRegions(member.getId())).isEmpty();
    }

    private Scenario createScenario() {
        Member member = persist(Member.of("통계 회원", UUID.randomUUID()));
        Member otherMember = persist(Member.of("다른 회원", UUID.randomUUID()));

        Region korea = country("KR", "대한민국");
        Region seoul = province(korea, "11", "서울특별시");
        Region busan = province(korea, "26", "부산광역시");
        Region gangnam = district(korea, seoul, "11680", "강남구");
        Region jongno = district(korea, seoul, "11110", "종로구");
        Region haeundae = district(korea, busan, "26350", "해운대구");

        Region japan = country("JP", "일본");
        Region tokyo = province(japan, "JP-13", "도쿄도");
        Region shinjuku = district(japan, tokyo, "JP-13-104", "신주쿠구");
        Region unitedStates = country("US", "미국");

        List<TravelRecord> gangnamRecords = saveRecords(member, gangnam, 2);
        List<TravelRecord> jongnoRecords = saveRecords(member, jongno, 1);
        saveRecords(member, haeundae, 1);
        saveRecords(member, japan, 1);
        saveRecords(member, shinjuku, 1);
        saveRecords(member, unitedStates, 1);

        saveMedia(gangnamRecords.get(0), 2);
        saveMedia(gangnamRecords.get(1), 1);
        saveMedia(jongnoRecords.get(0), 1);

        TravelRecord otherRecord = saveRecords(otherMember, gangnam, 1).get(0);
        saveMedia(otherRecord, 1);
        flushAndClear();

        return new Scenario(member, seoul, busan, japan);
    }

    private Region country(String code, String name) {
        return persist(Region.of(null, null, code, name, RegionType.COUNTRY));
    }

    private Region province(Region country, String code, String name) {
        return persist(Region.of(country, country, code, name, RegionType.PROVINCE));
    }

    private Region district(Region country, Region province, String code, String name) {
        return persist(Region.of(province, country, code, name, RegionType.DISTRICT));
    }

    private List<TravelRecord> saveRecords(Member member, Region region, int count) {
        java.util.ArrayList<TravelRecord> records = new java.util.ArrayList<>();
        for (int sequence = 0; sequence < count; sequence++) {
            records.add(persist(TravelRecord.of(
                    member,
                    region,
                    "통계 기록 " + UUID.randomUUID(),
                    "내용",
                    LocalDate.of(2026, 8, 31),
                    null
            )));
        }
        return records;
    }

    private void saveMedia(TravelRecord travelRecord, int count) {
        for (int sequence = 0; sequence < count; sequence++) {
            persist(RecordMedia.of(
                    travelRecord,
                    "travel-records/test/" + UUID.randomUUID() + ".jpg",
                    null,
                    sequence
            ));
        }
    }

    private void assertRegion(TopRegionQueryResult result, Region region, long recordCount) {
        assertThat(result.getRegionId()).isEqualTo(region.getId());
        assertThat(result.getRegionCode()).isEqualTo(region.getRegionCode());
        assertThat(result.getRegionType()).isEqualTo(region.getRegionType().name());
        assertThat(result.getName()).isEqualTo(region.getName());
        assertThat(result.getRecordCount()).isEqualTo(recordCount);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Scenario(Member member, Region seoul, Region busan, Region japan) {
    }
}
