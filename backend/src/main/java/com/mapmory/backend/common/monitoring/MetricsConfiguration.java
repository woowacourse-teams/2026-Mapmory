package com.mapmory.backend.common.monitoring;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    static final int MAX_HTTP_SERVER_URI_TAG_VALUES = 50;
    static final String VERSION_TAG = "version";

    @Bean
    MeterFilter httpServerUriCardinalityLimit() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests",
                "uri",
                MAX_HTTP_SERVER_URI_TAG_VALUES,
                MeterFilter.deny()
        );
    }

    // 이름이 http.server.requests로 시작하므로 위의 uri 개수 제한도 함께 받는다.
    @Bean
    HttpServerSloObservationHandler httpServerSloObservationHandler(MeterRegistry meterRegistry) {
        return new HttpServerSloObservationHandler(meterRegistry);
    }

    // 공통 태그(management.metrics.tags)가 붙인 version을 SLO 카운터에서만 뗀다. 배포마다 시계열이 새로 생기지 않게 한다.
    // 공통 태그를 붙이는 필터보다 뒤에 돌아야 하므로 순서를 맨 뒤에 둔다.
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    MeterFilter httpServerSloWithoutVersion() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!HttpServerSloObservationHandler.METRIC_NAME.equals(id.getName())
                        || id.getTag(VERSION_TAG) == null) {
                    return id;
                }
                return id.replaceTags(id.getTags().stream()
                        .filter(tag -> !VERSION_TAG.equals(tag.getKey()))
                        .toList());
            }
        };
    }
}
