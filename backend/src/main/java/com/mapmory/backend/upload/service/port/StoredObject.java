package com.mapmory.backend.upload.service.port;

import java.time.Instant;

/**
 * 저장소에 있는 객체 하나. lastModified는 마지막으로 올라간 시각이다.
 */
public record StoredObject(String key, Instant lastModified) {
}
