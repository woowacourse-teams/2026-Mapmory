package com.mapmory.backend.travelrecord.statistics.repository;

public interface TopRegionQueryResult {

    Long getRegionId();

    String getRegionCode();

    String getRegionType();

    String getName();

    long getRecordCount();
}
