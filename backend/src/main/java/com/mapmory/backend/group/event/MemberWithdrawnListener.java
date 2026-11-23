package com.mapmory.backend.group.event;

import com.mapmory.backend.group.application.InactiveMemberGroupCleanup;
import com.mapmory.backend.member.event.MemberWithdrawn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원이 탈퇴하면 모든 그룹에서 뺀다. 탈퇴한 회원은 남은 access token으로도 그룹 API를 쓸 수 없어 스스로 나가지 못한다. (#547)
 *
 * <p>탈퇴가 커밋된 뒤에 받는다. 여기서 실패해도 탈퇴는 이미 끝났으므로 예외를 올리지 않고 남긴다. 빼지 못한 회원은 배치가 다시 찾는다.
 * 게스트는 그룹에 들어갈 수 없으므로 버려진 게스트는 받지 않는다.
 */
@Component
class MemberWithdrawnListener {

    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawnListener.class);

    private final InactiveMemberGroupCleanup cleanup;

    MemberWithdrawnListener(InactiveMemberGroupCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @TransactionalEventListener
    void onMemberWithdrawn(MemberWithdrawn event) {
        try {
            cleanup.leaveAllGroups(event.memberId());
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "GROUP_LEAVE_ON_WITHDRAWAL_FAILED")
                    .addKeyValue("memberId", event.memberId())
                    .setCause(exception)
                    .log("탈퇴한 회원의 그룹을 읽지 못했다");
        }
    }
}
