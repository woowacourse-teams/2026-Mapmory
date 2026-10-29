package com.mapmory.backend.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AppleRevocationAdapterTest {

    private final AppleIdentityTokenVerifier verifier = mock(AppleIdentityTokenVerifier.class);
    private final AppleTokenClient tokenClient = mock(AppleTokenClient.class);
    private final AppleRevocationAdapter adapter = new AppleRevocationAdapter(verifier, tokenClient);

    @Test
    void 재인증한_identity_token의_sub와_앱을_돌려준다() {
        given(verifier.verify("identity-token")).willReturn(new AppleUser("apple-sub", "com.mapmory.ios"));

        assertThat(adapter.verify("identity-token"))
                .isEqualTo(new AppleReauthentication("apple-sub", "com.mapmory.ios"));
    }

    @Test
    void 잘못된_identity_token은_401이_아닌_재인증_실패다() {
        given(verifier.verify("bad")).willThrow(new BusinessException(AuthErrorCode.INVALID_APPLE_TOKEN));

        assertThatThrownBy(() -> adapter.verify("bad"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED));
    }

    @Test
    void 애플_장애는_그대로_던진다() {
        given(verifier.verify("token")).willThrow(new BusinessException(AuthErrorCode.APPLE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.verify("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_UNAVAILABLE));
    }

    @Test
    void 토큰이_발급된_앱의_client_id로_철회한다() {
        adapter.revoke(new AppleReauthentication("apple-sub", "com.mapmory.ios.internal"), "auth-code");

        then(tokenClient).should().revoke(new AppleReauthentication("apple-sub", "com.mapmory.ios.internal"), "auth-code");
    }
}
