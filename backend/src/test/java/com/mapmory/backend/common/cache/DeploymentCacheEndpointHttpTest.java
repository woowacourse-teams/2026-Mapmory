package com.mapmory.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** DB와 외부 연동 없이 실제 앱/관리 HTTP 포트를 따로 띄워 경계를 검증한다. */
@SpringBootTest(
        classes = DeploymentCacheEndpointHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=optional:classpath:/deployment-cache-test-only.yaml",
                "management.server.port=0",
                "management.server.address=127.0.0.1",
                "deployment.cache.initial-mode=BYPASS",
                "management.endpoints.access.default=none",
                "management.endpoints.web.exposure.include=deploymentcache",
                "management.endpoint.deploymentcache.access=unrestricted"
        }
)
class DeploymentCacheEndpointHttpTest {

    @Value("${local.server.port}")
    private int applicationPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private DeploymentCacheControl control;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void 내부_관리_포트에서만_조회와_모드_전환을_허용하고_잘못된_명령은_거부한다() throws Exception {
        assertThat(applicationPort).isNotEqualTo(managementPort);
        assertMode(send(managementPort, null), "BYPASS");
        assertMode(send(managementPort, "{\"mode\":\"NORMAL\"}"), "NORMAL");
        assertMode(send(managementPort, "{\"mode\":\"BYPASS\"}"), "BYPASS");
        assertMode(send(managementPort, null), "BYPASS");

        // Nginx처럼 localhost에서 앱 포트로 전달하더라도 제어는 불가능해야 한다.
        assertThat(send(applicationPort, null).statusCode()).isIn(401, 403, 404);
        assertThat(send(applicationPort, "{\"mode\":\"NORMAL\"}").statusCode()).isIn(401, 403, 404);
        assertThat(control.snapshot().mode()).isEqualTo(DeploymentCacheControl.Mode.BYPASS);

        assertThat(send(managementPort, "{\"mode\":\"INVALID\"}").statusCode()).isEqualTo(400);
        assertThat(send(managementPort, "{}").statusCode()).isEqualTo(400);
        assertThat(control.snapshot().mode()).isEqualTo(DeploymentCacheControl.Mode.BYPASS);
        assertMode(send(managementPort, "{\"mode\":\"NORMAL\"}"), "NORMAL");
        assertThat(control.snapshot().generation()).isEqualTo(3);
    }

    private HttpResponse<String> send(int port, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/deploymentcache"))
                .timeout(Duration.ofSeconds(5));
        if (body != null) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertMode(HttpResponse<String> response, String mode) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.mode")).isEqualTo(mode);
    }

    // @Configuration/@Component를 붙이지 않아 다른 통합 테스트의 컴포넌트 스캔에 섞이지 않게 한다.
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class
    })
    @Import({DeploymentCacheControl.class, DeploymentCacheEndpoint.class, DeploymentCacheSecurityConfig.class})
    static class TestApplication {
    }
}
