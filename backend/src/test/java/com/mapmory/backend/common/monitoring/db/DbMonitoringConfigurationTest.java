package com.mapmory.backend.common.monitoring.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 계측은 기본으로 켜지고, {@code monitoring.db.enabled=false}면 설정 전체가 빠진다. ADR 0032 7절.
 */
class DbMonitoringConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DbMonitoringConfiguration.class)
            .withBean("dataSource", DataSource.class, () -> mock(DataSource.class));

    @Test
    void 설정이_없으면_DataSource를_감싸고_느린_요청_핸들러를_둔다() {
        contextRunner.run(context -> {
            assertThat(context.getBean(DataSource.class)).isInstanceOf(ProxyDataSource.class);
            assertThat(context).hasSingleBean(SlowRequestObservationHandler.class);
        });
    }

    // /etc/mapmory.env에 DB_MONITORING_ENABLED=만 남아 있어도 꺼지지 않는다.
    @Test
    void 값이_비어_있으면_켠다() {
        contextRunner.withPropertyValues("monitoring.db.enabled=")
                .run(context -> assertThat(context.getBean(DataSource.class)).isInstanceOf(ProxyDataSource.class));
    }

    @Test
    void false면_DataSource를_감싸지_않고_계측_빈도_없다() {
        contextRunner.withPropertyValues("monitoring.db.enabled=false").run(context -> {
            assertThat(context.getBean(DataSource.class)).isNotInstanceOf(ProxyDataSource.class);
            assertThat(context)
                    .doesNotHaveBean(SlowRequestObservationHandler.class)
                    .doesNotHaveBean(RequestDbUsageListener.class);
        });
    }
}
