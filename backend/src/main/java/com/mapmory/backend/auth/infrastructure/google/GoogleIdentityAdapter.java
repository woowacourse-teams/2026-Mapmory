package com.mapmory.backend.auth.infrastructure.google;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import com.mapmory.backend.member.AuthProvider;
import org.springframework.stereotype.Component;

/** 구글 ID token을 검증해 sub와 이름을 꺼낸다. (ADR 0019) */
@Component
public class GoogleIdentityAdapter implements SocialIdentityPort {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    GoogleIdentityAdapter(GoogleIdTokenVerifier googleIdTokenVerifier) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        GoogleUser googleUser = googleIdTokenVerifier.verify(idToken);
        return new SocialIdentity(googleUser.subject(), googleUser.name());
    }
}
