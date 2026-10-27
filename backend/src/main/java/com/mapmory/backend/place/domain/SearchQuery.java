package com.mapmory.backend.place.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;

/** 장소 검색어. 앞뒤 공백을 뺀 값이 비어 있지 않고 100자 이하다. */
public record SearchQuery(String value) {

    public static final int MAX_LENGTH = 100;

    public SearchQuery {
        if (value == null || value.isBlank() || value.strip().length() > MAX_LENGTH) {
            throw new BusinessException(PlaceErrorCode.INVALID_SEARCH_QUERY);
        }
        value = value.strip();
    }

    public static SearchQuery from(String value) {
        return new SearchQuery(value);
    }
}
