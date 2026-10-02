package com.mapmory.backend.place.application.port;

import java.util.Optional;

/** 좌표에 해당하는 국내 시군구를 찾는다. 경계 데이터가 없으면 빈 값을 반환한다. */
public interface DistrictLocator {

    Optional<DistrictMatch> find(double longitude, double latitude);

    record DistrictMatch(String provinceCode, String districtCode) {
    }
}
