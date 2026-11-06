package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class UnlinkedMediaPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    // 설정이 빠진 환경에서 지우기 시작하면 안 된다.
    @Test
    void 설정이_없으면_드라이런이고_유예_기간은_3일이다() {
        contextRunner.run(context -> {
            UnlinkedMediaProperties properties = context.getBean(UnlinkedMediaProperties.class);

            assertThat(properties.dryRun()).isTrue();
            assertThat(properties.gracePeriod()).isEqualTo(Duration.ofDays(3));
        });
    }

    @Test
    void 유예_기간을_하루보다_짧게_둘_수_없다() {
        assertThatThrownBy(() -> new UnlinkedMediaProperties(true, Duration.ofHours(23)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration
    @EnableConfigurationProperties(UnlinkedMediaProperties.class)
    static class PropertiesConfig {
    }
}
