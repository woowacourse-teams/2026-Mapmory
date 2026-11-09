package com.mapmory.backend.group.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 초대 링크 주소의 앞부분. 토큰을 뒤에 붙여 {@code https://map-mory.com/invite/{토큰}}을 만든다.
 *
 * <p>링크 형식은 서버 한 곳에서만 정한다. 주소가 바뀌어도 앱을 다시 배포하지 않아도 된다.
 */
@ConfigurationProperties(prefix = "group.invitation.link")
public record InvitationLinkProperties(String baseUrl) {

    // 환경변수를 빈 값(INVITATION_LINK_BASE_URL=)으로 두면 기본값이 아니라 ""가 들어온다. 이때도 기본 주소를 쓴다.
    static final String DEFAULT_BASE_URL = "https://map-mory.com/invite/";

    public InvitationLinkProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        // ?나 #가 섞이면 앱 링크 경로(/invite/*)와 맞지 않는 주소가 나온다.
        if (!baseUrl.matches("https?://[^/\\s?#]+(/[^\\s?#]*)?/")) {
            throw new IllegalArgumentException("초대 링크 주소는 http(s)로 시작하고 /로 끝나야 합니다.");
        }
    }

    public String linkOf(String token) {
        return baseUrl + token;
    }
}
