package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 여행 일지 제목. 쓰지 않아도 된다. 비었거나 공백뿐이면 빈 제목("")으로 저장한다.
 *
 * <p>길이는 앱과 같게 UTF-16 글자 수로 센다(앱의 {@code String.length}). 이 기준이면 DB의 VARCHAR(200)
 * (utf8mb4 글자 수)를 넘을 수 없다. 앨범 이름은 코드 포인트로 세므로 이모지가 섞이면 두 규칙이 다르게 센다.
 * 앞뒤 공백은 자르지 않는다. 지금까지 저장한 값과 같게 둔다.
 */
@Embeddable
record TravelTitle(
        @Column(name = "title", nullable = false, length = 200)
        String value
) {
    private static final int MAX_LENGTH = 200;

    TravelTitle {
        if (value == null || value.length() > MAX_LENGTH) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_TITLE);
        }
    }

    static TravelTitle from(String raw) {
        return new TravelTitle(raw == null || raw.isBlank() ? "" : raw);
    }
}
