package com.mapmory.backend.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class HttpServerSloMetricsIntegrationTest extends IntegrationTest {

    private static final String RAW_TOKEN = "slo-raw-invitation-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void SLO_카운터는_경로_패턴으로_노출되고_기존_p95_Summary는_그대로다() throws Exception {
        Member member = memberRepository.save(Member.of("SLO 지표 회원", UUID.randomUUID()));
        String accessToken = jwtProvider.issueAccessToken(member.getId());
        mockMvc.perform(get("/api/v1/invitations/" + RAW_TOKEN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn().getResponse().getContentAsString();

        String sloLine = "http_server_requests_slo_total{environment=\"local\",le=\"%s\",method=\"GET\","
                + "outcome=\"CLIENT_ERROR\",service=\"mapmory-backend\",slo_class=\"other\","
                + "uri=\"/api/v1/invitations/{tokenOrCode}\"}";
        assertThat(scrape)
                .contains("# TYPE http_server_requests_slo_total counter")
                .contains(sloLine.formatted("0.3"), sloLine.formatted("1"), sloLine.formatted("2"),
                        sloLine.formatted("+Inf"))
                .doesNotContain(RAW_TOKEN);
        // SLO 버킷을 http.server.requests에 붙이면 histogram이 되어 quantile이 사라진다. 그러지 않았는지 본다.
        assertThat(scrape)
                .contains("# TYPE http_server_requests_seconds summary")
                .doesNotContain("http_server_requests_seconds_bucket");
        assertThat(scrape.lines()
                .filter(line -> line.startsWith("http_server_requests_seconds{"))
                .filter(line -> line.contains("uri=\"/api/v1/invitations/{tokenOrCode}\"")))
                .anySatisfy(line -> assertThat(line).contains("quantile=\"0.5\""))
                .anySatisfy(line -> assertThat(line).contains("quantile=\"0.95\""))
                .anySatisfy(line -> assertThat(line).contains("quantile=\"0.99\""));
    }

    // 분류표의 경로가 컨트롤러에서 바뀌면 그 API가 조용히 other로 빠진다. 표의 경로가 모두 실제로 있는지 본다.
    @Test
    void slo_class_분류표의_경로는_모두_실제_API다() {
        Set<String> routes = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getMethodsCondition().getMethods().stream()
                        .flatMap(method -> info.getPatternValues().stream()
                                .map(pattern -> method.name() + " " + pattern)))
                .collect(Collectors.toSet());

        assertThat(routes).containsAll(SloClass.ROUTES.keySet());
    }
}
