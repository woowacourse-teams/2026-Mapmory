package com.mapmory.backend.group.application;

import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.api.MemberQuery;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하거나 버려진 회원을 그룹에서 뺀다. (#547, #555, ADR 0021)
 *
 * <p>그룹은 다른 멤버에게 보이므로 탈퇴가 커밋되면 바로 나간다({@link #leaveAllGroups}). 그 이벤트는 한 번뿐이라 실패하면 다시 오지 않으므로,
 * 하루 한 번 그룹 멤버 표에서 활동 중이 아닌 회원을 찾아 같은 경로로 뺀다({@link #run}). 실패한 그룹, 이 코드 전에 탈퇴한 회원,
 * 탈퇴와 겹쳐 늦게 커밋된 초대 수락이 모두 여기서 정리된다. 나가기는 멱등이라 둘이 겹쳐도 된다.
 */
@Component
public class InactiveMemberGroupCleanup {

    static final int BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(InactiveMemberGroupCleanup.class);

    private final GroupLeaveService groupLeaveService;
    private final TravelGroupRepository travelGroupRepository;
    private final MemberQuery memberQuery;

    public InactiveMemberGroupCleanup(GroupLeaveService groupLeaveService,
                                      TravelGroupRepository travelGroupRepository,
                                      MemberQuery memberQuery) {
        this.groupLeaveService = groupLeaveService;
        this.travelGroupRepository = travelGroupRepository;
        this.memberQuery = memberQuery;
    }

    /**
     * 회원이 속한 모든 그룹에서 나간다. 한 그룹이 실패해도 나머지는 처리하고, 실패한 그룹은 배치가 다시 처리한다.
     */
    public void leaveAllGroups(Long memberId) {
        for (Long groupId : groupLeaveService.groupIdsOf(memberId)) {
            try {
                groupLeaveService.leaveAsInactive(memberId, groupId);
            } catch (RuntimeException exception) {
                log.atWarn()
                        .addKeyValue("event", "GROUP_LEAVE_ON_WITHDRAWAL_FAILED")
                        .addKeyValue("memberId", memberId)
                        .addKeyValue("groupId", groupId)
                        .setCause(exception)
                        .log("활동 중이 아닌 회원을 그룹에서 빼지 못했다");
            }
        }
    }

    /**
     * @return 그룹에서 뺀 회원 수
     */
    public int run() {
        int handled = 0;
        long after = 0;
        List<Long> memberIds;
        do {
            memberIds = travelGroupRepository.findMemberIdsAfter(after, PageRequest.of(0, BATCH_SIZE));
            if (memberIds.isEmpty()) {
                break;
            }
            Set<Long> inactive = memberQuery.inactiveAmong(memberIds);
            inactive.forEach(this::leaveAllGroups);
            handled += inactive.size();
            after = memberIds.getLast();
        } while (memberIds.size() == BATCH_SIZE);
        return handled;
    }
}
