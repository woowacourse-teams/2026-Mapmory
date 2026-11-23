package com.mapmory.backend.place.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import java.util.Locale;

/**
 * 사진 촬영 위치 같은 WGS84 좌표. 위도 -90~90, 경도 -180~180만 받는다.
 *
 * <p>사용자의 위치라 로그에 남지 않도록 {@link #toString()}은 값을 가린다. 외부 API에 보낼 때만 {@link #latitudeText()}·{@link #longitudeText()}를 쓴다.
 */
public record Coordinate(double latitude, double longitude) {

    public Coordinate {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }

    /** 소수점 아래 6자리(약 10cm)면 지역 판정에 충분하다. 예: {@code 37.566500} */
    public String latitudeText() {
        return String.format(Locale.ROOT, "%.6f", latitude);
    }

    public String longitudeText() {
        return String.format(Locale.ROOT, "%.6f", longitude);
    }

    @Override
    public String toString() {
        return "Coordinate[masked]";
    }
}
