package com.mapmory.backend.common.monitoring.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mapmory.backend.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class DbMonitoringIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 앱의_서버_요청_Observation_안에서_실행한_SQL을_실제_DB에서_센다() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", "/api/v1/travel-records"), new MockHttpServletResponse());
        Observation observation = Observation.createNotStarted(
                new DefaultServerRequestObservationConvention(), () -> context, observationRegistry).start();
        try {
            jdbcTemplate.queryForObject("select count(*) from member", Long.class);
            jdbcTemplate.queryForObject("select count(*) from travel_record", Long.class);
            jdbcTemplate.queryForObject("select count(*) from tag", Long.class);

            assertThat(RequestDbUsage.current()).isNotNull();
            assertThat(RequestDbUsage.current().queryCount()).isEqualTo(3);
            assertThat(RequestDbUsage.current().dbTimeNanos()).isPositive();
        } finally {
            observation.stop();
        }
        assertThat(RequestDbUsage.current()).isNull();
    }

    // DataSource를 감싸도 Hikari 지표와 풀 설정은 안쪽 HikariDataSource를 그대로 찾는다.
    @Test
    void DataSource를_감싸도_Hikari_풀과_지표는_그대로다() throws Exception {
        assertThat(dataSource).isInstanceOf(ProxyDataSource.class);
        assertThat(dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()).isPositive();

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn().getResponse().getContentAsString();

        assertThat(scrape)
                .contains("hikaricp_connections_max{")
                .contains("hikaricp_connections_acquire_seconds_count{");
    }
}
