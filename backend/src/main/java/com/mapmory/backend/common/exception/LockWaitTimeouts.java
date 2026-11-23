package com.mapmory.backend.common.exception;

import java.sql.SQLException;

/**
 * DB 잠금 충돌 가운데 잠금 대기 시간 초과(MySQL 1205, MySQL 기본 50초·dev와 prod는 1초)를 가려낸다. (#753)
 *
 * <p>Spring은 데드락(1213)과 잠금 대기 시간 초과를 모두 {@code PessimisticLockingFailureException}
 * ({@code CannotAcquireLockException})으로 바꾼다. 데드락은 바로 실패해 다시 해도 싸지만, 대기 시간 초과는 한도까지 기다린 뒤라
 * 다시 하면 요청이 두 배로 길어진다. 또 대개 오래 열린 트랜잭션이나 잠금을 놓지 않는 버그의 신호라 따로 알려야 한다.
 */
public final class LockWaitTimeouts {

    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

    private LockWaitTimeouts() {
    }

    public static boolean isLockWaitTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.LockTimeoutException
                    || cause instanceof jakarta.persistence.LockTimeoutException) {
                return true;
            }
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }
}
