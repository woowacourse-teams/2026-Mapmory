package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.AuthTokens;
import com.mapmory.backend.auth.application.model.LoginResult;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
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
    // member.name 컬럼 길이(VARCHAR(50))와 같다.
    private static final int MAX_NAME_LENGTH = 50;

    private final SocialIdentityPorts socialIdentityPorts;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            SocialIdentityPorts socialIdentityPorts,
            MemberRepository memberRepository,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService
    ) {
        this.socialIdentityPorts = socialIdentityPorts;
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 소셜 로그인. 제공자에 맞는 포트로 토큰을 확인한 뒤 가입·승격·토큰 발급은 공통으로 처리한다.
     *
     * 게스트로 사용 중이었다면(authenticatedMemberId가 게스트 회원) 새 회원을 만들지 않고
     * 그 회원을 승격해, 게스트로 남긴 기록이 그대로 이어지게 한다.
     *
     * 이미 같은 소셜 계정으로 가입한 회원이 있으면 한 사람에게 회원 행이 둘인 상황이므로,
     * 기존 회원을 우선하고 게스트 세션은 끊는다. 이때 게스트가 남긴 기록은 이어지지 않는다.
     * (ADR 0015)
     */
    @Transactional
    public LoginResult loginWithSocial(AuthProvider provider, String token, Long authenticatedMemberId) {
        SocialIdentity identity = socialIdentityPorts.verify(provider, token);
        String providerId = identity.providerId();
        Optional<Member> guest = findGuest(authenticatedMemberId);

        Optional<Member> registered = memberRepository.findByProviderAndProviderId(provider, providerId);
        if (registered.isPresent()) {
            guest.ifPresent(refreshTokenService::revokeAll);
            return issueTokens(registered.get(), false);
        }

        if (guest.isPresent()) {
            Member promoted = guest.get();
            promoted.promote(provider, providerId, resolveName(identity.name()));
            // 게스트로 이미 앱을 사용했으므로 온보딩을 반복하지 않도록 신규로 보지 않는다.
            return issueTokens(promoted, false);
        }

        return issueTokens(register(provider, providerId, identity.name()), true);
    }

    private Optional<Member> findGuest(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId)
                .filter(member -> member.getProvider() == AuthProvider.GUEST);
    }

    /**
     * 로그인 없이 서비스를 사용하기 위한 게스트 회원을 만든다.
     *
     * 소셜 로그인과 달리 외부에 물어볼 신원이 없으므로 식별자를 서버가 발급한다.
     * 발급 이후의 토큰 체계는 소셜 로그인과 완전히 동일하다. (ADR 0015)
     */
    @Transactional
    public LoginResult loginAsGuest() {
        Member member = memberRepository.save(Member.ofGuest(
                UUID.randomUUID().toString(),
                resolveName(null),
                UUID.randomUUID()
        ));
        return issueTokens(member, true);
    }

    // @Transactional을 두지 않는다. validateAndRevoke가 자체 트랜잭션에서 (재사용 시) 토큰 폐기를
    // 커밋한 뒤, 재사용이면 여기서 트랜잭션 밖에서 401을 던져 그 폐기가 롤백되지 않게 한다.
    public AuthTokens refresh(String refreshToken) {
        Member member = refreshTokenService.validateAndRevoke(refreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        String accessToken = jwtProvider.issueAccessToken(member.getId());
        String newRefreshToken = refreshTokenService.issue(member);
        return new AuthTokens(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResult issueTokens(Member member, boolean isNewMember) {
        String accessToken = jwtProvider.issueAccessToken(member.getId());
        String refreshToken = refreshTokenService.issue(member);
        return new LoginResult(new AuthTokens(accessToken, refreshToken), isNewMember);
    }

    private Member register(AuthProvider provider, String providerId, String nickname) {
        Member member = Member.ofOAuth(
                provider,
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
        return truncate(nickname);
    }

    // 구글 표시 이름은 길이 제한이 없어 컬럼(50자)을 넘을 수 있다. 서로게이트 쌍이 잘리지 않게 코드 포인트 단위로 자른다.
    private String truncate(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }
}
