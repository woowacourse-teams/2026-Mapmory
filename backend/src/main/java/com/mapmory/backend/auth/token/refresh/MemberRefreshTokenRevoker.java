package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.GuestPromoted;
import com.mapmory.backend.member.event.MemberWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원의 기존 세션을 끝내야 하면 그 회원의 refresh 토큰을 모두 폐기한다.
 *
 *   - 탈퇴, 버려진 게스트: 회원이 활동 중이 아니게 됐다. (ADR 0021)
 *   - 게스트 승격: 게스트일 때의 세션이 소셜 로그인으로 대체됐다. 게스트 refresh는 365일이라, 남겨 두면
 *     게스트일 때 새어 나간 토큰으로 소셜 회원 계정을 쓸 수 있다. (#460)
 *
 * member가 RefreshTokenService를 직접 부르면 member → auth 의존이 생기므로 이벤트로 받는다.
 * 탈퇴·로그인과 같은 트랜잭션에서 실행돼야 한다(폐기가 실패하면 탈퇴·로그인도 롤백). 그래서 @TransactionalEventListener가 아닌
 * 동기 @EventListener다.
 */
@Component
class MemberRefreshTokenRevoker {

    private final RefreshTokenService refreshTokenService;

    MemberRefreshTokenRevoker(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @EventListener
    void revokeAll(MemberWithdrawn event) {
        refreshTokenService.revokeAll(event.memberId());
    }

    @EventListener
    void revokeAll(GuestAbandoned event) {
        refreshTokenService.revokeAll(event.guestMemberId());
    }

    @EventListener
    void revokeAll(GuestPromoted event) {
        refreshTokenService.revokeAll(event.memberId());
    }
}
