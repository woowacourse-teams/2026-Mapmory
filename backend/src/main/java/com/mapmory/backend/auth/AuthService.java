package com.mapmory.backend.auth;

import com.mapmory.backend.auth.dto.LoginResponse;
import com.mapmory.backend.auth.dto.TokenResponse;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.jwt.JwtProvider;
import com.mapmory.backend.auth.kakao.KakaoApiClient;
import com.mapmory.backend.auth.kakao.KakaoUserResponse;
import com.mapmory.backend.auth.refresh.RefreshTokenService;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_NAME_PREFIX = "회원";

    private final KakaoApiClient kakaoApiClient;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            KakaoApiClient kakaoApiClient,
            MemberRepository memberRepository,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService
    ) {
        this.kakaoApiClient = kakaoApiClient;
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse loginWithKakao(String kakaoAccessToken) {
        KakaoUserResponse kakaoUser = kakaoApiClient.fetchUser(kakaoAccessToken);
        String providerId = String.valueOf(kakaoUser.id());

        Optional<Member> existing =
                memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId);
        boolean isNewMember = existing.isEmpty();
        Member member = existing.orElseGet(() -> register(providerId, kakaoUser.nickname()));

        String accessToken = jwtProvider.issueAccessToken(member.getId());
        String refreshToken = refreshTokenService.issue(member);
        return new LoginResponse(accessToken, refreshToken, isNewMember);
    }

    // @Transactional을 두지 않는다. validateAndRevoke가 자체 트랜잭션에서 (재사용 시) 토큰 폐기를
    // 커밋한 뒤, 재사용이면 여기서 트랜잭션 밖에서 401을 던져 그 폐기가 롤백되지 않게 한다.
    public TokenResponse refresh(String refreshToken) {
        Member member = refreshTokenService.validateAndRevoke(refreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        String accessToken = jwtProvider.issueAccessToken(member.getId());
        String newRefreshToken = refreshTokenService.issue(member);
        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private Member register(String providerId, String nickname) {
        Member member = Member.ofOAuth(
                AuthProvider.KAKAO,
                providerId,
                resolveName(nickname),
                UUID.randomUUID()
        );
        return memberRepository.save(member);
    }

    private String resolveName(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            // 닉네임 미동의 시 구분 가능한 기본 이름을 부여한다. (예: 회원58213)
            return DEFAULT_NAME_PREFIX + ThreadLocalRandom.current().nextInt(10_000, 100_000);
        }
        return nickname;
    }
}
