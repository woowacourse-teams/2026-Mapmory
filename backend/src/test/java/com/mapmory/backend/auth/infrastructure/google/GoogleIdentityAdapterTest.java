package com.mapmory.backend.auth.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.member.AuthProvider;
import org.junit.jupiter.api.Test;

class GoogleIdentityAdapterTest {

    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final GoogleIdentityAdapter adapter = new GoogleIdentityAdapter(googleIdTokenVerifier);

    @Test
    void 구글_제공자를_맡는다() {
        assertThat(adapter.provider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    void 구글_sub와_이름을_신원으로_바꾼다() {
        given(googleIdTokenVerifier.verify("id-token")).willReturn(new GoogleUser("109876543210", "소현"));

        SocialIdentity identity = adapter.verify("id-token");

        assertThat(identity.providerId()).isEqualTo("109876543210");
        assertThat(identity.name()).isEqualTo("소현");
    }
}
