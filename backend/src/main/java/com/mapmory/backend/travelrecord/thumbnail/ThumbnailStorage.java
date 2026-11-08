package com.mapmory.backend.travelrecord.thumbnail;

import java.nio.file.Path;

/**
 * 썸네일 워커가 쓰는 저장소. 원본은 읽기만 하고, 쓰기와 지우기는 파생 경로(derived/) 안에서만 한다.
 */
public interface ThumbnailStorage {

    boolean exists(String thumbnailKey);

    void download(String originalKey, Path destination);

    void upload(Path source, String thumbnailKey);

    /** 모든 버전까지 지운다. 지우지 못하면 {@link ThumbnailStorageException}을 던진다. */
    void delete(String thumbnailKey);
}
