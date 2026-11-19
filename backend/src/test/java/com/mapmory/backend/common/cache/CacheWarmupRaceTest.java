package com.mapmory.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CacheWarmupRaceTest {

    @Test
    void 무효화와_겹친_워밍업은_옛_값을_남기지_않는다() throws Exception {
        CountDownLatch invalidated = new CountDownLatch(1);
        AtomicReference<String> cache = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var warmup = executor.submit(() -> {
                String loaded = "stale";
                waitFor(invalidated);
                cache.compareAndSet(null, loaded);
            });
            cache.set(null);
            invalidated.countDown();
            warmup.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(cache.get()).isNotEqualTo("fresh");
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
