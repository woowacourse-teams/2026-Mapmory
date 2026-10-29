package com.mapmory.backend.notification.event;

import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.MemberWithdrawn;
import com.mapmory.backend.notification.application.PushTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원이 탈퇴하거나 게스트가 버려지면 그 회원의 기기 토큰을 지운다(ADR 0021). 그 기기로 앨범 알림이 가지 않는다.
 *
 * <p>탈퇴·로그인이 커밋된 뒤에 받는다. 여기서 실패해도 탈퇴·로그인은 이미 끝났으므로 예외를 올리지 않고 남긴다.
 */
@Component
class InactiveMemberListener {

    private static final Logger log = LoggerFactory.getLogger(InactiveMemberListener.class);

    private final PushTokenService pushTokenService;

    InactiveMemberListener(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @TransactionalEventListener
    void onMemberWithdrawn(MemberWithdrawn event) {
        removeTokens(event.memberId());
    }

    @TransactionalEventListener
    void onGuestAbandoned(GuestAbandoned event) {
        removeTokens(event.guestMemberId());
    }

    private void removeTokens(Long memberId) {
        try {
            pushTokenService.removeAllOf(memberId);
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "PUSH_TOKEN_CLEANUP_FAILED")
                    .addKeyValue("memberId", memberId)
                    .setCause(exception)
                    .log("떠난 회원의 기기 토큰을 지우지 못했다");
        }
    }
}
