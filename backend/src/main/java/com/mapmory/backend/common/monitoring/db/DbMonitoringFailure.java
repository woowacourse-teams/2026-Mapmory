package com.mapmory.backend.common.monitoring.db;

import org.slf4j.Logger;

/**
 * 계측 코드가 실패했을 때 남기는 경고. 업무 SQL·요청은 그대로 진행하고, 계측이 고장 났다는 것만 알린다.
 *
 * <p>WARN이라 대시보드 WARN 위젯과 Discord 알림에 잡힌다. 계속 나면 {@code DB_MONITORING_ENABLED=false}로 계측을 빼고 고친다.
 */
final class DbMonitoringFailure {

    static final String EVENT = "DB_MONITORING_FAILED";

    private DbMonitoringFailure() {
    }

    static void warn(Logger log, String stage, RuntimeException exception) {
        log.atWarn()
                .addKeyValue("event", EVENT)
                .addKeyValue("stage", stage)
                .setCause(exception)
                .log("DB 사용량 계측이 실패했다. 요청과 SQL은 그대로 진행한다");
    }
}
