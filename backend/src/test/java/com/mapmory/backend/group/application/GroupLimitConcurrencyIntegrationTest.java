package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.GroupInvitation;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 회원당 그룹 상한(10개)이 동시에 만들거나 수락할 때도 지켜지는지 실제 DB로 확인한다.
 *
 * <p>만들기와 수락은 회원이 속한 그룹 수를 센 뒤 넣는다. 두 요청이 모두 9개를 보고 넣으면 11개가 된다(읽은 다음 쓰기).
 * 수락은 그 그룹 행을 잠그지만, 서로 다른 그룹에 수락하면 잠금이 겹치지 않는다.
 * 한 번으로는 경쟁이 겹치지 않을 수 있어 회원을 바꿔 여러 번 동시에 출발시킨다. 매번 정확히 10개에서 멈추고 한 요청만 거절돼야 한다.
 */
class GroupLimitConcurrencyIntegrationTest extends IntegrationTest {

    private static final int ROUNDS = 20;
    private static final int LIMIT = 10;
    private static final String OK = "OK";
    private static final String TOO_MANY_GROUPS = GroupErrorCode.TOO_MANY_GROUPS.toString();

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupInvitationService groupInvitationService;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 그룹이_아홉_개일_때_동시에_두_번_만들어도_열_개를_넘지_않는다() throws Exception {
        List<String> violations = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Long memberId = memberInGroups(LIMIT - 1);

            List<String> outcomes = runAtOnce(List.of(
                    () -> groupService.create(memberId, "동시 그룹 A"),
                    () -> groupService.create(memberId, "동시 그룹 B")));

            recordIfNotStoppedAtLimit(violations, round, memberId, outcomes);
        }

        assertThat(violations)
                .as("%d번 중 10개에서 멈추지 않은 경우", ROUNDS)
                .isEmpty();
    }

    @Test
    void 그룹이_아홉_개일_때_서로_다른_그룹에_동시에_수락해도_열_개를_넘지_않는다() throws Exception {
        List<String> violations = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Long memberId = memberInGroups(LIMIT - 1);
            String first = invitationToNewGroup();
            String second = invitationToNewGroup();

            List<String> outcomes = runAtOnce(List.of(
                    () -> groupInvitationService.accept(memberId, first),
                    () -> groupInvitationService.accept(memberId, second)));

            recordIfNotStoppedAtLimit(violations, round, memberId, outcomes);
        }

        assertThat(violations)
                .as("%d번 중 10개에서 멈추지 않은 경우", ROUNDS)
                .isEmpty();
    }

    private void recordIfNotStoppedAtLimit(List<String> violations, int round, Long memberId, List<String> outcomes) {
        int count = groupCount(memberId);
        long rejected = outcomes.stream().filter(TOO_MANY_GROUPS::equals).count();
        if (count != LIMIT || rejected != 1) {
            violations.add("round " + round + ": " + count + "개 " + outcomes);
        }
        // 데드락이나 커넥션 대기 같은 다른 실패가 섞이지 않았는지 본다. 개수 초과 여부는 모아서 판단한다.
        assertThat(outcomes).isSubsetOf(OK, TOO_MANY_GROUPS);
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 실행하고, 각 결과를 OK 또는 에러 코드로 돌려준다.
    private List<String> runAtOnce(List<Callable<?>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (Callable<?> action : actions) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    action.call();
                    return OK;
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

    // 그룹을 만든 사람은 그 그룹의 멤버다.
    private Long memberInGroups(int groupCount) {
        Long memberId = socialMember().getId();
        for (int i = 0; i < groupCount; i++) {
            travelGroupRepository.save(TravelGroup.create("기존 그룹 " + i, memberId, LocalDateTime.now()));
        }
        return memberId;
    }

    private String invitationToNewGroup() {
        Long ownerId = socialMember().getId();
        TravelGroup group = travelGroupRepository.save(TravelGroup.create("초대한 그룹", ownerId, LocalDateTime.now()));
        GroupInvitation invitation = groupInvitationService.create(ownerId, group.getId());
        return invitation.getToken();
    }

    private int groupCount(Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_member WHERE member_id = ?", Integer.class, memberId);
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
