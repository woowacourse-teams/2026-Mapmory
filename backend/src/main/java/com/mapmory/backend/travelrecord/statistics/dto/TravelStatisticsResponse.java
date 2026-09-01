package com.mapmory.backend.travelrecord.statistics.dto;

import com.mapmory.backend.travelrecord.statistics.model.TravelStatistics;
import java.util.List;

public record TravelStatisticsResponse(
        long recordCount,
        long mediaCount,
        long visitedCountryCount,
        long visitedKoreaDistrictCount,
        List<String> visitedCountryCodes,
        List<TopRegionResponse> topRegions
) {

    public static TravelStatisticsResponse from(TravelStatistics statistics) {
        return new TravelStatisticsResponse(
                statistics.recordCount(),
                statistics.mediaCount(),
                statistics.visitedCountryCount(),
                statistics.visitedKoreaDistrictCount(),
                List.copyOf(statistics.visitedCountryCodes()),
                statistics.topRegions().stream()
                        .map(TopRegionResponse::from)
                        .toList()
        );
    }
}
