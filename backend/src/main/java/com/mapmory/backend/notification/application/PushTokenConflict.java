package com.mapmory.backend.notification.application;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class PushTokenConflict {

    private static final String TOKEN_UNIQUE_CONSTRAINT = "uk_push_token_token";

    private PushTokenConflict() {
    }

    public static boolean isCausedBy(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            // 입력 검증 예외가 아니라 Hibernate의 DB 제약 위반 예외를 확인한다.
            if (cause instanceof ConstraintViolationException violation
                    && isTokenConstraint(violation.getConstraintName())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private static boolean isTokenConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }

        String normalized = constraintName.toLowerCase(Locale.ROOT);

        return normalized.equals(TOKEN_UNIQUE_CONSTRAINT)
                || normalized.endsWith("." + TOKEN_UNIQUE_CONSTRAINT);
    }
}
