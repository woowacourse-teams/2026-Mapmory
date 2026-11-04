package com.mapmory.backend.notification.application;

import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하거나 버려진 회원의 기기 토큰을 지운다. (#546, ADR 0021)
 *
 * <p>알림은 발송할 때 활동 중이 아닌 회원을 거르므로(AlbumPushDelivery) 토큰이 남아 있어도 가지 않는다. 그래서 탈퇴 이벤트로 바로
 * 지우지 않고 남은 토큰을 찾아 지운다. 토큰 표를 기준으로 찾으므로 실패한 차례, 이 코드 전에 탈퇴한 회원, 탈퇴와 겹쳐 늦게 커밋된 등록이
 * 다음 차례에 모두 지워진다. 활동 중이 아니게 된 회원은 되돌아오지 않으므로 여러 번, 여러 서버에서 돌아도 된다.
 */
@Component
public class InactivePushTokenCleanup {

    static final int BATCH_SIZE = 500;

    private final PushTokenRepository pushTokenRepository;
    private final PushTokenService pushTokenService;
    private final MemberQuery memberQuery;

    public InactivePushTokenCleanup(PushTokenRepository pushTokenRepository, PushTokenService pushTokenService,
                                    MemberQuery memberQuery) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushTokenService = pushTokenService;
        this.memberQuery = memberQuery;
    }

    /**
     * @return 지운 토큰 수
     */
    public int run() {
        int removed = 0;
        long after = 0;
        List<Long> memberIds;
        do {
            memberIds = pushTokenRepository.findMemberIdsAfter(after, PageRequest.of(0, BATCH_SIZE));
            if (memberIds.isEmpty()) {
                break;
            }
            Set<Long> inactive = memberQuery.inactiveAmong(memberIds);
            if (!inactive.isEmpty()) {
                removed += pushTokenService.removeAllOf(inactive);
            }
            after = memberIds.getLast();
        } while (memberIds.size() == BATCH_SIZE);
        return removed;
    }
}
