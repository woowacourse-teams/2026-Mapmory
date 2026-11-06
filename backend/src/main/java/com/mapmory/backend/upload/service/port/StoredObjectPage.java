package com.mapmory.backend.upload.service.port;

import java.util.List;

/**
 * 객체 목록 한 쪽. nextToken이 null이면 마지막 쪽이다.
 */
public record StoredObjectPage(List<StoredObject> objects, String nextToken) {

    public StoredObjectPage {
        objects = List.copyOf(objects);
    }
}
