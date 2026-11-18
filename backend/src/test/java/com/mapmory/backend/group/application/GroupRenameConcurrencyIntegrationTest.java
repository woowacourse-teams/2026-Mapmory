package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 그룹 이름 바꾸기가 그사이 커밋된 수락·나가기를 옛 값으로 덮어쓰지 않는지 실제 DB로 확인한다.
 *
 * <p>이름을 바꾸면 Hibernate는 그룹 행의 모든 컬럼(이름, 방장, 인원 수)을 UPDATE한다. 그룹을 잠그지 않고 읽으면,
 * 읽은 뒤 커밋된 수락의 인원 수나 나가기의 방장 넘김을 처음 읽은 값으로 되돌린다.
 * 이름 바꾸기를 커밋하지 않은 트랜잭션 안에서 부르고, 그사이 다른 스레드에서 수락·나가기를 실행한 뒤 커밋해 순서를 고정한다.
 */
class GroupRenameConcurrencyIntegrationTest extends IntegrationTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupInvitationService groupInvitationService;

    @Autowired
    private GroupLeaveService groupLeaveService;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void 이름을_바꾸는_사이에_수락해도_인원_수가_실제_멤버_수와_같다() throws Exception {
        Member owner = socialMember();
        Long groupId = groupOf(owner);
        String token = groupInvitationService.create(owner.getId(), groupId).getToken();
        Member joiner = socialMember();

        Future<?> accept = whileRenaming(owner, groupId,
                () -> groupInvitationService.accept(joiner.getId(), token));
        accept.get(10, TimeUnit.SECONDS);

        assertThat(memberCountOf(groupId)).as("그룹 행의 인원 수").isEqualTo(membersOf(groupId)).isEqualTo(2);
        assertThat(nameOf(groupId)).isEqualTo("새 이름");
    }

    @Test
    void 이름을_바꾸는_사이에_방장이_나가도_나간_사람이_방장으로_돌아오지_않는다() throws Exception {
        Member owner = socialMember();
        Long groupId = groupOf(owner);
        String token = groupInvitationService.create(owner.getId(), groupId).getToken();
        Member member = socialMember();
        groupInvitationService.accept(member.getId(), token);

        Future<?> leave = whileRenaming(member, groupId, () -> {
            groupLeaveService.leave(owner.getId(), groupId);
            return null;
        });
        leave.get(10, TimeUnit.SECONDS);

        assertThat(ownerOf(groupId)).as("방장").isEqualTo(member.getId());
        assertThat(memberCountOf(groupId)).as("그룹 행의 인원 수").isEqualTo(membersOf(groupId)).isEqualTo(1);
    }

    /**
     * 이름 바꾸기를 커밋하지 않은 트랜잭션 안에서 부르고, 그사이 다른 스레드에서 other를 실행한 뒤 커밋한다.
     * 이름 바꾸기가 그룹을 잠그면 other는 커밋까지 기다리고, 잠그지 않으면 먼저 커밋된다. 어느 쪽이든 1초 기다린 뒤 커밋한다.
     */
    private Future<?> whileRenaming(Member renamer, Long groupId, Callable<?> other) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            groupService.rename(renamer.getId(), groupId, "새 이름");
            Future<?> future = executor.submit(other);
            waitBriefly(future);
            return future;
        });
    }

    private static void waitBriefly(Future<?> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException blocked) {
            // 이름 바꾸기의 잠금을 기다리는 중이다.
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int memberCountOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT member_count FROM travel_group WHERE id = ?", Integer.class, groupId);
    }

    private int membersOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ?", Integer.class, groupId);
    }

    private Long ownerOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT owner_member_id FROM travel_group WHERE id = ?", Long.class, groupId);
    }

    private String nameOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT name FROM travel_group WHERE id = ?", String.class, groupId);
    }

    private Long groupOf(Member member) {
        return travelGroupRepository.save(TravelGroup.create("제주 가족", member.getId(), LocalDateTime.now())).getId();
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
