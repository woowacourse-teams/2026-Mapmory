package com.mapmory.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.upload.storage.S3PermissionSelfCheck;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 기동 자가진단이 실제로 동작하는지 인수 관점에서 고정한다. (ADR 0018)
 *
 * 단위 테스트는 메서드를 직접 불러 "어떤 객체를 조회하는지"와 "기동을 막지 않는지"를 검증한다.
 * 그러나 이 기능의 값은 <b>운영자가 기동 로그 한 줄로 권한 상태를 판정할 수 있다</b>는 것뿐이다.
 * 이슈 #172 의 완료 조건도 "기동 로그에서 S3_PERMISSION_OK 가 찍히는지 확인한다"이다.
 *
 * 그래서 단위 테스트로는 덮이지 않는 두 가지를 여기서 고정한다.
 *
 *   - @EventListener 가 ApplicationReadyEvent 에 실제로 반응하는가
 *     (단위 테스트는 메서드를 직접 부르므로, 애너테이션이 사라져도 통과한다)
 *   - 약속한 event 키가 실제로 로그에 남는가
 *     (키가 바뀌면 운영자의 확인 절차가 조용히 실패한다)
 *
 * 이 둘은 자가진단 빈 하나만 띄운 컨텍스트로 충분하다. 통합 테스트 컨텍스트는 자가진단을 끄므로,
 * 거기서 켜면 이 테스트만을 위한 컨텍스트를 하나 더 띄우게 된다.
 */
class S3PermissionSelfCheckAcceptanceTest {

    private static final String BUCKET_OBJECT = "mapmory/travel-records/1/photo.jpg";

    private final S3Client s3Client = mock(S3Client.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(S3Client.class, () -> s3Client)
            .withBean(S3StorageProperties.class, () -> new S3StorageProperties(
                    "mapmory-test",
                    "ap-northeast-2",
                    "mapmory",
                    new S3StorageProperties.Client(Duration.ofSeconds(2), Duration.ofSeconds(6), 3)
            ))
            .withUserConfiguration(S3PermissionSelfCheck.class);

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        selfCheckLogger().addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        selfCheckLogger().detachAppender(logs);
        logs.stop();
    }

    @Test
    void 조회_권한이_있으면_기동_로그에_확인_결과를_남긴다() {
        givenObjectExists();
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().build());

        publishApplicationReady();

        assertThat(loggedEvents()).contains("S3_PERMISSION_OK");
    }

    @Test
    void 객체_조회가_거부되면_어느_권한이_없는지_남긴다() {
        givenObjectExists();
        willThrow(forbidden()).given(s3Client).headObject(any(HeadObjectRequest.class));

        publishApplicationReady();

        assertThat(loggedEvents()).contains("S3_PERMISSION_DENIED");
        assertThat(deniedPermission()).isEqualTo("s3:GetObject");
    }

    @Test
    void 목록_조회가_거부되면_어느_권한이_없는지_남긴다() {
        willThrow(forbidden()).given(s3Client).listObjectsV2(any(ListObjectsV2Request.class));

        publishApplicationReady();

        assertThat(loggedEvents()).contains("S3_PERMISSION_DENIED");
        assertThat(deniedPermission()).isEqualTo("s3:ListBucket");
    }

    @Test
    void 저장소가_응답하지_않으면_판정을_보류한다() {
        willThrow(SdkClientException.create("connection reset"))
                .given(s3Client).listObjectsV2(any(ListObjectsV2Request.class));

        publishApplicationReady();

        assertThat(loggedEvents()).contains("S3_PERMISSION_UNKNOWN");
    }

    @Test
    void 저장된_객체가_없으면_일부만_확인했음을_남긴다() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .willReturn(ListObjectsV2Response.builder().build());

        publishApplicationReady();

        assertThat(loggedEvents()).contains("S3_PERMISSION_PARTIALLY_VERIFIED");
    }

    private void publishApplicationReady() {
        contextRunner.run(context -> context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(),
                new String[0],
                context,
                Duration.ZERO
        )));
    }

    private void givenObjectExists() {
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .willReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(BUCKET_OBJECT).build())
                        .build());
    }

    private java.util.List<String> loggedEvents() {
        return logs.list.stream()
                .map(event -> event.getKeyValuePairs() == null ? null : event.getKeyValuePairs().stream()
                        .filter(pair -> "event".equals(pair.key))
                        .map(pair -> String.valueOf(pair.value))
                        .findFirst()
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String deniedPermission() {
        return logs.list.stream()
                .filter(event -> event.getKeyValuePairs() != null)
                .flatMap(event -> event.getKeyValuePairs().stream())
                .filter(pair -> "permission".equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    private Logger selfCheckLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "com.mapmory.backend.upload.storage.S3PermissionSelfCheck"
        );
        logger.setLevel(Level.DEBUG);
        return logger;
    }

    private S3Exception forbidden() {
        return (S3Exception) S3Exception.builder()
                .statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                        .build())
                .build();
    }
}
