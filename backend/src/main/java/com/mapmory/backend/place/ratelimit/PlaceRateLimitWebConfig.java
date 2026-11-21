package com.mapmory.backend.place.ratelimit;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.dropoff.DropOffRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 장소 API마다 어떤 회원 한도를 확인할지 정한다. 한도를 어떻게 세는지는 {@link MemberPlaceRateLimiter}가 맡는다.
 *
 * <p>WebMvcConfigurer는 @WebMvcTest 슬라이스에도 들어간다. 한도 구현체는 DB가 필요해 슬라이스에 없으므로
 * {@code @Lazy}로 첫 요청 때 찾는다.
 */
@Configuration
class PlaceRateLimitWebConfig implements WebMvcConfigurer {

    private static final String PLACES = "/api/v1/places/";

    private final MemberPlaceRateLimiter rateLimiter;
    private final DropOffRecorder recorder;

    PlaceRateLimitWebConfig(@Lazy MemberPlaceRateLimiter rateLimiter, @Lazy DropOffRecorder recorder) {
        this.rateLimiter = rateLimiter;
        this.recorder = recorder;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PlaceRateLimitInterceptor(rateLimiter::checkSearch, this::searchRejected))
                .addPathPatterns("/api/v1/places/search");
        registry.addInterceptor(new PlaceRateLimitInterceptor(rateLimiter::checkSelection, this::selectionRejected))
                .addPathPatterns("/api/v1/places/*")
                .excludePathPatterns("/api/v1/places/search");
    }

    // 한도에 막힌 검색·선택도 막힌 지점이라 남긴다. 컨트롤러에 들어가기 전이라 요청에서 직접 읽는다.
    private void searchRejected(Long memberId, HttpServletRequest request, BusinessException exception) {
        recorder.searchFailed(context(memberId, request), request.getParameter("sessionToken"),
                request.getParameter("query"), exception.getErrorCode().code());
    }

    // 같은 한도를 쓰는 사진 좌표 → 지역(POST /location)은 장소 선택이 아니라 남기지 않는다.
    private void selectionRejected(Long memberId, HttpServletRequest request, BusinessException exception) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return;
        }
        String uri = request.getRequestURI();
        String placeId = uri.substring(uri.indexOf(PLACES) + PLACES.length());
        recorder.selectFailed(context(memberId, request), request.getParameter("sessionToken"), placeId,
                exception.getErrorCode().code());
    }

    private static DropOffContext context(Long memberId, HttpServletRequest request) {
        return new DropOffContext(memberId, request.getHeader(HttpHeaders.USER_AGENT));
    }
}
