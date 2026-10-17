package com.mapmory.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.mapmory.backend.auth.infrastructure.apple.AppleIdentityAdapter;
import com.mapmory.backend.auth.infrastructure.google.GoogleIdentityAdapter;
import com.mapmory.backend.auth.infrastructure.kakao.KakaoIdentityAdapter;
import com.mapmory.backend.auth.web.ratelimit.GuestLoginRateLimitProperties;
import com.mapmory.backend.auth.web.ratelimit.GuestLoginRateLimiter;
import com.mapmory.backend.place.infrastructure.google.GooglePlacesClient;
import com.mapmory.backend.place.infrastructure.google.GoogleRequestLimiter;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import com.mapmory.backend.support.SecuredTestController;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.UploadedObjectChecker;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

/**
 * DB가 필요한 통합 테스트의 공통 설정.
 *
 * MySQL 컨테이너는 테스트 JVM에서 하나를 공유한다.
 * 각 테스트는 운영과 같은 트랜잭션 경계로 실행하고, 종료 후 변경 데이터를 별도로 정리한다.
 *
 * 이미지 태그는 운영 RDS와 동일한 8.4로 고정한다. latest 금지.
 *
 * S3 권한 자가진단(ADR 0018)은 끈다. 실제 AWS를 호출하는 진단이라 테스트에서 돌 이유가 없고,
 * 인스턴스 역할이 붙은 러너에서는 운영 버킷을 조회하게 된다.
 * 진단 자체는 S3PermissionSelfCheckTest가 대역으로 검증한다.
 *
 * 나머지 설정도 모든 통합 테스트가 같은 값을 쓴다. 테스트마다 다르게 주면 컨텍스트를 따로 띄우게 된다.
 *
 *   - 커넥션을 기다리는 테스트가 오래 멈추지 않게 대기 시간을 줄인다.
 *   - Google 주소는 닿지 않는 곳으로 둔다. 개발 PC에 API 키가 있어도 실제 Google을 부르지 않고,
 *     stub하지 않은 조회는 PLACE_PROVIDER_UNAVAILABLE로 실패한다. 키는 설정 누락으로 먼저 실패하지 않게 채운다.
 *   - 운영처럼 공용 버킷 prefix를 둔다.
 *
 * MockMvc는 쓰지 않는 테스트에도 켠다. 테스트마다 켜고 끄면 컨텍스트 캐시 키가 달라져
 * 같은 설정의 컨텍스트를 새로 띄우게 된다.
 *
 * 외부 연동 빈(소셜 로그인 adapter, Google Places와 호출 한도, S3 업로드 확인과 presigned URL, 미디어 URL)과
 * 게스트 로그인 한도도 같은 이유로 여기서 spy로 둔다.
 * stub하지 않으면 실제 메서드를 그대로 부르고, stub은 테스트가 끝나면 초기화되므로 이 빈을 쓰지 않는 테스트의 동작은 바뀌지 않는다.
 * spy는 {@code given(spy.method())}로 stub하면 그 순간 실제 메서드가 불린다. {@code willReturn(...).given(spy).method()}로 stub한다.
 *
 * 인증 테스트용 컨트롤러(SecuredTestController)도 같은 이유로 모든 통합 테스트에 등록한다.
 *
 * 게스트 로그인 한도는 limiter가 메모리에 출처별로 센다. 컨텍스트를 같이 쓰면 앞선 테스트의 카운트가 남아
 * 관계없는 테스트가 429로 깨질 수 있으므로, 테스트마다 운영 설정값으로 만든 새 limiter에 위임한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecuredTestController.class)
@TestPropertySource(properties = {
        "upload.storage.s3.permission-self-check=false",
        "spring.datasource.hikari.connection-timeout=500",
        "google.places.api-key=test-key",
        "google.places.base-url=http://127.0.0.1:9",
        "upload.storage.s3.key-prefix=mapmory"
})
@Sql(
        scripts = "/db/cleanup-test-data.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
public abstract class IntegrationTest extends MySqlTestContainerSupport {

    @MockitoSpyBean
    protected KakaoIdentityAdapter kakaoIdentityAdapter;

    @MockitoSpyBean
    protected AppleIdentityAdapter appleIdentityAdapter;

    @MockitoSpyBean
    protected GoogleIdentityAdapter googleIdentityAdapter;

    @MockitoSpyBean
    protected GuestLoginRateLimiter guestLoginRateLimiter;

    @MockitoSpyBean
    protected GooglePlacesClient googlePlacesClient;

    @MockitoSpyBean
    protected GoogleRequestLimiter googleRequestLimiter;

    @MockitoSpyBean
    protected UploadedObjectChecker uploadedObjectChecker;

    @MockitoSpyBean
    protected PresignedUrlProvider presignedUrlProvider;

    @MockitoSpyBean
    protected RecordMediaUrlService recordMediaUrlService;

    @Autowired
    private GuestLoginRateLimitProperties guestLoginRateLimitProperties;

    @BeforeEach
    void startGuestLoginRateLimitFresh() {
        GuestLoginRateLimiter limiter = new GuestLoginRateLimiter(guestLoginRateLimitProperties);
        willAnswer(invocation -> limiter.tryAcquire(invocation.getArgument(0)))
                .given(guestLoginRateLimiter).tryAcquire(anyString());
    }
}
