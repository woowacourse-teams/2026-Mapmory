package com.mapmory.backend.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class RequestPathsTest {

    @Test
    void 매칭된_경로_패턴을_돌려준다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invitations/secret-token/accept");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/invitations/{tokenOrCode}/accept");

        assertThat(RequestPaths.patternOf(request)).isEqualTo("/api/v1/invitations/{tokenOrCode}/accept");
        assertThat(RequestPaths.instanceOf(request).toString())
                .isEqualTo("/api/v1/invitations/%7BtokenOrCode%7D/accept");
    }

    @Test
    void 패턴이_없으면_원본_대신_UNKNOWN을_돌려준다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invitations/secret-token/accept");

        assertThat(RequestPaths.patternOf(request)).isEqualTo(RequestPaths.UNKNOWN);
        assertThat(RequestPaths.instanceOf(request).toString()).isEqualTo(RequestPaths.UNKNOWN);
    }
}
