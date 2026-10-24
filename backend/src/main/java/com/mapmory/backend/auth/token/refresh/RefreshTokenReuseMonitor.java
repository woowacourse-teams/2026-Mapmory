package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * 폐기된 refresh 토큰이 다시 온 사건을 로그·메트릭으로 남긴다. (ADR 0014, #446)
 *
 * 응답은 처음부터 없던 토큰·탈퇴 회원과 같은 401이라, 운영에서 탈취 감지가 몇 번 일어났는지는 여기서만 알 수 있다.
 * 토큰 원문·해시는 남기지 않는다. memberId는 로그에만 두고 메트릭 태그로 쓰지 않는다.
 */
@Component
class RefreshTokenReuseMonitor {

    static final String METRIC_NAME = "mapmory.auth.refresh.reuse";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenReuseMonitor.class);

    private final MeterRegistry meterRegistry;

    RefreshTokenReuseMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 탈취로 보고 회원의 refresh를 모두 폐기했다.
     *
     * successorUnused가 true면 후속 토큰을 아무도 쓰지 않았다. 소셜 회원이라면 회전 응답 유실로 강제 로그아웃됐을 가능성이 크다.
     */
    void revokedAll(RefreshToken reused, boolean successorUnused) {
        withContext(log.atWarn(), reused)
                .addKeyValue("event", "REFRESH_TOKEN_REUSE_DETECTED")
                .addKeyValue("successorUnused", successorUnused)
                .log("폐기된 refresh 토큰이 다시 와 회원의 refresh를 모두 폐기했다");
        count(Outcome.REVOKED_ALL, reused.getMember());
    }

    /**
     * 게스트의 회전 응답 유실로 보고 재발급했다. (ADR 0023)
     */
    void reissued(RefreshToken lost) {
        withContext(log.atInfo(), lost)
                .addKeyValue("event", "REFRESH_TOKEN_LOST_ROTATION_REISSUED")
                .log("게스트가 회전 응답을 받지 못한 refresh 토큰으로 재발급했다");
        count(Outcome.REISSUED, lost.getMember());
    }

    // revokedAt은 이번 요청 전에 폐기된 시각이다. 지금과 비교하면 폐기 뒤 얼마 만에 다시 왔는지 알 수 있다.
    private LoggingEventBuilder withContext(LoggingEventBuilder event, RefreshToken token) {
        Member member = token.getMember();
        return event.addKeyValue("memberId", member.getId())
                .addKeyValue("provider", providerTag(member))
                .addKeyValue("refreshTokenId", token.getId())
                .addKeyValue("revokedAt", token.getRevokedAt());
    }

    private void count(Outcome outcome, Member member) {
        Counter.builder(METRIC_NAME)
                .description("Revoked refresh tokens presented again")
                .tag("outcome", outcome.name())
                .tag("provider", providerTag(member))
                .register(meterRegistry)
                .increment();
    }

    private static String providerTag(Member member) {
        AuthProvider provider = member.getProvider();
        return provider == null ? "NONE" : provider.name();
    }

    enum Outcome {
        REVOKED_ALL,
        REISSUED
    }
}
