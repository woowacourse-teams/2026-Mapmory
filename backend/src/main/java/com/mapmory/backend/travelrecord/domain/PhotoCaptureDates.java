package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * 사진 Object Key별 촬영일. 앱이 사진첩에서 읽어 보낸 값이다.
 *
 * <p>카메라 시계가 틀린 사진도 흔하므로 이상한 날짜 때문에 기록 저장을 실패시키지 않는다.
 * 형식이나 범위가 틀린 날짜와 형식이 틀린 키는 모르는 날짜로 보고 버린다.
 * 저장된 촬영일을 모르는 날짜로 되돌리는 경로는 없다. 사진을 지우고 다시 올리면 새 키라 비어 있다.
 * 오늘 날짜는 서버와 기기의 시간대가 달라도 거절되지 않게 하루 여유를 둔다.
 */
public final class PhotoCaptureDates {
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final PhotoCaptureDates EMPTY = new PhotoCaptureDates(Map.of());

    private final Map<ObjectKey, LocalDate> dates;

    private PhotoCaptureDates(Map<ObjectKey, LocalDate> dates) {
        this.dates = dates;
    }

    public static PhotoCaptureDates empty() {
        return EMPTY;
    }

    public static PhotoCaptureDates from(Map<String, String> rawDates, LocalDate today) {
        if (rawDates == null || rawDates.isEmpty()) {
            return EMPTY;
        }
        LocalDate latest = today.plusDays(1);
        Map<ObjectKey, LocalDate> dates = new HashMap<>();
        rawDates.forEach((rawKey, rawDate) -> {
            LocalDate date = toDateOrNull(rawDate);
            if (rawKey == null || date == null || date.isBefore(EARLIEST) || date.isAfter(latest)) {
                return;
            }
            ObjectKey objectKey = toObjectKeyOrNull(rawKey);
            if (objectKey != null) {
                dates.put(objectKey, date);
            }
        });
        return new PhotoCaptureDates(Map.copyOf(dates));
    }

    private static LocalDate toDateOrNull(String rawDate) {
        if (rawDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException invalidDate) {
            return null;
        }
    }

    private static ObjectKey toObjectKeyOrNull(String rawKey) {
        try {
            return ObjectKey.from(rawKey);
        } catch (BusinessException invalidKey) {
            return null;
        }
    }

    LocalDate of(ObjectKey objectKey) {
        return dates.get(objectKey);
    }
}
