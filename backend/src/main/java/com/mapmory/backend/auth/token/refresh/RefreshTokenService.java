package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.token.jwt.JwtProperties;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh 토큰 발급·회전·폐기.
 *
 * 원문(raw)은 RefreshTokenGenerator로 받아 클라이언트에만 반환하고, 서버는 RefreshTokenHasher로 만든 해시만 저장한다.
 * 로그인 한 번에서 회전으로 이어진 토큰은 한 세션(RefreshTokenFamily)에 속한다. (#467)
 * 회전 시 기존 토큰을 폐기하고, 이미 회전된 토큰이 다시 제시되면(탈취 의심) 그 세션의
 * 유효한 토큰을 모두 폐기한다. 단, 게스트의 회전 응답 유실은 재발급한다. (ADR 0023)
 * 로그아웃도 세션 단위로 폐기한다.
 *
 * 잠그는 순서는 회원 → 세션 → refresh 토큰이다. 같은 세션의 회전·재사용 판정·로그아웃은 세션 행에서 직렬화된다. (ADR 0028)
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFamilyRepository familyRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenReuseMonitor reuseMonitor;
    private final Duration refreshTokenValidity;
    private final Duration guestRefreshTokenValidity;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenFamilyRepository familyRepository,
            MemberRepository memberRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenReuseMonitor reuseMonitor,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.reuseMonitor = reuseMonitor;
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
        this.guestRefreshTokenValidity = jwtProperties.guestRefreshTokenValidity();
        this.clock = clock;
    }

    /**
     * 로그인. 새 세션을 열고 첫 토큰을 발급한다.
     */
    @Transactional
    public String issue(Member member) {
        RefreshTokenFamily family = RefreshTokenFamily.open(member);
        familyRepository.save(family);
        String rawToken = refreshTokenGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(validityFor(member));
        refreshTokenRepository.save(RefreshToken.issue(family, member, refreshTokenHasher.hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 게스트는 다시 로그인할 수단이 없어 refresh가 만료되면 기록을 복구할 방법이 없다.
     * 회전 때마다 만료가 갱신되므로, 긴 유효기간은 "그 기간 동안 앱을 한 번도 열지 않은 경우"에만
     * 의미를 갖는다. (ADR 0015)
     */
    private Duration validityFor(Member member) {
        return member.isGuest()
                ? guestRefreshTokenValidity
                : refreshTokenValidity;
    }

    /**
     * 회전. 기존 토큰을 폐기하고 새 토큰을 발급해, 둘을 회전 체인(replacedById)으로 잇는다.
     *
     * 토큰 행에 비관적 락(FOR UPDATE)을 걸어 같은 토큰 동시 사용의 이중 회전을 막는다.
     * 그 전에 회원 행을 공유 잠금하고 세션 행을 잠가, 잠그는 순서를 회원 → 세션 → refresh로 맞춘다(lockOwnerOf).
     * 반환값으로 결과를 구분한다.
     *   - 유효: 새 토큰을 담아 반환(Optional.of)
     *   - 재사용(이미 회전된 토큰): 게스트의 응답 유실이면 재발급하고, 아니면 탈취로 간주해
     *     그 세션의 유효 토큰을 모두 폐기하고 Optional.empty 반환
     *     (폐기를 커밋해야 하므로 여기서 예외를 던지지 않는다. 401 변환은 호출부가 한다.)
     *   - 회전 외 이유(로그아웃·승격·버림·탈퇴)로 폐기된 토큰: 다른 토큰은 건드리지 않고 Optional.empty 반환.
     *     같은 토큰을 가진 사람이 둘이라는 신호가 아니다. 이미 쓸 수 없는 토큰이라 훔친 사람도 401로 끝나고,
     *     진짜 탈취는 누가 먼저 회전하든 회전된 토큰이 다시 오므로 위에서 잡힌다. (#579)
     *   - 활동 중이 아닌 회원: 토큰만 폐기하고 Optional.empty 반환
     *   - 없음/만료: 남길 부작용이 없으므로 예외로 던진다.
     */
    @Transactional
    public Optional<RotatedRefreshToken> rotate(String rawToken) {
        String tokenHash = refreshTokenHasher.hash(rawToken);
        lockOwnerOf(tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now(clock);
        if (refreshToken.isRotated()) {
            return handleReuse(refreshToken, now);
        }
        if (refreshToken.isRevoked()) {
            reuseMonitor.revokedPresented(refreshToken);
            return Optional.empty();
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
     * 토큰 주인의 회원 행을 공유 잠금하고, 토큰의 세션 행을 잠근다. refresh를 잠그기 전에 불러야 한다.
     *
     * 회원 먼저: 새 refresh를 INSERT하면 FK 확인으로 회원 행에 공유 잠금이 걸린다. refresh를 먼저 잠그면 순서가 refresh → 회원이
     * 되어, 회원 → refresh 순서로 잠그는 승격·탈퇴(회원 FOR UPDATE 뒤 refresh 일괄 폐기)와 데드락이 난다. (#579)
     * 공유 잠금이라 같은 회원의 회전끼리는 서로 막지 않는다.
     *
     * 그다음 세션: 재사용 판정과 로그아웃은 세션의 토큰을 일괄 폐기하는데, 일괄 UPDATE는 이미 폐기된 앞선 토큰까지 잠근다.
     * 세션 행을 먼저 잠그지 않으면 "제시 토큰을 쥐고 앞선 토큰을 기다리는" 쪽과 반대쪽이 엇갈려 데드락이 났다. (#467)
     * 세션 행에서 직렬화되므로 같은 세션의 토큰을 잠그는 순서가 문제 되지 않는다. 다른 세션끼리는 서로 막지 않는다.
     *
     * id를 읽는 조회는 잠그지 않으므로 REPEATABLE READ 스냅샷을 만들지만, 뒤의 조회는 모두 잠금 조회라 최신 커밋을 읽는다.
     * 그래서 아래의 활동 중 확인과 폐기 확인은 잠근 시점의 값으로 한다. 토큰이 없으면 empty다.
     */
    private Optional<RefreshTokenOwner> lockOwnerOf(String tokenHash) {
        Optional<RefreshTokenOwner> owner = refreshTokenRepository.findOwnerByTokenHash(tokenHash);
        owner.ifPresent(found -> {
            memberRepository.findByIdForShare(found.memberId());
            familyRepository.findByIdForUpdate(found.familyId());
        });
        return owner;
    }

    /**
     * 회전된 토큰이 다시 왔다. 탈취로 보고 그 세션의 토큰을 모두 폐기하되, 게스트의 회전 응답 유실이면 재발급한다. (ADR 0023)
     *
     * 세션만 폐기한다. 탈취자가 가진 것은 그 세션의 토큰뿐이므로, 같은 회원의 다른 기기 세션은 끊지 않는다. (#467)
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
        if (reused.getMember().isGuest() && unusedSuccessor.isPresent()) {
            RotatedRefreshToken reissued = replace(unusedSuccessor.get(), now);
            reused.followSuccessorOf(unusedSuccessor.get());
            reuseMonitor.reissued(reused);
            return Optional.of(reissued);
        }
        refreshTokenRepository.revokeAllActiveByFamilyId(reused.getFamily().getId(), now);
        reuseMonitor.revokedAll(reused, unusedSuccessor.isPresent());
        return Optional.empty();
    }

    private Optional<RefreshToken> findUnusedSuccessor(RefreshToken rotated, LocalDateTime now) {
        return refreshTokenRepository.findByIdForUpdate(rotated.getReplacedById())
                .filter(successor -> !successor.isRevoked() && !successor.isExpired(now));
    }

    // 같은 세션에 새 토큰을 발급하고, current를 폐기해 새 토큰을 후속으로 가리키게 한다.
    // 새 토큰을 먼저 저장해야(IDENTITY) id가 생겨 후속으로 기록할 수 있다.
    private RotatedRefreshToken replace(RefreshToken current, LocalDateTime now) {
        Member member = current.getMember();
        String rawToken = refreshTokenGenerator.generate();
        RefreshToken successor = RefreshToken.issue(
                current.getFamily(), member, refreshTokenHasher.hash(rawToken), now.plus(validityFor(member)));
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
        refreshTokenRepository.revokeAllActiveByMemberId(memberId, LocalDateTime.now(clock));
    }

    /**
     * 로그아웃. 제시된 토큰이 속한 세션의 유효한 토큰을 모두 폐기한다. (#467)
     *
     * 어느 시점의 토큰으로 로그아웃해도 그 세션에 쓸 수 있는 refresh가 남지 않는다. 체인을 따라가지 않고 일괄 UPDATE 한 번이라
     * 쿼리 수가 회전 횟수와 무관하다. 다른 기기의 세션은 남긴다.
     *
     * 회전과 같이 회원 → 세션 순서로 잠근다. 세션 행에서 회전·재사용 판정과 직렬화되어, 로그아웃이 회전보다 늦으면
     * 회전으로 나간 후속 토큰까지 폐기하고, 빠르면 회전이 폐기된 토큰을 보고 새 토큰을 주지 않는다. (#462)
     * 회원을 잠그는 것은 순서를 맞추기 위해서다. 승격·탈퇴가 회원을 쥔 채 refresh를 일괄 폐기하므로, 로그아웃도 회원부터 잡는다.
     * 없는 토큰이면 아무것도 하지 않는다.
     */
    @Transactional
    public void revoke(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        lockOwnerOf(refreshTokenHasher.hash(rawToken))
                .ifPresent(owner -> refreshTokenRepository.revokeAllActiveByFamilyId(owner.familyId(), now));
    }
}
