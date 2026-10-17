package com.mapmory.backend.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.member.AuthProvider;
import org.junit.jupiter.api.Test;

class AppleIdentityAdapterTest {

    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier = mock(AppleIdentityTokenVerifier.class);
    private final AppleIdentityAdapter adapter = new AppleIdentityAdapter(appleIdentityTokenVerifier);

    @Test
    void 애플_제공자를_맡는다() {
        assertThat(adapter.provider()).isEqualTo(AuthProvider.APPLE);
    }

    @Test
    void 애플_sub를_신원으로_바꾸고_이름은_비워_둔다() {
        given(appleIdentityTokenVerifier.verify("identity-token")).willReturn(new AppleUser("001234.abcdef.0123"));

        SocialIdentity identity = adapter.verify("identity-token");

        assertThat(identity.providerId()).isEqualTo("001234.abcdef.0123");
        assertThat(identity.name()).isNull();
    }
}
