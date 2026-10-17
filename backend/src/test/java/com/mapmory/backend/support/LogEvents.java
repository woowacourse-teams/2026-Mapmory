package com.mapmory.backend.support;

import ch.qos.logback.classic.spi.ILoggingEvent;

public final class LogEvents {

    private LogEvents() {
    }

    /** 로그에 addKeyValue로 남긴 값을 꺼낸다. 없으면 null이다. */
    public static Object field(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) {
            return null;
        }
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
