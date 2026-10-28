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
     *
     * <p>ERROR는 운영 알람으로 간다. 장애를 원인이 난 곳에서 따로 알리는 기능은 요청마다 알람이 울리지 않도록
     * 서비스 장애도 WARN으로 낮춘다(PlaceErrorCode).
     */
    default Level logLevel() {
        return kind() == ErrorKind.SERVICE_UNAVAILABLE ? Level.ERROR : Level.DEBUG;
    }
}
