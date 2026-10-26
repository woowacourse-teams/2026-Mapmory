package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.GroupInvitation;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 초대 수락이 동시에 들어와도 30명을 넘지 않는지 실제 DB로 확인한다.
 */
class GroupInvitationConcurrencyTest extends IntegrationTest {

    @Autowired
    private GroupInvitationService groupInvitationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 동시에_수락해도_서른_명을_넘지_않는다() throws Exception {
        Member owner = socialMember();
        TravelGroup group = travelGroupRepository.save(TravelGroup.create("제주 가족", owner.getId(), LocalDateTime.now()));
        for (int i = 0; i < 27; i++) {
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                    group.getId(), socialMember().getId(), LocalDateTime.now());
        }
        jdbcTemplate.update("UPDATE travel_group SET member_count = 28 WHERE id = ?", group.getId());
        GroupInvitation invitation = groupInvitationService.create(owner.getId(), group.getId());
        List<Long> joiners = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            joiners.add(socialMember().getId());
        }

        List<String> outcomes = acceptAtOnce(joiners, invitation.getToken());

        assertThat(memberCount(group)).isEqualTo(30);
        assertThat(outcomes).containsOnly("OK", "GROUP_FULL");
        assertThat(outcomes).filteredOn("OK"::equals).hasSize(2);
    }

    @Test
    void 같은_사람이_동시에_여러_번_수락해도_한_번만_들어간다() throws Exception {
        Member owner = socialMember();
        TravelGroup group = travelGroupRepository.save(TravelGroup.create("제주 가족", owner.getId(), LocalDateTime.now()));
        GroupInvitation invitation = groupInvitationService.create(owner.getId(), group.getId());
        Long joiner = socialMember().getId();

        List<String> outcomes = acceptAtOnce(List.of(joiner, joiner, joiner, joiner, joiner), invitation.getToken());

        assertThat(outcomes).containsOnly("OK");
        assertThat(memberCount(group)).isEqualTo(2);
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 수락하고, 각 결과를 OK 또는 에러 코드로 돌려준다.
    private List<String> acceptAtOnce(List<Long> joiners, String token) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(joiners.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (Long joiner : joiners) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    groupInvitationService.accept(joiner, token);
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.getErrorCode().toString();
                } catch (RuntimeException exception) {
                    return exception.getClass().getSimpleName();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return outcomes;
    }

    private int memberCount(TravelGroup group) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_member WHERE group_id = ?", Integer.class, group.getId());
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
