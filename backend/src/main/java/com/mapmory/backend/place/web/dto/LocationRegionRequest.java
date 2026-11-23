package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.domain.Coordinate;
import jakarta.validation.constraints.NotNull;

/** 사진의 촬영 위치. 좌표가 URL과 접근 로그에 남지 않도록 본문으로 받는다. */
public record LocationRegionRequest(@NotNull Double latitude, @NotNull Double longitude) {

    public Coordinate toCoordinate() {
        return new Coordinate(latitude, longitude);
    }

    @Override
    public String toString() {
        return "LocationRegionRequest[masked]";
    }
}
