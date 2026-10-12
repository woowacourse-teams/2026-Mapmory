package com.mapmory.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProperties;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    void 토큰이_없으면_401_ProblemDetails로_응답한다() throws Exception {
        mockMvc.perform(get("/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void 유효한_토큰이면_보호된_API에_접근하고_Member가_주입된다() throws Exception {
        Member member = memberRepository.save(Member.of("인증 테스트 회원", UUID.randomUUID()));
        String token = jwtProvider.issueAccessToken(member.getId());

        mockMvc.perform(get("/test/secured").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(member.getId().toString()));
    }

    @Test
    void 만료된_토큰이면_401과_EXPIRED_ACCESS_TOKEN을_응답한다() throws Exception {
        String expiredToken = new JwtProvider(new JwtProperties(secret, Duration.ofSeconds(-1), Duration.ofDays(14), Duration.ofDays(365)))
                .issueAccessToken(7L);

        mockMvc.perform(get("/test/secured").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EXPIRED_ACCESS_TOKEN"));
    }

    @Test
    void 화이트리스트_경로는_토큰_없이_접근된다() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void 화이트리스트_경로의_에러는_401로_덮이지_않고_원래_상태가_유지된다() throws Exception {
        // permitAll 인 /api/v1/auth/** 아래 없는 경로 → 404가 /error로 재디스패치된다.
        // ERROR 디스패치가 permitAll이 아니면 이 응답이 401로 덮인다.
        mockMvc.perform(post("/api/v1/auth/no-such-endpoint"))
                .andExpect(status().isNotFound());
    }

    @Test
    void Actuator_health는_토큰_없이_접근할_수_있다() throws Exception{
        mockMvc.perform(get ("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void Prometheus_Metric은_토큰_없이_조회할_수_있다() throws Exception {
        // HTTP 서버 요청 메트릭이 생성되도록 일반 엔드포인트를 먼저 호출한다.
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get ("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# HELP")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections")))
                .andExpect(content().string(containsString("# TYPE http_server_requests_seconds summary")))
                .andExpect(content().string(containsString("http_server_requests_seconds{")))
                .andExpect(content().string(containsString("http_server_requests_seconds_count")))
                .andExpect(content().string(containsString("quantile=\"0.95\"")))
                .andExpect(content().string(containsString("uri=\"/health\"")))
                .andExpect(content().string(containsString("status=\"200\"")))
                .andExpect(content().string(containsString("service=\"mapmory-backend\"")))
                .andExpect(content().string(containsString("environment=\"local\"")));
    }

    @TestConfiguration
    static class SecuredTestControllerConfig {

        @Bean
        SecuredTestController securedTestController() {
            return new SecuredTestController();
        }
    }

    @RestController
    static class SecuredTestController {

        @GetMapping("/test/secured")
        Long secured(@LoginMember Member member) {
            return member.getId();
        }
    }
}
