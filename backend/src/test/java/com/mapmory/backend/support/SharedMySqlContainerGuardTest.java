package com.mapmory.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 테스트가 MySQL 컨테이너를 따로 띄우지 않게 막는다.
 *
 * 컨테이너 하나를 띄우는 데 약 10초가 걸린다. 2026-08에 컨테이너를 테스트 JVM 하나에서 같이 쓰도록 바꿨지만,
 * 그 뒤 마이그레이션 테스트가 하나씩 추가되며 테스트마다 컨테이너를 새로 띄워 CI가 다시 4분대가 됐다(#594).
 * 규칙이 주석에만 있어서 어겨도 아무것도 실패하지 않았기 때문이다.
 *
 * 컨테이너는 {@link MySqlTestContainerSupport}만 만든다.
 *   - DB가 필요한 통합 테스트는 IntegrationTest를 상속한다.
 *   - 빈 DB나 옛 버전 스키마가 필요한 테스트는 {@link MigrationTestDatabase#create()}로 공용 컨테이너 안에 새 DB를 받는다.
 */
@DisplayName("MySQL 테스트 컨테이너 공유 규칙")
class SharedMySqlContainerGuardTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path ALLOWED = TEST_SOURCES.resolve("com/mapmory/backend/support/MySqlTestContainerSupport.java");
    private static final Pattern NEW_CONTAINER = Pattern.compile("new\\s+([\\w.]+\\.)?(MySQLContainer|GenericContainer)\\b");

    @Test
    @DisplayName("MySqlTestContainerSupport 밖에서는 컨테이너를 만들지 않는다")
    void onlySupportCreatesContainer() throws IOException {
        List<Path> violations;
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(ALLOWED))
                    .filter(this::createsContainer)
                    .toList();
        }

        assertThat(violations)
                .as("MySQL 컨테이너를 따로 띄우면 하나에 약 10초씩 CI가 느려진다. "
                        + "통합 테스트는 IntegrationTest를 상속하고, 빈 DB가 필요하면 MigrationTestDatabase.create()를 쓴다.")
                .isEmpty();
    }

    private boolean createsContainer(Path path) {
        try {
            return NEW_CONTAINER.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }
}
