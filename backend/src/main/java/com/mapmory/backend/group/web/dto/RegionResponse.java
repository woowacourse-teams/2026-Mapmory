package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionType;

/**
 * 앨범의 지역 하나. 붙이기 화면을 미리 채우려고 id를 함께 준다.
 */
public record RegionResponse(Long id, String code, RegionType type, String name) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(region.getId(), region.getRegionCode(), region.getRegionType(), region.getName());
    }
}
