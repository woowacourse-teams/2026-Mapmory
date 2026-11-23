package com.mapmory.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.LockTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class LockWaitTimeoutsTest {

    @Test
    void Hibernate가_바꾼_잠금_대기_시간_초과를_가려낸다() {
        SQLException timeout = new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205);
        CannotAcquireLockException exception = new CannotAcquireLockException("could not execute statement",
                new LockTimeoutException("could not execute statement", timeout));

        assertThat(LockWaitTimeouts.isLockWaitTimeout(exception)).isTrue();
    }

    @Test
    void 오류_코드_1205만_있어도_가려낸다() {
        CannotAcquireLockException exception = new CannotAcquireLockException("could not execute statement",
                new SQLException("Lock wait timeout exceeded", "HY000", 1205));

        assertThat(LockWaitTimeouts.isLockWaitTimeout(exception)).isTrue();
    }

    @Test
    void 데드락은_대기_시간_초과가_아니다() {
        CannotAcquireLockException exception = new CannotAcquireLockException("could not execute statement",
                new SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001", 1213));

        assertThat(LockWaitTimeouts.isLockWaitTimeout(exception)).isFalse();
    }
}
