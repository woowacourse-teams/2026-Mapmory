package com.mapmory.backend.place.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import java.util.regex.Pattern;

/**
 * 제공자가 붙인 장소 ID. 기록에 저장하고(place_id 255자) URL 경로에 넣으므로 영문·숫자·{@code -}·{@code _} 1~255자만 받는다.
 */
public record PlaceId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,255}");

    public PlaceId {
        if (!isValid(value)) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        }
    }

    public static PlaceId from(String value) {
        return new PlaceId(value);
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}
