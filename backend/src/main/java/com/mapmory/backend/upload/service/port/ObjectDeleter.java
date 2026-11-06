package com.mapmory.backend.upload.service.port;

import java.util.Collection;
import java.util.List;

/**
 * 업로드 저장소의 객체를 영구히 지우는 포트.
 */
public interface ObjectDeleter {

    /**
     * 객체를 모든 버전까지 영구히 지운다. 되돌릴 수 없다. 없는 객체는 지운 것으로 본다.
     *
     * 저장소 장애로 지우지 못해도 예외를 던지지 않고 실패한 키를 돌려준다. 다시 지울지는 부르는 쪽이 정한다.
     * 우리 경로 밖의 키가 있으면 아무것도 지우지 않고 IllegalArgumentException을 던진다.
     *
     * @return 지우지 못한 키
     */
    List<String> delete(Collection<String> objectKeys);
}
