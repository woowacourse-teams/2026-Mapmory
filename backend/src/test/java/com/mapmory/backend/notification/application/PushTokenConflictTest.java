package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PushTokenConflictTest {

    @Test
    void 토큰_UNIQUE_제약만_등록_충돌로_분류한다() {
        assertThat(PushTokenConflict.isCausedBy(violationOf("uk_push_token_token"))).isTrue();
        assertThat(PushTokenConflict.isCausedBy(violationOf("push_token.UK_PUSH_TOKEN_TOKEN"))).isTrue();
        assertThat(PushTokenConflict.isCausedBy(violationOf("fk_push_token_member"))).isFalse();
        assertThat(PushTokenConflict.isCausedBy(violationOf(null))).isFalse();
        assertThat(PushTokenConflict.isCausedBy(new DataIntegrityViolationException("not null"))).isFalse();
    }

    private DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException(), constraintName));
    }
}
