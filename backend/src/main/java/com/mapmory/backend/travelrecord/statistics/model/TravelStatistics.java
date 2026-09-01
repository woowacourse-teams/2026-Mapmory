package com.mapmory.backend.travelrecord.statistics.model;

import java.util.List;

public record TravelStatistics(
        long recordCount,
        long mediaCount,
        long visitedCountryCount,
        long visitedKoreaDistrictCount,
        List<String> visitedCountryCodes,
        List<TopRegionStatistics> topRegions
) {

    public TravelStatistics {
        visitedCountryCodes = List.copyOf(visitedCountryCodes);
        topRegions = List.copyOf(topRegions);
    }
}
