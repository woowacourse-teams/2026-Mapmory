package com.mapmory.backend.common.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * 배포 캐시 모드를 지표로 내보낸다. BYPASS면 1, NORMAL이면 0.
 *
 * <p>슬롯은 BYPASS로 시작하고 배포 스크립트가 단독 서비스를 확인한 뒤 NORMAL로 켠다. 켜는 단계가 빠지면
 * 응답 값은 맞은 채로 모든 조회가 DB로 가서 아무도 알아채지 못한다. BYPASS는 캐시를 호출하지 않아
 * {@code cache_gets_total}에도 잡히지 않으므로, 모드를 따로 내보내 대시보드와 알람이 보게 한다.
 */
@Component
class DeploymentCacheMetrics implements MeterBinder {

    static final String METRIC_NAME = "mapmory.deployment.cache.bypass";

    private final DeploymentCacheControl control;

    DeploymentCacheMetrics(DeploymentCacheControl control) {
        this.control = control;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(METRIC_NAME, control, it -> it.snapshot().bypass() ? 1 : 0)
                .description("배포 캐시 모드. BYPASS면 1, NORMAL이면 0")
                .register(registry);
    }
}
