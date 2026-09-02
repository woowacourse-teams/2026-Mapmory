package com.mapmory.backend.waitlist;

import com.mapmory.backend.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Locale;

/**
 * 출시 알림 신청에 쓰는 이메일. 항상 정규화된 형태로만 존재한다.
 *
 * <p>정규화가 값 객체 밖에 있으면 같은 주소가 다른 문자열로 저장될 수 있다.
 * {@code launch_waitlist.email}은 {@code utf8mb4_0900_bin} 대소문자 구분 콜레이션이라
 * UNIQUE 제약이 {@code A@x.com}과 {@code a@x.com}을 서로 다른 값으로 본다.
 * 중복 신청을 막는 책임이 애플리케이션에 있으므로 생성 경로에서 강제한다.
 *
 * <p>형식 검증(@Email)은 요청 DTO가 담당한다. 여기서는 저장 가능한 형태인지만 본다.
 */
@Embeddable
record Email(
        @Column(name = "email", nullable = false, length = 254)
        String value
) {
    private static final int MAX_LENGTH = 254;

    Email {
        validateNotBlank(value);
        validateLength(value);
        validateNormalized(value);
    }

    static Email from(String rawEmail) {
        validateNotNull(rawEmail);

        return new Email(normalize(rawEmail));
    }

    private static String normalize(String rawEmail) {
        return Normalizer.normalize(rawEmail.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static void validateNotNull(String rawEmail) {
        if (rawEmail == null) {
            throwInvalidEmail();
        }
    }

    private static void validateNotBlank(String value) {
        if (value == null || value.isBlank()) {
            throwInvalidEmail();
        }
    }

    private static void validateLength(String value) {
        if (value.length() > MAX_LENGTH) {
            throwInvalidEmail();
        }
    }

    private static void validateNormalized(String value) {
        if (!value.equals(normalize(value))) {
            throwInvalidEmail();
        }
    }

    private static void throwInvalidEmail() {
        throw new BusinessException(LaunchWaitlistErrorCode.INVALID_EMAIL);
    }
}
