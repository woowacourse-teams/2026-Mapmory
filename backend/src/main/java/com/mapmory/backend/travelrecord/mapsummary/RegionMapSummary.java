package com.mapmory.backend.travelrecord.mapsummary;

import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;

/**
 * 지도에 칠할 지역 하나의 요약이다.
 *
 * <p>리포지토리 프로젝션에 색상 단계 정책을 적용한 결과이며 표현 형식을 정하지 않는다.
 * 응답 DTO 변환은 웹 계층이 맡는다. (ADR 0016, 0017)
 */
public record RegionMapSummary(
        Long regionId,
        String regionCode,
        RegionType regionType,
        String name,
        long recordCount,
        MapColorLevel level
) {
}
