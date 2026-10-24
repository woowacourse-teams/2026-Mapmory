package com.mapmory.backend.auth.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RefreshTokenReuseMonitorTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RefreshTokenReuseMonitor monitor = new RefreshTokenReuseMonitor(meterRegistry);

    private final Logger logger = (Logger) LoggerFactory.getLogger(RefreshTokenReuseMonitor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 전체_폐기는_WARN과_카운터를_남긴다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "400001", "소현", UUID.randomUUID());

        monitor.revokedAll(token(member), true);

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(fields(event))
                .containsEntry("event", "REFRESH_TOKEN_REUSE_DETECTED")
                .containsEntry("provider", "KAKAO")
                .containsEntry("successorUnused", true)
                .containsKeys("memberId", "refreshTokenId", "revokedAt");
        assertThat(count("REVOKED_ALL", "KAKAO")).isEqualTo(1.0);
    }

    @Test
    void 게스트_재발급은_INFO와_카운터를_남긴다() {
        Member guest = Member.ofGuest(UUID.randomUUID().toString(), "회원12345", UUID.randomUUID());

        monitor.reissued(token(guest));

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel().toString()).isEqualTo("INFO");
        assertThat(fields(event))
                .containsEntry("event", "REFRESH_TOKEN_LOST_ROTATION_REISSUED")
                .containsEntry("provider", "GUEST");
        assertThat(count("REISSUED", "GUEST")).isEqualTo(1.0);
    }

    @Test
    void 토큰_해시는_로그에_남기지_않는다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "400002", "소현", UUID.randomUUID());

        monitor.revokedAll(token(member), false);

        assertThat(fields(appender.list.getFirst()).values())
                .doesNotContain("token-hash");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("token-hash");
    }

    private RefreshToken token(Member member) {
        return RefreshToken.issue(member, "token-hash", LocalDateTime.now().plusDays(1));
    }

    private double count(String outcome, String provider) {
        return meterRegistry.get(RefreshTokenReuseMonitor.METRIC_NAME)
                .tag("outcome", outcome)
                .tag("provider", provider)
                .counter()
                .count();
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value == null ? "null" : pair.value));
    }
}
