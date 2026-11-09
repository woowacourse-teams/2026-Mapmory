package com.mapmory.backend.place.application.port;

import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.Optional;

/** 좌표를 주소 이름(국가, 시·도, 시·군·구)으로 바꾸는 포트. 제공자를 쓸 수 없으면 PLACE_PROVIDER_UNAVAILABLE이다. */
public interface ReverseGeocodePort {

    /** 주소 출처. 응답에 표기한다. */
    PlaceProvider provider();

    /** 바다처럼 주소가 없는 좌표면 빈 값이다. 좌표는 저장하지도 로그에 남기지도 않는다. */
    Optional<RegionAddress> findAddress(Coordinate coordinate);
}
