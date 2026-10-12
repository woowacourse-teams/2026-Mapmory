package com.mapmory.backend.auth.web.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 출처별 고정 윈도우 카운터.
 *
 * 게스트 로그인은 인증 없이 호출되므로 계정을 무제한으로 찍어낼 수 있고, 그 계정으로
 * presigned URL을 발급받으면 저장소 비용 남용으로 이어진다. (ADR 0015)
 *
 * 애플리케이션 인스턴스가 하나라는 전제로 메모리에 상태를 둔다.
 * 인스턴스를 늘리면 한도가 인스턴스 수만큼 늘어나므로, 그때는 공유 저장소로 옮겨야 한다.
 */
public class GuestLoginRateLimiter {

    // 출처가 무한히 늘어나 메모리를 잠식하지 않도록 상한을 둔다. 초과하면 만료된 항목부터 버린다.
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int capacity;
    private final Duration window;
    private final Clock clock;

    public GuestLoginRateLimiter(GuestLoginRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    GuestLoginRateLimiter(GuestLoginRateLimitProperties properties, Clock clock) {
        this.capacity = properties.capacity();
        this.window = properties.window();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        evictIfCrowded(now);

        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.isExpired(now, window) ? new Window(now) : existing);
        return current.increment() <= capacity;
    }

    private void evictIfCrowded(Instant now) {
        if (windows.size() < MAX_TRACKED_KEYS) {
            return;
        }
        windows.values().removeIf(tracked -> tracked.isExpired(now, window));
    }

    private static final class Window {

        private final Instant startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(startedAt.plus(window));
        }

        private int increment() {
            return count.incrementAndGet();
        }
    }
}
