package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SocialAccountConflictTest {

    @Test
    void 소셜_계정_UNIQUE_위반이면_충돌이다() {
        assertThat(SocialAccountConflict.isCausedBy(violationOf("uk_member_provider"))).isTrue();
        assertThat(SocialAccountConflict.isCausedBy(violationOf("member.uk_member_provider"))).isTrue();
    }

    @Test
    void 다른_제약_위반은_충돌이_아니다() {
        assertThat(SocialAccountConflict.isCausedBy(violationOf("uk_member_uuid"))).isFalse();
        assertThat(SocialAccountConflict.isCausedBy(violationOf(null))).isFalse();
        assertThat(SocialAccountConflict.isCausedBy(new DataIntegrityViolationException("not null"))).isFalse();
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), constraintName));
    }
}
