package com.mapmory.backend.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.member.AuthProvider;
import org.junit.jupiter.api.Test;

class KakaoIdentityAdapterTest {

    private final KakaoIdTokenVerifier kakaoIdTokenVerifier = mock(KakaoIdTokenVerifier.class);
    private final KakaoIdentityAdapter adapter = new KakaoIdentityAdapter(kakaoIdTokenVerifier);

    @Test
    void 카카오_제공자를_맡는다() {
        assertThat(adapter.provider()).isEqualTo(AuthProvider.KAKAO);
    }

    @Test
    void 카카오_회원번호와_닉네임을_신원으로_바꾼다() {
        given(kakaoIdTokenVerifier.verify("id-token")).willReturn(new KakaoUser("12345", "소현"));

        SocialIdentity identity = adapter.verify("id-token");

        assertThat(identity.providerId()).isEqualTo("12345");
        assertThat(identity.name()).isEqualTo("소현");
    }

    @Test
    void 닉네임에_동의하지_않았으면_이름_없이_바꾼다() {
        given(kakaoIdTokenVerifier.verify("id-token")).willReturn(new KakaoUser("12345", null));

        SocialIdentity identity = adapter.verify("id-token");

        assertThat(identity.providerId()).isEqualTo("12345");
        assertThat(identity.name()).isNull();
    }
}
