package com.mapmory.backend.sharevideo.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TravelTimelineSnapshotFactoryTest {

    private final TravelTimelineSnapshotFactory factory = new TravelTimelineSnapshotFactory();
    private final Member member = Member.of("타임라인 테스트 회원", UUID.randomUUID());
    private final Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
    private final Region seoul = Region.of(korea, korea, "11", "서울특별시", RegionType.PROVINCE);
    private final Region busan = Region.of(korea, korea, "26", "부산광역시", RegionType.PROVINCE);
    private final Region unitedKingdom = Region.of(null, null, "GB", "영국", RegionType.COUNTRY);
    private final Region iceland = Region.of(null, null, "IS", "아이슬란드", RegionType.COUNTRY);

    @Test
    void 연속된_대한민국_기록을_하나의_정류장으로_합친다() {
        TravelRecord seoulRecord = record(
                1L, seoul, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12)
        );
        TravelRecord busanRecord = record(2L, busan, LocalDate.of(2026, 4, 1), null);

        TravelTimelineSnapshot snapshot = factory.create(List.of(seoulRecord, busanRecord));

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.stops()).hasSize(1);
        TravelTimelineStop koreaStop = snapshot.stops().getFirst();
        assertThat(koreaStop.countryCode()).isEqualTo("KR");
        assertThat(koreaStop.countryName()).isEqualTo("대한민국");
        assertThat(koreaStop.visits())
                .extracting(TravelTimelineVisit::visitId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 다른_국가가_사이에_있으면_대한민국을_새_정류장으로_만든다() {
        TravelTimelineSnapshot snapshot = factory.create(List.of(
                record(1L, seoul, LocalDate.of(2026, 3, 10), null),
                record(2L, unitedKingdom, LocalDate.of(2026, 4, 10), null),
                record(3L, busan, LocalDate.of(2026, 5, 10), null)
        ));

        assertThat(snapshot.stops())
                .extracting(TravelTimelineStop::countryCode)
                .containsExactly("KR", "GB", "KR");
    }

    @Test
    void 연속된_다른_국가_기록도_하나의_정류장으로_합친다() {
        TravelTimelineSnapshot snapshot = factory.create(List.of(
                record(1L, unitedKingdom, LocalDate.of(2026, 3, 10), null),
                record(2L, unitedKingdom, LocalDate.of(2026, 4, 10), null)
        ));

        assertThat(snapshot.stops())
                .extracting(TravelTimelineStop::countryCode)
                .containsExactly("GB");
        assertThat(snapshot.stops().getFirst().visits())
                .extracting(TravelTimelineVisit::visitId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 방문_ID와_날짜를_스냅샷에_보존한다() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 7);
        TravelTimelineSnapshot snapshot = factory.create(List.of(
                record(10L, iceland, startDate, endDate)
        ));

        TravelTimelineVisit visit = snapshot.stops().getFirst().visits().getFirst();

        assertThat(visit).isEqualTo(new TravelTimelineVisit(10L, startDate, endDate));
    }

    private TravelRecord record(
            Long id,
            Region region,
            LocalDate startDate,
            LocalDate endDate
    ) {
        TravelRecord record = TravelRecord.of(
                member,
                region,
                "여행 기록",
                "",
                startDate,
                endDate
        );
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }
}
