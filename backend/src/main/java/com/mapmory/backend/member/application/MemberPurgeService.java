package com.mapmory.backend.member.application;

import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.api.MemberDataPurger;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 탈퇴하거나 버려지고 30일이 지난 회원의 데이터를 파기한다. 회원 행은 남기고 purged_at을 채운다. (#435, ADR 0021)
 *
 * <p>회원마다 따로 처리한다. 한 회원이 실패해도 다음 회원을 처리하고, purged_at이 비어 있으므로 다음 날 다시 처리한다.
 * 30일은 access token 유효시간(30분)보다 길어, 탈퇴 뒤 남은 토큰으로 만든 데이터까지 파기 대상에 든다(ADR 0021).
 */
@Service
public class MemberPurgeService {

    static final Duration RETENTION = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(MemberPurgeService.class);
    private static final int BATCH_SIZE = 100;

    private final MemberRepository memberRepository;
    private final List<MemberDataPurger> purgers;
    private final Clock clock;

    public MemberPurgeService(MemberRepository memberRepository, List<MemberDataPurger> purgers, Clock clock) {
        this.memberRepository = memberRepository;
        this.purgers = purgers;
        this.clock = clock;
    }

    /**
     * @return 파기를 끝낸 회원 수
     */
    public int run() {
        LocalDateTime before = LocalDateTime.now(clock).minus(RETENTION);
        int purged = 0;
        long after = 0;
        List<Long> memberIds;
        do {
            memberIds = memberRepository.findIdsToPurge(before, after, PageRequest.of(0, BATCH_SIZE));
            for (Long memberId : memberIds) {
                if (purge(memberId)) {
                    purged++;
                }
                after = memberId;
            }
        } while (memberIds.size() == BATCH_SIZE);
        return purged;
    }

    private boolean purge(Long memberId) {
        try {
            purgers.forEach(purger -> purger.purge(memberId));
            memberRepository.markPurged(memberId, LocalDateTime.now(clock));
            return true;
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "MEMBER_PURGE_FAILED")
                    .addKeyValue("memberId", memberId)
                    .setCause(exception)
                    .log("회원 데이터를 파기하지 못했다. 다음 날 다시 처리한다");
            return false;
        }
    }
}
