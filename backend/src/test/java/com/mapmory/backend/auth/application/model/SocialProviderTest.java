package com.mapmory.backend.auth.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SocialProviderTest {

    @Test
    void 같은_이름의_회원_제공자로_바꾼다() {
        for (SocialProvider provider : SocialProvider.values()) {
            assertThat(provider.toAuthProvider().name()).isEqualTo(provider.name());
        }
    }
}
