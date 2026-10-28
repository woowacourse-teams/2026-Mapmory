package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvitationKeyGeneratorTest {

    private final InvitationKeyGenerator generator = new InvitationKeyGenerator();

    @Test
    void 토큰은_링크에_그대로_넣을_수_있는_43자다() {
        assertThat(generator.token()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void 코드는_헷갈리는_글자를_뺀_6자리다() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.code()).matches("[A-HJ-NP-Z2-9]{6}");
        }
    }

    @Test
    void 매번_다른_값을_만든다() {
        Set<String> tokens = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(generator.token());
            codes.add(generator.code());
        }

        assertThat(tokens).hasSize(100);
        assertThat(codes).hasSizeGreaterThan(95);
    }
}
