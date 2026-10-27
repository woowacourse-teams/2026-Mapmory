package com.mapmory.backend.common.exception;

import java.io.Serializable;
import org.slf4j.event.Level;

public interface ErrorCode extends Serializable {

    ErrorKind kind();

    String code();

    String title();

    String detail();

    /**
     * BusinessExceptionHandler가 이 오류를 남길 로그 레벨. 서비스 장애는 ERROR, 나머지는 DEBUG다.
     * 기본 로그 레벨(INFO)에서도 보여야 하는 오류는 이 메서드를 덮어쓴다.
     */
    // TODO: SERVICE_UNAVAILABLE 전체를 ERROR로 기록할지, KAKAO_UNAVAILABLE만 대상으로 할지 논의 필요
    default Level logLevel() {
        return kind() == ErrorKind.SERVICE_UNAVAILABLE ? Level.ERROR : Level.DEBUG;
    }
}
