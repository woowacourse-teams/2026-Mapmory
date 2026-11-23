package com.mapmory.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class DeploymentCacheSecurityConfigTest {

    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("local.management.port", "8081")
            .withProperty("management.server.address", "127.0.0.1");

    @Test
    void 별도_관리_포트로_들어온_로컬_요청만_허용한다() {
        assertThat(allowed("127.0.0.1", 8081)).isTrue();
        assertThat(allowed("::1", 8081)).isTrue();
        assertThat(allowed("203.0.113.10", 8081)).isFalse();
        assertThat(allowed("127.0.0.1", 8080)).isFalse();
    }

    @Test
    void 프록시_헤더로_관리_포트를_사칭해도_공개_포트에서는_거부한다() {
        MockHttpServletRequest request = request("127.0.0.1", 8080);
        request.setServerPort(8081);
        request.addHeader("Host", "127.0.0.1:8081");
        request.addHeader("X-Forwarded-Port", "8081");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertThat(DeploymentCacheSecurityConfig.isLocalManagementRequest(request, environment)).isFalse();
    }

    @Test
    void 관리_포트를_분리하지_않았거나_실제_포트를_모르면_거부한다() {
        MockHttpServletRequest request = request("127.0.0.1", 8081);
        assertThat(DeploymentCacheSecurityConfig.isLocalManagementRequest(request, new MockEnvironment())).isFalse();
        environment.withProperty("local.server.port", "8081");
        assertThat(DeploymentCacheSecurityConfig.isLocalManagementRequest(request, environment)).isFalse();
    }

    @Test
    void 관리_서버가_외부_인터페이스에_바인딩된_설정이면_거부한다() {
        environment.withProperty("management.server.address", "0.0.0.0");
        assertThat(allowed("127.0.0.1", 8081)).isFalse();
        environment.withProperty("management.server.address", "::");
        assertThat(allowed("::1", 8081)).isFalse();
    }

    private boolean allowed(String address, int port) {
        return DeploymentCacheSecurityConfig.isLocalManagementRequest(request(address, port), environment);
    }

    private MockHttpServletRequest request(String address, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        request.setLocalPort(port);
        return request;
    }
}
