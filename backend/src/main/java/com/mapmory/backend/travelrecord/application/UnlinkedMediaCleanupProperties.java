package com.mapmory.backend.travelrecord.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 기록에 붙지 않은 S3 객체 정리 설정. (#186)
 *
 * <p>dryRun이면 후보만 세고 지우지 않는다. 처음 배포할 때 후보가 정말 고아인지 보고 끈다. 설정이 빠져도 지우지 않도록 기본값은 true다.
 * gracePeriod보다 최근에 올라간 객체는 저장 요청이 아직 오는 중일 수 있어 건드리지 않는다. 하루보다 짧게 둘 수 없다.
 */
@ConfigurationProperties(prefix = "upload.unlinked-cleanup")
public record UnlinkedMediaCleanupProperties(
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("3d") Duration gracePeriod
) {

    private static final Duration MIN_GRACE_PERIOD = Duration.ofDays(1);

    public UnlinkedMediaCleanupProperties {
        if (gracePeriod == null || gracePeriod.compareTo(MIN_GRACE_PERIOD) < 0) {
            throw new IllegalArgumentException("upload.unlinked-cleanup.grace-period는 하루 이상이어야 합니다.");
        }
    }
}
