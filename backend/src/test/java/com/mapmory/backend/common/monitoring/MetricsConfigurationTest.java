package com.mapmory.backend.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class MetricsConfigurationTest {

    @Test
    void HTTP_응답_시간은_p50_p95_p99_Summary로_수집한다() throws IOException {
        List<PropertySource<?>> sources = applicationYaml();

        assertThat(property(sources, "management.metrics.distribution.percentiles-histogram.http.server.requests"))
                .isNull();
        assertThat(property(sources, "management.metrics.distribution.slo.http.server.requests"))
                .isNull();
        assertThat(percentiles(sources, "http.server.requests"))
                .containsExactly(0.5, 0.95, 0.99);
    }

    @Test
    void 내부_작업_응답_시간은_p95_p99_Summary로_수집한다() throws IOException {
        List<PropertySource<?>> sources = applicationYaml();

        assertThat(property(sources, "management.metrics.distribution.slo.mapmory.operation.duration"))
                .isNull();
        assertThat(percentiles(sources, "mapmory.operation.duration"))
                .containsExactly(0.95, 0.99);
    }

    @Test
    void Tomcat_스레드_풀_지표를_노출한다() throws IOException {
        List<PropertySource<?>> sources = applicationYaml();

        assertThat(property(sources, "server.tomcat.mbeanregistry.enabled"))
                .isEqualTo(true);
    }

    @Test
    void HTTP_URI_태그는_허용된_개수까지만_등록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricsConfiguration().httpServerUriCardinalityLimit());

        for (int index = 0; index <= MetricsConfiguration.MAX_HTTP_SERVER_URI_TAG_VALUES; index++) {
            Timer.builder("http.server.requests")
                    .tag("uri", "/test/" + index)
                    .register(registry);
        }

        assertThat(registry.find("http.server.requests").timers())
                .hasSize(MetricsConfiguration.MAX_HTTP_SERVER_URI_TAG_VALUES);
    }

    private static List<PropertySource<?>> applicationYaml() throws IOException {
        return new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yaml")
        );
    }

    private static List<Object> percentiles(List<PropertySource<?>> sources, String meterName) {
        String prefix = "management.metrics.distribution.percentiles." + meterName;
        List<Object> values = new ArrayList<>();
        for (int index = 0; property(sources, prefix + "[" + index + "]") != null; index++) {
            values.add(property(sources, prefix + "[" + index + "]"));
        }
        return values;
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
