package com.mapmory.backend.common.http;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 로그와 오류 응답에 남길 요청 경로.
 *
 * <p>원본 URI({@code request.getRequestURI()})에는 경로 변수로 받은 비밀값이 그대로 들어간다. 예를 들어 초대 토큰을 아는
 * 사람은 그룹에 들어갈 수 있다. 그래서 로그와 응답에는 매칭된 경로 패턴({@code /api/v1/invitations/{tokenOrCode}})만 쓴다.
 * 메트릭의 {@code uri} 태그도 같은 패턴이다.
 *
 * <p>패턴이 없으면(시큐리티 필터에서 끝난 요청, 매핑되는 핸들러가 없는 요청) 원본 대신 {@link #UNKNOWN}을 쓴다.
 * 앞 몇 칸만 남기는 방식은 경로마다 어디까지 안전한지 따로 따져야 해서 쓰지 않는다.
 *
 * <p>{@code /error}(ERROR 디스패치)로 넘어간 응답은 Boot의 BasicErrorController가 만든다. 그 응답의 {@code path}는
 * {@code spring.web.error.include-path: never}로 끈다.
 */
public final class RequestPaths {

    public static final String UNKNOWN = "UNKNOWN";

    private RequestPaths() {
    }

    public static String patternOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null || pattern.toString().isBlank()) {
            return UNKNOWN;
        }
        return pattern.toString();
    }

    /**
     * ProblemDetail {@code instance}용 값이다. 패턴의 {@code {}}는 URI에 그대로 쓸 수 없어 퍼센트 인코딩된다.
     */
    public static URI instanceOf(HttpServletRequest request) {
        try {
            return new URI(null, null, patternOf(request), null);
        } catch (URISyntaxException exception) {
            return URI.create(UNKNOWN);
        }
    }
}
