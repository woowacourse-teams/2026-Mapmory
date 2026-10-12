package com.mapmory.backend.auth.infrastructure.kakao;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import com.mapmory.backend.member.AuthProvider;
import org.springframework.stereotype.Component;

/** 카카오 access token으로 회원번호와 닉네임을 조회한다. (ADR 0010) */
@Component
public class KakaoIdentityAdapter implements SocialIdentityPort {

    private final KakaoApiClient kakaoApiClient;

    KakaoIdentityAdapter(KakaoApiClient kakaoApiClient) {
        this.kakaoApiClient = kakaoApiClient;
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialIdentity verify(String kakaoAccessToken) {
        KakaoUserResponse kakaoUser = kakaoApiClient.fetchUser(kakaoAccessToken);
        return new SocialIdentity(Long.toString(kakaoUser.id()), kakaoUser.nickname());
    }
}
