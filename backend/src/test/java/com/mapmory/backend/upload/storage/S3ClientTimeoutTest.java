package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * S3가 연결은 받고 답하지 않을 때 요청 스레드를 얼마나 쥐는지 확인한다.
 *
 * <p>운영과 같은 {@link S3StorageConfig}와 application.yaml 값으로 클라이언트를 만들고,
 * 접속 주소만 AWS SDK가 읽는 시스템 속성으로 테스트 서버에 돌린다.
 */
class S3ClientTimeoutTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(S3StorageConfig.class, S3UploadedObjectChecker.class);

    @Test
    void S3가_답하지_않으면_6초_안에_세_번_시도하고_저장소_장애로_끝낸다() throws IOException {
        try (SilentS3 s3 = SilentS3.start()) {
            contextRunner
                    .withSystemProperties(
                            "aws.endpointUrlS3=" + s3.url(),
                            "aws.accessKeyId=test",
                            "aws.secretAccessKey=test"
                    )
                    .run(context -> {
                        UploadedObjectChecker checker = context.getBean(UploadedObjectChecker.class);

                        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                                assertThatThrownBy(() -> checker.exists("travel-records/1/photo.jpg"))
                                        .isInstanceOf(BusinessException.class)
                                        .extracting(exception -> ((BusinessException) exception).getErrorCode())
                                        .isEqualTo(UploadErrorCode.STORAGE_UNAVAILABLE));
                        assertThat(s3.requests()).isEqualTo(3);
                    });
        }
    }

    /** 연결을 받고 요청을 읽기만 하는 S3다. 응답은 보내지 않는다. */
    private static final class SilentS3 implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final AtomicInteger requests = new AtomicInteger();

        private SilentS3(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        static SilentS3 start() throws IOException {
            SilentS3 s3 = new SilentS3(new ServerSocket(0));
            Thread.ofPlatform().daemon().start(s3::acceptForever);
            return s3;
        }

        String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        int requests() {
            return requests.get();
        }

        private void acceptForever() {
            try {
                while (true) {
                    Socket connection = serverSocket.accept();
                    connections.add(connection);
                    if (connection.getInputStream().read(new byte[8192]) > 0) {
                        requests.incrementAndGet();
                    }
                }
            } catch (IOException closed) {
                // close()가 소켓을 닫으면 끝난다.
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            for (Socket connection : connections) {
                connection.close();
            }
        }
    }
}
