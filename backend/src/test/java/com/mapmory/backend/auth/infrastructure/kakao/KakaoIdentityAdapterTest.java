package com.mapmory.backend.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.infrastructure.kakao.KakaoUserResponse.KakaoAccount;
import com.mapmory.backend.auth.infrastructure.kakao.KakaoUserResponse.KakaoAccount.Profile;
import com.mapmory.backend.member.AuthProvider;
import org.junit.jupiter.api.Test;

class KakaoIdentityAdapterTest {

    private final KakaoApiClient kakaoApiClient = mock(KakaoApiClient.class);
    private final KakaoIdentityAdapter adapter = new KakaoIdentityAdapter(kakaoApiClient);

    @Test
    void 카카오_제공자를_맡는다() {
        assertThat(adapter.provider()).isEqualTo(AuthProvider.KAKAO);
    }

    @Test
    void 카카오_회원번호와_닉네임을_신원으로_바꾼다() {
        given(kakaoApiClient.fetchUser("kakao-token"))
                .willReturn(new KakaoUserResponse(12345L, new KakaoAccount(new Profile("소현"))));

        SocialIdentity identity = adapter.verify("kakao-token");

        assertThat(identity.providerId()).isEqualTo("12345");
        assertThat(identity.name()).isEqualTo("소현");
    }

    @Test
    void 닉네임에_동의하지_않았으면_이름_없이_바꾼다() {
        given(kakaoApiClient.fetchUser("kakao-token")).willReturn(new KakaoUserResponse(12345L, null));

        SocialIdentity identity = adapter.verify("kakao-token");

        assertThat(identity.providerId()).isEqualTo("12345");
        assertThat(identity.name()).isNull();
    }
}
