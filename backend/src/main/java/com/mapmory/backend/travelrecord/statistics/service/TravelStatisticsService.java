package com.mapmory.backend.travelrecord.statistics.service;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.statistics.model.TopRegionStatistics;
import com.mapmory.backend.travelrecord.statistics.model.TravelStatistics;
import com.mapmory.backend.travelrecord.statistics.repository.TopRegionQueryResult;
import com.mapmory.backend.travelrecord.statistics.repository.TravelStatisticsRepository;
import com.mapmory.backend.travelrecord.statistics.repository.TravelStatisticsSummaryQueryResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelStatisticsService {

    private final TravelStatisticsRepository travelStatisticsRepository;

    public TravelStatisticsService(TravelStatisticsRepository travelStatisticsRepository) {
        this.travelStatisticsRepository = travelStatisticsRepository;
    }

    @Transactional(readOnly = true)
    public TravelStatistics getStatistics(Member member) {
        return getStatistics(member.getId());
    }

    private TravelStatistics getStatistics(Long memberId) {
        TravelStatisticsSummaryQueryResult summary = travelStatisticsRepository.findSummary(memberId);
        List<String> visitedCountryCodes = travelStatisticsRepository.findVisitedCountryCodes(memberId);
        List<TopRegionStatistics> topRegions = travelStatisticsRepository.findTopRegions(memberId).stream()
                .map(TravelStatisticsService::toTopRegionStatistics)
                .toList();

        return new TravelStatistics(
                summary.getRecordCount(),
                summary.getMediaCount(),
                visitedCountryCodes.size(),
                summary.getVisitedKoreaDistrictCount(),
                visitedCountryCodes,
                topRegions
        );
    }

    private static TopRegionStatistics toTopRegionStatistics(TopRegionQueryResult result) {
        return new TopRegionStatistics(
                result.getRegionId(),
                result.getRegionCode(),
                RegionType.valueOf(result.getRegionType()),
                result.getName(),
                result.getRecordCount()
        );
    }
}
