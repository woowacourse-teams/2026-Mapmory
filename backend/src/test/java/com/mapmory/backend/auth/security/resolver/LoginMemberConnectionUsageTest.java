package com.mapmory.backend.auth.security.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인한 회원의 요청 하나가 DB 커넥션을 몇 번 빌리는지 확인한다.
 *
 * <p>open-in-view가 꺼져 있어, {@code @LoginMember}를 채우려고 회원을 조회하면 서비스 트랜잭션과 별개로
 * 커넥션을 한 번 더 빌린다. 부하 테스트에서 요청당 평균 2.2번 빌려 커넥션 풀(10개)이 먼저 고갈됐다.
 * 회원 주입은 커넥션을 빌리지 않아야 한다.
 *
 * <p>빌린 횟수는 Hikari가 커넥션을 내줄 때마다 기록하는 {@code hikaricp.connections.acquire} 타이머로 센다.
 */
class LoginMemberConnectionUsageTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void 로그인한_회원의_기록_목록_조회는_커넥션을_한_번만_빌린다() throws Exception {
        String accessToken = guestAccessToken();

        long before = connectionAcquireCount();
        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        long acquired = connectionAcquireCount() - before;

        assertThat(acquired).isEqualTo(1);
    }

    private long connectionAcquireCount() {
        Timer timer = meterRegistry.find("hikaricp.connections.acquire").timer();
        assertThat(timer).as("Hikari 커넥션 메트릭이 등록되어 있어야 한다").isNotNull();
        return timer.count();
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
