package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.time.LocalDate;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 회원 행과 그룹 행을 함께 잠그는 요청이 겹쳐도 데드락이 나지 않는지 실제 DB로 확인한다.
 *
 * <p>그룹 흐름은 그룹 행을 잠근 뒤 회원을 가리키는 값을 쓰고, 이때 FK 확인이 회원 행을 공유 잠금한다. 수락은 멤버를,
 * 앨범 열기·담기는 album.opener_member_id·album_entry.member_id를 넣는다. 나가기는 방장(travel_group.owner_member_id)이나
 * 앨범을 연 사람(album.opener_member_id)을 다음 멤버로 바꾸는데, FK 값을 바꾸는 UPDATE도 바뀐 회원 행을 공유 잠금한다.
 * 모두 그룹 → 회원 순서라 겹쳐도 한쪽이 기다리기만 한다. 어느 흐름이든 회원을 먼저 잠그면 순서가 엇갈려 데드락이 난다(ADR 0028).
 * 한 번으로는 겹치지 않을 수 있어 여러 번 동시에 출발시킨다.
 */
class GroupLockOrderConcurrencyIntegrationTest extends IntegrationTest {

    private static final int ROUNDS = 20;
    private static final String OK = "OK";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Autowired
    private GroupInvitationService groupInvitationService;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private GroupLeaveService groupLeaveService;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Region region;

    @BeforeEach
    void setUp() {
        Long regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM region WHERE region_type = 'PROVINCE' ORDER BY id LIMIT 1", Long.class);
        region = regionRepository.findById(regionId).orElseThrow();
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void 같은_그룹에_수락하면서_앨범을_열어도_데드락이_나지_않는다() throws Exception {
        List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            Long groupId = groupOf(member);
            String token = groupInvitationService.create(member.getId(), groupId).getToken();
            Long recordId = record(member);

            List<String> outcomes = runAtOnce(List.of(
                    () -> groupInvitationService.accept(member.getId(), token),
                    () -> albumService.open(member.getId(), groupId, recordId, null, null)));

            recordIfFailed(failures, round, outcomes);
        }

        assertThat(failures).as("%d번 중 실패한 경우", ROUNDS).isEmpty();
    }

    @Test
    void 같은_그룹에_수락하면서_앨범에_담아도_데드락이_나지_않는다() throws Exception {
        List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            Long groupId = groupOf(member);
            String token = groupInvitationService.create(member.getId(), groupId).getToken();
            Long albumId = albumService.open(member.getId(), groupId, record(member), null, null).id();
            Long recordId = record(member);

            List<String> outcomes = runAtOnce(List.of(
                    () -> groupInvitationService.accept(member.getId(), token),
                    () -> albumService.addEntry(member.getId(), groupId, albumId, recordId)));

            recordIfFailed(failures, round, outcomes);
        }

        assertThat(failures).as("%d번 중 실패한 경우", ROUNDS).isEmpty();
    }

    @Test
    void 같은_그룹에_수락하면서_나가도_데드락이_나지_않는다() throws Exception {
        List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member owner = socialMember();
            Long groupId = groupOf(owner);
            String token = groupInvitationService.create(owner.getId(), groupId).getToken();
            Member member = socialMember();
            groupInvitationService.accept(member.getId(), token);

            List<String> outcomes = runAtOnce(List.of(
                    () -> groupInvitationService.accept(member.getId(), token),
                    () -> {
                        groupLeaveService.leave(member.getId(), groupId);
                        return null;
                    }));

            recordIfFailed(failures, round, outcomes);
        }

        assertThat(failures).as("%d번 중 실패한 경우", ROUNDS).isEmpty();
    }

    @Test
    void 방장이_나가면서_다음_방장이_앨범에_담아도_데드락이_나지_않는다() throws Exception {
        List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member owner = socialMember();
            Long groupId = groupOf(owner);
            String token = groupInvitationService.create(owner.getId(), groupId).getToken();
            Member next = socialMember();
            groupInvitationService.accept(next.getId(), token);
            Long albumId = albumService.open(next.getId(), groupId, record(next), null, null).id();
            Long recordId = record(next);

            List<String> outcomes = runAtOnce(List.of(
                    () -> {
                        groupLeaveService.leave(owner.getId(), groupId);
                        return null;
                    },
                    () -> albumService.addEntry(next.getId(), groupId, albumId, recordId)));

            recordIfFailed(failures, round, outcomes);
        }

        assertThat(failures).as("%d번 중 실패한 경우", ROUNDS).isEmpty();
    }

    @Test
    void 앨범을_연_멤버가_나가면서_다음에_연_사람이_될_멤버가_앨범에_담아도_데드락이_나지_않는다() throws Exception {
        List<String> failures = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member owner = socialMember();
            Long groupId = groupOf(owner);
            String token = groupInvitationService.create(owner.getId(), groupId).getToken();
            Member opener = socialMember();
            Member next = socialMember();
            groupInvitationService.accept(opener.getId(), token);
            groupInvitationService.accept(next.getId(), token);
            Long albumId = albumService.open(opener.getId(), groupId, record(opener), null, null).id();
            albumService.addEntry(next.getId(), groupId, albumId, record(next));
            Long recordId = record(next);

            List<String> outcomes = runAtOnce(List.of(
                    () -> {
                        groupLeaveService.leave(opener.getId(), groupId);
                        return null;
                    },
                    () -> albumService.addEntry(next.getId(), groupId, albumId, recordId)));

            recordIfFailed(failures, round, outcomes);
        }

        assertThat(failures).as("%d번 중 실패한 경우", ROUNDS).isEmpty();
    }

    private void recordIfFailed(List<String> failures, int round, List<String> outcomes) {
        if (!outcomes.stream().allMatch(OK::equals)) {
            failures.add("round " + round + ": " + outcomes);
        }
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 실행하고, 각 결과를 OK, 에러 코드 또는 예외 이름으로 돌려준다.
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
        try {
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            // 시간 초과나 데드락으로 중간에 빠져나가도 남은 스레드가 커넥션을 쥔 채 다음 라운드로 넘어가지 않게 한다.
            results.forEach(result -> result.cancel(true));
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Long groupOf(Member member) {
        return travelGroupRepository.save(TravelGroup.create("제주 가족", member.getId(), LocalDateTime.now())).getId();
    }

    private Long record(Member member) {
        TravelRecord record = TravelRecord.of(member, region, "도쿄", "본문", LocalDate.of(2026, 3, 1), null, TODAY);
        record.synchronizeMedia(ObjectKeys.from(
                List.of("travel-records/" + member.getId() + "/" + UUID.randomUUID() + "-a.jpg")));
        return travelRecordRepository.save(record).getId();
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
