package com.mapmory.backend.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.jwt.JwtProperties;
import com.mapmory.backend.auth.jwt.JwtProvider;
import java.time.Duration;
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
    void 유효한_토큰이면_보호된_API에_접근하고_memberId가_주입된다() throws Exception {
        String token = jwtProvider.issueAccessToken(7L);

        mockMvc.perform(get("/test/secured").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void 만료된_토큰이면_401과_EXPIRED_ACCESS_TOKEN을_응답한다() throws Exception {
        String expiredToken = new JwtProvider(new JwtProperties(secret, Duration.ofSeconds(-1), Duration.ofDays(14)))
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
        Long secured(@LoginMemberId Long memberId) {
            return memberId;
        }
    }
}
