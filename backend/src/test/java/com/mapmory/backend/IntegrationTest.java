package com.mapmory.backend;

import com.mapmory.backend.support.MySqlTestContainerSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

/**
 * DB가 필요한 통합 테스트의 공통 설정.
 *
 * MySQL 컨테이너는 테스트 JVM에서 하나를 공유한다.
 * 각 테스트는 운영과 같은 트랜잭션 경계로 실행하고, 종료 후 변경 데이터를 별도로 정리한다.
 *
 * 이미지 태그는 운영 RDS와 동일한 8.4로 고정한다. latest 금지.
 *
 * S3 권한 자가진단은 끈다. 켜두면 컨텍스트를 띄울 때마다 실제 AWS 자격증명 조회와 S3 호출을
 * 시도해, 자격증명이 없는 환경에서 타임아웃만큼 테스트가 느려진다.
 */
@SpringBootTest(properties = "upload.storage.s3.permission-self-check=false")
@Sql(
        scripts = "/db/cleanup-test-data.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
public abstract class IntegrationTest extends MySqlTestContainerSupport {
}
