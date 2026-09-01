package com.mapmory.backend.travelrecord.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.statistics.model.TopRegionStatistics;
import com.mapmory.backend.travelrecord.statistics.model.TravelStatistics;
import com.mapmory.backend.travelrecord.statistics.repository.TopRegionQueryResult;
import com.mapmory.backend.travelrecord.statistics.repository.TravelStatisticsRepository;
import com.mapmory.backend.travelrecord.statistics.repository.TravelStatisticsSummaryQueryResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("여행 통계 서비스")
class TravelStatisticsServiceTest {

    @Mock
    private Member member;

    @Mock
    private TravelStatisticsRepository travelStatisticsRepository;

    @Test
    @DisplayName("조회 결과를 국가 코드와 인기 지역을 포함한 응답으로 변환한다")
    void returnsTravelStatistics() {
        TravelStatisticsService service = service();
        when(member.getId()).thenReturn(10L);
        when(travelStatisticsRepository.findSummary(10L)).thenReturn(summary(7L, 4L, 3L));
        when(travelStatisticsRepository.findVisitedCountryCodes(10L)).thenReturn(List.of("JP", "KR", "US"));
        when(travelStatisticsRepository.findTopRegions(10L)).thenReturn(List.of(
                topRegion(15L, "11", "PROVINCE", "서울특별시", 3L),
                topRegion(2L, "JP", "COUNTRY", "일본", 2L)
        ));

        TravelStatistics statistics = service.getStatistics(member);

        assertThat(statistics.recordCount()).isEqualTo(7L);
        assertThat(statistics.mediaCount()).isEqualTo(4L);
        assertThat(statistics.visitedCountryCount()).isEqualTo(3L);
        assertThat(statistics.visitedKoreaDistrictCount()).isEqualTo(3L);
        assertThat(statistics.visitedCountryCodes()).containsExactly("JP", "KR", "US");
        assertThat(statistics.topRegions()).containsExactly(
                new TopRegionStatistics(15L, "11", RegionType.PROVINCE, "서울특별시", 3L),
                new TopRegionStatistics(2L, "JP", RegionType.COUNTRY, "일본", 2L)
        );
    }

    private TravelStatisticsService service() {
        return new TravelStatisticsService(travelStatisticsRepository);
    }

    private static TravelStatisticsSummaryQueryResult summary(
            long recordCount,
            long mediaCount,
            long visitedKoreaDistrictCount
    ) {
        return new TravelStatisticsSummaryQueryResult() {
            @Override
            public long getRecordCount() {
                return recordCount;
            }

            @Override
            public long getMediaCount() {
                return mediaCount;
            }

            @Override
            public long getVisitedKoreaDistrictCount() {
                return visitedKoreaDistrictCount;
            }
        };
    }

    private static TopRegionQueryResult topRegion(
            Long regionId,
            String regionCode,
            String regionType,
            String name,
            long recordCount
    ) {
        return new TopRegionQueryResult() {
            @Override
            public Long getRegionId() {
                return regionId;
            }

            @Override
            public String getRegionCode() {
                return regionCode;
            }

            @Override
            public String getRegionType() {
                return regionType;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getRecordCount() {
                return recordCount;
            }
        };
    }
}
