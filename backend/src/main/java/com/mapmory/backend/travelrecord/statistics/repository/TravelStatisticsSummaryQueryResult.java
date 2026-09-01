package com.mapmory.backend.travelrecord.statistics.repository;

public interface TravelStatisticsSummaryQueryResult {

    long getRecordCount();

    long getMediaCount();

    long getVisitedKoreaDistrictCount();
}
