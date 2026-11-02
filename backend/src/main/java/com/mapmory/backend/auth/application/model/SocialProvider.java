package com.mapmory.backend.auth.application.model;

import com.mapmory.backend.member.AuthProvider;

/**
 * 소셜 로그인 제공자. 게스트는 소셜 로그인이 아니므로 없다.
 *
 * auth 안에서는 이 타입으로 제공자를 다루고, 회원에 저장할 때만 toAuthProvider()로 member의 AuthProvider로 바꾼다.
 */
public enum SocialProvider {
    KAKAO(AuthProvider.KAKAO),
    GOOGLE(AuthProvider.GOOGLE),
    APPLE(AuthProvider.APPLE);

    private final AuthProvider authProvider;

    SocialProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public AuthProvider toAuthProvider() {
        return authProvider;
    }
}
