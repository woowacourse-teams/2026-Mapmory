package com.mapmory.backend.place.application;

import com.mapmory.backend.place.application.port.ReverseGeocodePort;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.region.application.RegionSuggester;
import com.mapmory.backend.region.domain.RegionAddress;
import org.springframework.stereotype.Service;

/**
 * 사진 좌표로 기록 지역을 추천한다. 앱은 대표 사진의 좌표를 보내고, 추천 지역을 저장 화면에 미리 채운다.
 *
 * <p>좌표를 주소 이름으로 바꾼 뒤에는 장소 선택과 같은 {@link RegionSuggester}로 지역을 찾는다.
 * 좌표는 이 요청 안에서만 쓰고 저장하지 않는다.
 */
@Service
public class LocationRegionService {

    private final ReverseGeocodePort reverseGeocodePort;
    private final RegionSuggester regionSuggester;

    public LocationRegionService(ReverseGeocodePort reverseGeocodePort, RegionSuggester regionSuggester) {
        this.reverseGeocodePort = reverseGeocodePort;
        this.regionSuggester = regionSuggester;
    }

    public LocatedRegion locate(Coordinate coordinate) {
        RegionAddress address = reverseGeocodePort.findAddress(coordinate).orElse(RegionAddress.UNKNOWN);
        return new LocatedRegion(address, regionSuggester.suggest(address).orElse(null),
                reverseGeocodePort.provider());
    }
}
