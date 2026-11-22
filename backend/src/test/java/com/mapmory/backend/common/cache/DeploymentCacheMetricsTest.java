package com.mapmory.backend.common.cache;

import static com.mapmory.backend.common.cache.DeploymentCacheControl.Mode.BYPASS;
import static com.mapmory.backend.common.cache.DeploymentCacheControl.Mode.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배포 캐시 모드 지표")
class DeploymentCacheMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("BYPASS로 시작하면 1이고, NORMAL로 바꾸면 0이 된다")
    void bypassGaugeFollowsCurrentMode() {
        DeploymentCacheControl control = new DeploymentCacheControl(BYPASS);
        new DeploymentCacheMetrics(control).bindTo(registry);

        assertThat(gauge()).isEqualTo(1.0);

        control.changeMode(NORMAL);

        assertThat(gauge()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("NORMAL로 시작하면 0이고, 다시 BYPASS로 바꾸면 1이 된다")
    void bypassGaugeRisesWhenSwitchedBack() {
        DeploymentCacheControl control = new DeploymentCacheControl(NORMAL);
        new DeploymentCacheMetrics(control).bindTo(registry);

        assertThat(gauge()).isEqualTo(0.0);

        control.changeMode(BYPASS);

        assertThat(gauge()).isEqualTo(1.0);
    }

    private double gauge() {
        return registry.get(DeploymentCacheMetrics.METRIC_NAME).gauge().value();
    }
}
