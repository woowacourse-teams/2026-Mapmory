package com.mapmory.backend.travelrecord.thumbnail;

import java.nio.file.Path;

/**
 * 원본 파일 하나를 썸네일 파일로 바꾼다. 다시 하면 나을 수 있는 실패는 {@link ThumbnailMakeException}을 던진다.
 */
public interface ThumbnailMaker {

    Outcome make(Path original, Path thumbnail);

    /**
     * 변환에 필요한 것(스크립트, 실행기, 패키지)이 준비됐는지 확인한다. 아니면 {@link ThumbnailMakeException}을 던진다.
     */
    void checkReady();

    enum Outcome {
        CREATED,
        /** 원본 내용 때문에 만들 수 없다. 다시 해도 같다. */
        UNDECODABLE
    }
}
