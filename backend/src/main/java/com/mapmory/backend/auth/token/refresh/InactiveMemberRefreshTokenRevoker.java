package com.mapmory.backend.auth.token.refresh;

import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.MemberWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원이 활동 중이 아니게 되면(탈퇴, 버려진 게스트) 그 회원의 refresh 토큰을 모두 폐기한다. (ADR 0021)
 *
 * member가 RefreshTokenService를 직접 부르면 member → auth 의존이 생기므로 이벤트로 받는다.
 * 탈퇴·로그인과 같은 트랜잭션에서 실행돼야 한다(폐기가 실패하면 탈퇴·로그인도 롤백). 그래서 @TransactionalEventListener가 아닌
 * 동기 @EventListener다.
 */
@Component
class InactiveMemberRefreshTokenRevoker {

    private final RefreshTokenService refreshTokenService;

    InactiveMemberRefreshTokenRevoker(RefreshTokenService refreshTokenService) {
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
}
