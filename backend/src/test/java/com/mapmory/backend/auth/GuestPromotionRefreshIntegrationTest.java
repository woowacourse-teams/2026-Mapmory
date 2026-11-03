package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.application.model.LoginResult;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.MemberJoinService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게스트가 소셜 로그인으로 승격될 때, 게스트로 받아 둔 refresh가 다시 오는 경우. (#579)
 *
 * 승격하면 게스트 refresh를 모두 폐기한다. 앱이 저장해 둔 게스트 refresh로 갱신을 시도하는 것은 탈취가 아니므로,
 * 방금 받은 소셜 refresh까지 폐기하면 안 된다.
 *
 * loginWithSocial은 트랜잭션 밖에서 불러야 하므로(Propagation.NEVER) 테스트 메서드에 트랜잭션을 두지 않는다.
 */
class GuestPromotionRefreshIntegrationTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void 승격_뒤_게스트_refresh가_다시_와도_소셜_refresh는_유효하다() {
        Member guest = memberJoinService.joinAsGuest();
        String guestRefresh = refreshTokenService.issue(guest);
        LoginResult promoted = loginWithKakao("500001", guest.getId());

        assertThat(refreshTokenService.rotate(guestRefresh)).isEmpty();

        assertThat(refreshTokenService.rotate(promoted.tokens().refreshToken())).isPresent();
    }

    private LoginResult loginWithKakao(String kakaoId, Long guestId) {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        return authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guestId);
    }
}
