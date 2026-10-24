package com.mapmory.backend.member.application;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 같은 소셜 계정의 회원이 이미 있어 (provider, provider_id) UNIQUE에 걸렸는지 판단한다.
 *
 * 동시에 들어온 첫 로그인은 가입이면 저장할 때, 게스트 승격이면 커밋할 때 위반이 드러난다.
 * 그래서 호출부가 트랜잭션 밖에서 판단해야 하고, 제약 이름은 member가 알도록 여기에 둔다. (#461)
 */
public final class SocialAccountConflict {

    private static final String SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT = "uk_member_provider";

    private SocialAccountConflict() {
    }

    public static boolean isCausedBy(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && isSocialAccountConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // MySQL은 제약 이름 앞에 테이블 이름을 붙여 알려 주기도 한다(member.uk_member_provider).
    private static boolean isSocialAccountConstraint(String constraintName) {
        return constraintName != null
                && (constraintName.equalsIgnoreCase(SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT)
                || constraintName.toLowerCase().endsWith("." + SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT));
    }
}
