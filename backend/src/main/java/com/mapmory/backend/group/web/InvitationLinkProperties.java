package com.mapmory.backend.group.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 초대 링크 주소의 앞부분. 토큰을 뒤에 붙여 {@code https://map-mory.com/invite/{토큰}}을 만든다.
 *
 * <p>링크 형식은 서버 한 곳에서만 정한다. 주소가 바뀌어도 앱을 다시 배포하지 않아도 된다.
 */
@ConfigurationProperties(prefix = "group.invitation.link")
public record InvitationLinkProperties(String baseUrl) {

    public InvitationLinkProperties {
        if (baseUrl == null || !baseUrl.matches("https?://[^/\\s]+(/[^\\s]*)?/")) {
            throw new IllegalArgumentException("초대 링크 주소는 http(s)로 시작하고 /로 끝나야 합니다.");
        }
    }

    public String linkOf(String token) {
        return baseUrl + token;
    }
}
