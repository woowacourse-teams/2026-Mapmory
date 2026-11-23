package com.mapmory.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * dev·prod의 DB 타임아웃 설정이 서로 맞물리는지 확인한다.
 *
 * <p>앱 연결은 응답 없는 DB를 기다리다 풀이 묶이지 않게 끊고, 마이그레이션 연결은 오래 걸리는 문장이 끊기지 않게 둔다.
 */
class DatabaseTimeoutConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 앱_연결은_응답을_3초까지만_기다린다(String profile) throws IOException {
        assertThat(property(profile, "spring.datasource.url")).contains("socketTimeout=3000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 잠금_대기_한도는_소켓_타임아웃보다_짧아_1205가_먼저_온다(String profile) throws IOException {
        String url = property(profile, "spring.datasource.url");

        // MySQL은 한도를 초 단위로 확인해 한도 + 1초까지 걸릴 수 있다. 그 뒤에도 소켓 타임아웃까지 여유가 있어야 한다.
        long lockWaitMillis = Long.parseLong(parameter(url, "sessionVariables=innodb_lock_wait_timeout")) * 1_000;
        long socketTimeoutMillis = Long.parseLong(parameter(url, "socketTimeout"));

        assertThat(lockWaitMillis + 1_000).isLessThan(socketTimeoutMillis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 풀에서_커넥션은_500밀리까지만_기다린다(String profile) throws IOException {
        assertThat(property(profile, "spring.datasource.hikari.connection-timeout")).isEqualTo("500");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void 마이그레이션_연결은_같은_DB에_소켓_타임아웃_없이_붙는다(String profile) throws IOException {
        String flywayUrl = property(profile, "spring.flyway.url");

        assertThat(flywayUrl).doesNotContain("socketTimeout").doesNotContain("innodb_lock_wait_timeout");
        assertThat(property(profile, "spring.datasource.url")).startsWith(flywayUrl);
        assertThat(property(profile, "spring.flyway.user")).isEqualTo(property(profile, "spring.datasource.username"));
        assertThat(property(profile, "spring.flyway.password"))
                .isEqualTo(property(profile, "spring.datasource.password"));
    }

    private static String parameter(String url, String name) {
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=(\\d+)").matcher(url);
        assertThat(matcher.find()).as("%s가 주소에 있어야 한다", name).isTrue();
        return matcher.group(1);
    }

    private static String property(String profile, String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-" + profile,
                new ClassPathResource("application-" + profile + ".yaml")
        );
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .map(String::valueOf)
                .findFirst()
                .orElse(null);
    }
}
