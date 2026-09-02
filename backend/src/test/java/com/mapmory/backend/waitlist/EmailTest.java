package com.mapmory.backend.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    private static final int MAX_LENGTH = 254;

    @Test
    void 앞뒤_공백과_대문자를_정규화한다() {
        assertThat(Email.from("  MapMory.User@Example.COM  ").value())
                .isEqualTo("mapmory.user@example.com");
    }

    @Test
    void 호환_문자를_NFKC로_정규화한다() {
        assertThat(Email.from("ＵＳＥＲ@example.com").value())
                .isEqualTo("user@example.com");
    }

    @Test
    void 대소문자만_다른_주소는_같은_값이_된다() {
        assertThat(Email.from("USER@example.com")).isEqualTo(Email.from("user@EXAMPLE.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 비어_있는_주소를_거부한다(String rawEmail) {
        assertInvalidEmail(() -> Email.from(rawEmail));
    }

    @Test
    void 저장_한계_길이는_허용한다() {
        String localPart = "a".repeat(MAX_LENGTH - "@example.com".length());

        assertThatCode(() -> Email.from(localPart + "@example.com")).doesNotThrowAnyException();
    }

    @Test
    void 저장_한계를_넘는_주소를_거부한다() {
        String localPart = "a".repeat(MAX_LENGTH - "@example.com".length() + 1);

        assertInvalidEmail(() -> Email.from(localPart + "@example.com"));
    }

    @Test
    void 정규화되지_않은_값으로는_만들_수_없다() {
        assertInvalidEmail(() -> new Email("USER@example.com"));
    }

    private void assertInvalidEmail(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("VALIDATION_ERROR");
    }
}
