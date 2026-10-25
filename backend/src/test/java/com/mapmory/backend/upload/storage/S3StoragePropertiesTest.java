package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class S3StoragePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "upload.storage.s3.bucket=mapmory-test",
                    "upload.storage.s3.region=ap-northeast-2",
                    "upload.storage.s3.client.attempt-timeout=2s",
                    "upload.storage.s3.client.total-timeout=6s",
                    "upload.storage.s3.client.max-attempts=3"
            );

    @ParameterizedTest
    @ValueSource(strings = {"PT1S", "PT604800S"})
    void 서명_URL_유효시간의_경곗값을_허용한다(String expiration) {
        contextRunner
                .withPropertyValues("upload.storage.s3.signed-url-expiration=" + expiration)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(S3StorageProperties.class).signedUrlExpiration())
                            .isEqualTo(Duration.parse(expiration));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "PT604801S"})
    void 범위를_벗어난_서명_URL_유효시간은_속성_바인딩에_실패한다(String expiration) {
        contextRunner
                .withPropertyValues("upload.storage.s3.signed-url-expiration=" + expiration)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @EnableConfigurationProperties(S3StorageProperties.class)
    static class PropertiesConfig {
    }
}
