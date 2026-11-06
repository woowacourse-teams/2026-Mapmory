package com.mapmory.backend.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class MaintenanceSchedulingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MaintenanceSchedulingConfig.class);

    /**
     * 스케줄러를 지정하지 않으면 어느 스레드에서 도는지가 떠 있는 TaskScheduler 빈에 따라 달라진다. 알림 발송과 같은 스레드가 될 수 있다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "com.mapmory.backend.notification.infrastructure.PushTokenCleanupScheduler",
            "com.mapmory.backend.group.infrastructure.InactiveMemberGroupCleanupScheduler",
            "com.mapmory.backend.place.ratelimit.PlaceRateLimitCleanup",
            "com.mapmory.backend.auth.infrastructure.RefreshTokenCleanupScheduler",
            "com.mapmory.backend.travelrecord.infrastructure.OrphanedMediaCleanupScheduler",
            "com.mapmory.backend.travelrecord.infrastructure.UnlinkedMediaCleanupScheduler"
    })
    void 정리_배치는_정리_전용_스케줄러를_명시한다(String className) throws ClassNotFoundException {
        Method scheduled = Arrays.stream(Class.forName(className).getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .findFirst()
                .orElseThrow();

        assertThat(scheduled.getAnnotation(Scheduled.class).scheduler())
                .isEqualTo(MaintenanceSchedulingConfig.SCHEDULER);
    }

    @Test
    void 기본값이면_정리_전용_스케줄러가_뜬다() {
        contextRunner.run(context -> assertThat(context).hasBean(MaintenanceSchedulingConfig.SCHEDULER));
    }

    @Test
    void API를_받지_않는_서버에서는_정리_전용_스케줄러가_뜨지_않는다() {
        contextRunner.withPropertyValues("notification.outbox.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MaintenanceSchedulingConfig.SCHEDULER));
    }
}
