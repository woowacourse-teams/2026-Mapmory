package com.mapmory.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * DB 테스트가 실행되는 하나의 테스트 JVM에서 MySQL 컨테이너를 공유한다.
 *
 * 컨테이너를 Spring Bean으로 등록하지 않아 개별 ApplicationContext의 종료에 영향을 받지 않는다.
 * Testcontainers의 JVM 종료 훅이 테스트 프로세스 종료 시 컨테이너를 정리한다.
 */
public abstract class MySqlTestContainerSupport {

    private static final MySQLContainer<?> MYSQL_CONTAINER = startContainer();

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    }

    static MySQLContainer<?> sharedContainer() {
        return MYSQL_CONTAINER;
    }

    private static MySQLContainer<?> startContainer() {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.4");
        container.start();
        return container;
    }
}
