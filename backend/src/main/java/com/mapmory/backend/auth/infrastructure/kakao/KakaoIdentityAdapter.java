package com.mapmory.backend.auth.infrastructure.kakao;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import com.mapmory.backend.member.AuthProvider;
import org.springframework.stereotype.Component;

/** 카카오 ID token을 검증해 회원번호와 닉네임을 꺼낸다. (ADR 0024) */
@Component
public class KakaoIdentityAdapter implements SocialIdentityPort {

    private final KakaoIdTokenVerifier kakaoIdTokenVerifier;

    KakaoIdentityAdapter(KakaoIdTokenVerifier kakaoIdTokenVerifier) {
        this.kakaoIdTokenVerifier = kakaoIdTokenVerifier;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        KakaoUser kakaoUser = kakaoIdTokenVerifier.verify(idToken);
        return new SocialIdentity(kakaoUser.subject(), kakaoUser.nickname());
    }
}
