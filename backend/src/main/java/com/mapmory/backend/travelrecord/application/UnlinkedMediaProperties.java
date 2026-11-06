package com.mapmory.backend.travelrecord.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 기록에 붙지 않은 S3 객체 대조 설정. (#186)
 *
 * <p>dryRun이면 후보만 세고 큐에 넣지 않는다. 설정이 빠진 환경이 지우기 시작하지 않도록 기본값은 true다.
 * gracePeriod보다 최근에 올라간 객체는 아직 저장 중일 수 있어 건드리지 않는다. 하루보다 짧게 둘 수 없다.
 */
@ConfigurationProperties(prefix = "upload.unlinked-media")
public record UnlinkedMediaProperties(
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("3d") Duration gracePeriod
) {

    private static final Duration MIN_GRACE_PERIOD = Duration.ofDays(1);

    public UnlinkedMediaProperties {
        if (gracePeriod.compareTo(MIN_GRACE_PERIOD) < 0) {
            throw new IllegalArgumentException("upload.unlinked-media.grace-period는 하루 이상이어야 합니다.");
        }
    }
}
