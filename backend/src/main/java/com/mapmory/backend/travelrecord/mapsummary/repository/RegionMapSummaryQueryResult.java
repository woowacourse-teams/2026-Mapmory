package com.mapmory.backend.travelrecord.mapsummary.repository;

public interface RegionMapSummaryQueryResult {

    Long getRegionId();

    /**
     * 바로 위 지역 ID. 국가(루트)는 null이다.
     */
    Long getParentRegionId();

    String getRegionCode();

    String getName();

    String getRegionType();

    long getRecordCount();
}
