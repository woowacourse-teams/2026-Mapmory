package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.AuthTokens;
import com.mapmory.backend.auth.application.model.LoginResult;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
import com.mapmory.backend.auth.token.refresh.RotatedRefreshToken;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.MemberJoinService;
import com.mapmory.backend.member.application.SocialJoin;
import com.mapmory.backend.member.application.SocialJoinType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    private final SocialIdentityPorts socialIdentityPorts;
    private final MemberJoinService memberJoinService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            SocialIdentityPorts socialIdentityPorts,
            MemberJoinService memberJoinService,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager
    ) {
        this.socialIdentityPorts = socialIdentityPorts;
        this.memberJoinService = memberJoinService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 소셜 로그인. 제공자에 맞는 포트로 토큰을 확인하고, 어떤 회원으로 들어올지는 MemberJoinService가 정한다.
     *
     * 게스트로 사용 중이었다면(authenticatedMemberId가 게스트 회원) 그 회원을 승격한다.
     * 이미 같은 소셜 계정으로 가입한 회원이 있으면 기존 회원을 우선하고, 두고 떠난 게스트의 세션은 끊는다.
     * 이때 응답의 guestAbandoned로 게스트 기록이 이어지지 않았음을 알린다. (ADR 0021)
     * 게스트로 이미 앱을 사용했으므로 승격·기존 계정은 온보딩을 반복하지 않도록 신규로 보지 않는다.
     * (ADR 0015)
     *
     * 토큰 검증(공개키 조회)은 트랜잭션 밖에서 한다. 안에서 하면 외부를 기다리는 동안 커넥션을 쥐고 있게 된다.
     *
     * 같은 소셜 계정의 첫 로그인이 동시에 오면(버튼 연타) 둘 다 회원이 없다고 보고 가입하려 해, 늦은 쪽이
     * (provider, provider_id) UNIQUE에 걸린다. 위반한 트랜잭션은 되돌릴 수 없으므로 새 트랜잭션에서 한 번 더 들어와,
     * 먼저 가입된 회원으로 로그인시킨다. 다시 실패하면 같은 계정 충돌이 아니므로 그대로 던진다. (#461)
     */
    public LoginResult loginWithSocial(AuthProvider provider, String token, Long authenticatedMemberId) {
        SocialIdentity identity = socialIdentityPorts.verify(provider, token);
        try {
            return transactionTemplate.execute(status -> joinWithSocial(provider, identity, authenticatedMemberId));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> joinWithSocial(provider, identity, authenticatedMemberId));
        }
    }

    private LoginResult joinWithSocial(AuthProvider provider, SocialIdentity identity, Long authenticatedMemberId) {
        SocialJoin join = memberJoinService.joinWithSocial(
                provider, identity.providerId(), identity.name(), authenticatedMemberId);
        return issueTokens(join.member(), join.type() == SocialJoinType.SOCIAL_JOINED, join.guestAbandoned());
    }

    /**
     * 로그인 없이 서비스를 사용하기 위한 게스트로 로그인한다.
     *
     * 발급 이후의 토큰 체계는 소셜 로그인과 완전히 동일하다. (ADR 0015)
     */
    @Transactional
    public LoginResult loginAsGuest() {
        return issueTokens(memberJoinService.joinAsGuest(), true, false);
    }

    // @Transactional을 두지 않는다. rotate가 자체 트랜잭션에서 (재사용 시) 토큰 폐기를
    // 커밋한 뒤, 재사용이면 여기서 트랜잭션 밖에서 401을 던져 그 폐기가 롤백되지 않게 한다.
    public AuthTokens refresh(String refreshToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        String accessToken = jwtProvider.issueAccessToken(rotated.memberId());
        return new AuthTokens(accessToken, rotated.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResult issueTokens(Member member, boolean isNewMember, boolean guestAbandoned) {
        String accessToken = jwtProvider.issueAccessToken(member.getId());
        String refreshToken = refreshTokenService.issue(member);
        return new LoginResult(new AuthTokens(accessToken, refreshToken), isNewMember, guestAbandoned);
    }
}
