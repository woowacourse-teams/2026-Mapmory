package com.mapmory.backend.auth.application.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialIdentityTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 회원_식별자가_비어_있으면_만들_수_없다(String providerId) {
        assertThatThrownBy(() -> new SocialIdentity(providerId, "소현"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이름은_없어도_된다() {
        assertThatCode(() -> new SocialIdentity("12345", null)).doesNotThrowAnyException();
    }
}
