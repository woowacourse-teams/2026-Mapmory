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
 * <p>초대 수락은 회원 → 그룹 순서로 잠근다. 앨범 열기·담기는 그룹을 잠근 뒤 회원을 가리키는 행(album.opener_member_id,
 * album_entry.member_id)을 넣는데, 이때 FK 확인이 회원 행을 공유 잠금한다. 앨범 쪽이 그룹 → 회원 순서면 같은 회원이 같은 그룹에
 * 수락(이미 멤버라 그대로 돌려줌)과 앨범 요청을 동시에 보낼 때 서로를 기다린다. 나가기는 그룹만 잠그고 회원 행은 잠그지 않아
 * 수락과 겹쳐도 한쪽이 기다리기만 한다. 한 번으로는 겹치지 않을 수 있어 여러 번 동시에 출발시킨다.
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
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return outcomes;
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
