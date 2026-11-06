package com.mapmory.backend.place.infrastructure.google.guard;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.http.ExternalCallFailure;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google 호출 실패를 우리 에러로 바꾸고 운영에 필요한 로그를 남긴다.
 * 타임아웃은 어느 단계에서 끊겼는지 WARN, 4xx는 키 거절처럼 고칠 때까지 계속되는 실패라 ERROR다(서킷은 4xx를 세지 않는다).
 * 예외 메시지에 요청 URL(좌표)이 들어갈 수 있어 원인 예외는 붙이지 않는다. 장소 API는 좌표가 없어 Google이 준 거절 사유를 남긴다.
 */
final class GoogleFailureTranslator {

    private static final Logger log = LoggerFactory.getLogger(GoogleFailureTranslator.class);
    private static final int MAX_REASON_LENGTH = 500;

    private GoogleFailureTranslator() {
    }

    static BusinessException toError(GoogleApi api, ExternalCallFailure failure) {
        failure.timeoutPhase().ifPresent(phase -> log.atWarn()
                .addKeyValue("event", api.host.eventPrefix + "_TIMEOUT")
                .addKeyValue("phase", phase)
                .log("Google {} 호출이 타임아웃으로 끊겼다: {}", api, phase));
        if (api.notFound(failure)) {
            return new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
        }
        if (failure.rejected()) {
            String reason = api.host.logsRejectionReason ? abbreviate(failure.responseBody()) : "(숨김: 좌표 요청)";
            log.atError()
                    .addKeyValue("event", api.host.eventPrefix + "_REJECTED")
                    .addKeyValue("status", failure.status())
                    .addKeyValue("reason", reason)
                    .log("Google {} 요청이 거절됐다: {} {}", api, failure.status(), reason);
        }
        return unavailable();
    }

    // 키 거절·API 미허용 같은 사유는 앞부분에 있다. 로그 한 줄이 너무 길어지지 않게 자른다.
    private static String abbreviate(String body) {
        return body.length() <= MAX_REASON_LENGTH ? body : body.substring(0, MAX_REASON_LENGTH) + "...";
    }

    static BusinessException unavailable() {
        return new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }
}
