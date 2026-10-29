package com.mapmory.backend.group.event;

import com.mapmory.backend.group.application.GroupLeaveService;
import com.mapmory.backend.member.event.MemberWithdrawn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원이 탈퇴하면 속한 모든 그룹에서 나간다(#547). 다른 멤버에게 "탈퇴한 회원"과 그 기록이 남아 보이지 않게 한다.
 * 개인 기록은 30일 뒤 파기하지만(#435) 그룹은 다른 사람에게 보이는 데이터라 바로 뺀다.
 *
 * <p>탈퇴가 커밋된 뒤에 받는다. 그룹마다 따로 나가고, 한 그룹이 실패해도 나머지는 계속한다.
 * 탈퇴는 이미 끝났으므로 예외를 올리지 않고 남긴다.
 */
@Component
class MemberWithdrawnListener {

    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawnListener.class);

    private final GroupLeaveService groupLeaveService;

    MemberWithdrawnListener(GroupLeaveService groupLeaveService) {
        this.groupLeaveService = groupLeaveService;
    }

    @TransactionalEventListener
    void onMemberWithdrawn(MemberWithdrawn event) {
        Long memberId = event.memberId();
        try {
            groupLeaveService.groupIdsOf(memberId).forEach(groupId -> leave(memberId, groupId));
        } catch (RuntimeException exception) {
            failed(memberId, null, exception);
        }
    }

    private void leave(Long memberId, Long groupId) {
        try {
            groupLeaveService.leaveAsWithdrawn(memberId, groupId);
        } catch (RuntimeException exception) {
            failed(memberId, groupId, exception);
        }
    }

    private void failed(Long memberId, Long groupId, RuntimeException exception) {
        log.atWarn()
                .addKeyValue("event", "GROUP_LEAVE_ON_WITHDRAWAL_FAILED")
                .addKeyValue("memberId", memberId)
                .addKeyValue("groupId", groupId)
                .setCause(exception)
                .log("탈퇴한 회원을 그룹에서 빼지 못했다");
    }
}
