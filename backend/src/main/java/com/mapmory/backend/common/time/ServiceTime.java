package com.mapmory.backend.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 서비스의 날짜 기준. 저장하는 시각은 UTC이고, "오늘"과 하루의 경계만 한국 날짜로 정한다. */
public final class ServiceTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ServiceTime() {
    }

    /** 한국 기준 오늘. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** 한국 날짜의 시작 시각을 저장 기준(UTC)으로 바꾼다. */
    public static LocalDateTime startOfDayInUtc(LocalDate date) {
        return date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
