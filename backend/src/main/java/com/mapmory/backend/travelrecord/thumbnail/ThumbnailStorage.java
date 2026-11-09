package com.mapmory.backend.travelrecord.thumbnail;

import java.nio.file.Path;

/**
 * 썸네일 워커가 쓰는 저장소. 원본은 읽기만 하고, 쓰기는 파생 경로(derived/) 안에서만 한다.
 * 지우기는 정리 큐(orphaned_media)에 맡긴다.
 */
public interface ThumbnailStorage {

    boolean exists(String thumbnailKey);

    void download(String originalKey, Path destination);

    void upload(Path source, String thumbnailKey);
}
