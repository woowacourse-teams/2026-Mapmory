package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.token.jwt.JwtProperties;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh 토큰 발급·회전·폐기.
 *
 * 원문(raw)은 RefreshTokenGenerator로 받아 클라이언트에만 반환하고, 서버는 RefreshTokenHasher로 만든 해시만 저장한다.
 * 회전 시 기존 토큰을 폐기하고, 이미 폐기된 토큰이 다시 제시되면(탈취 의심) 해당 회원의
 * 유효한 토큰을 모두 폐기한다. 단, 게스트의 회전 응답 유실은 재발급한다. (ADR 0023)
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenReuseMonitor reuseMonitor;
    private final Duration refreshTokenValidity;
    private final Duration guestRefreshTokenValidity;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenReuseMonitor reuseMonitor,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.reuseMonitor = reuseMonitor;
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
        this.guestRefreshTokenValidity = jwtProperties.guestRefreshTokenValidity();
    }

    @Transactional
    public String issue(Member member) {
        String rawToken = refreshTokenGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now().plus(validityFor(member));
        refreshTokenRepository.save(RefreshToken.issue(member, refreshTokenHasher.hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 게스트는 다시 로그인할 수단이 없어 refresh가 만료되면 기록을 복구할 방법이 없다.
     * 회전 때마다 만료가 갱신되므로, 긴 유효기간은 "그 기간 동안 앱을 한 번도 열지 않은 경우"에만
     * 의미를 갖는다. (ADR 0015)
     */
    private Duration validityFor(Member member) {
        return member.getProvider() == AuthProvider.GUEST
                ? guestRefreshTokenValidity
                : refreshTokenValidity;
    }

    /**
     * 회전. 기존 토큰을 폐기하고 새 토큰을 발급해, 둘을 회전 체인(replacedById)으로 잇는다.
     *
     * 토큰 행에 비관적 락(FOR UPDATE)을 걸어 같은 토큰 동시 사용의 이중 회전을 막는다.
     * 반환값으로 결과를 구분한다.
     *   - 유효: 새 토큰을 담아 반환(Optional.of)
     *   - 재사용(이미 폐기된 토큰): 게스트의 응답 유실이면 재발급하고, 아니면 탈취로 간주해
     *     회원의 유효 토큰을 모두 폐기하고 Optional.empty 반환
     *     (폐기를 커밋해야 하므로 여기서 예외를 던지지 않는다. 401 변환은 호출부가 한다.)
     *   - 활동 중이 아닌 회원: 토큰만 폐기하고 Optional.empty 반환
     *   - 없음/만료: 남길 부작용이 없으므로 예외로 던진다.
     */
    @Transactional
    public Optional<RotatedRefreshToken> rotate(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.hash(rawToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (refreshToken.isRevoked()) {
            return handleReuse(refreshToken, now);
        }
        if (refreshToken.isExpired(now)) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // 탈퇴하면 refresh를 모두 폐기하지만, 탈퇴와 재발급이 동시에 오면 폐기 전에 검증을 통과할 수 있다.
        // 그래서 회원이 활동 중인지 한 번 더 본다. 제시된 토큰만 폐기하고 새 토큰은 주지 않는다. (ADR 0021)
        if (!refreshToken.getMember().isActive()) {
            refreshToken.revoke(now);
            return Optional.empty();
        }
        return Optional.of(replace(refreshToken, now));
    }

    /**
     * 폐기된 토큰이 다시 왔다. 탈취로 보고 회원의 토큰을 모두 폐기하되, 게스트의 회전 응답 유실이면 재발급한다. (ADR 0023)
     *
     * 게스트는 다시 로그인할 수단이 없어, 탈취로 보고 전체 폐기하면 계정을 영구히 잃는다.
     * 응답 유실이라면 후속 토큰은 경과 시간과 무관하게 아직 쓰이지 않았다(쓰였다면 회전으로 폐기됐다).
     * 그래서 후속 토큰이 유효할 때만 재발급하고, 이전 토큰이 새 후속을 가리키게 해 유실이 반복돼도 이어지게 한다.
     * 직전보다 오래된 토큰은 후속이 이미 폐기됐으므로 여기서 걸러져 탈취로 처리된다.
     *
     * 소셜 회원은 다시 로그인하면 되므로 예외를 두지 않는다. 다만 후속 토큰이 쓰이지 않았는지는 로그에 남겨,
     * 응답 유실로 강제 로그아웃되는 빈도를 볼 수 있게 한다. (#446)
     */
    private Optional<RotatedRefreshToken> handleReuse(RefreshToken reused, LocalDateTime now) {
        Optional<RefreshToken> unusedSuccessor = findUnusedSuccessor(reused, now);
        if (reused.getMember().getProvider() == AuthProvider.GUEST && unusedSuccessor.isPresent()) {
            RotatedRefreshToken reissued = replace(unusedSuccessor.get(), now);
            reused.followSuccessorOf(unusedSuccessor.get());
            reuseMonitor.reissued(reused);
            return Optional.of(reissued);
        }
        refreshTokenRepository.revokeAllActiveByMember(reused.getMember(), now);
        reuseMonitor.revokedAll(reused, unusedSuccessor.isPresent());
        return Optional.empty();
    }

    private Optional<RefreshToken> findUnusedSuccessor(RefreshToken revoked, LocalDateTime now) {
        if (revoked.getReplacedById() == null) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByIdForUpdate(revoked.getReplacedById())
                .filter(successor -> !successor.isRevoked() && !successor.isExpired(now));
    }

    // 새 토큰을 발급하고, current를 폐기해 새 토큰을 후속으로 가리키게 한다.
    // 새 토큰을 먼저 저장해야(IDENTITY) id가 생겨 후속으로 기록할 수 있다.
    private RotatedRefreshToken replace(RefreshToken current, LocalDateTime now) {
        Member member = current.getMember();
        String rawToken = refreshTokenGenerator.generate();
        RefreshToken successor = RefreshToken.issue(
                member, refreshTokenHasher.hash(rawToken), now.plus(validityFor(member)));
        refreshTokenRepository.save(successor);
        current.replaceWith(successor, now);
        return new RotatedRefreshToken(member.getId(), rawToken);
    }

    /**
     * 회원의 유효한 refresh를 모두 폐기한다.
     *
     * 이미 발급된 access는 무상태라 만료까지 남지만, 갱신 경로가 끊겨 세션이 이어지지 않는다.
     */
    @Transactional
    public void revokeAll(Long memberId) {
        refreshTokenRepository.revokeAllActiveByMemberId(memberId, LocalDateTime.now());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(rawToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }
}
