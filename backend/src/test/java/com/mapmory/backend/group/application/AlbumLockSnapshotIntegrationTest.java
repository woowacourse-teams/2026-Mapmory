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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 앨범 흐름이 그룹 잠금을 기다리는 사이 커밋된 변경을 잠금 뒤에 보는지 실제 DB로 확인한다.
 *
 * <p>REPEATABLE READ의 스냅샷은 트랜잭션의 첫 일반 조회 때 DB 전체를 기준으로 생긴다. 회원 확인 같은 일반 조회를 그룹 잠금보다 먼저 하면,
 * 잠금을 기다린 뒤 일반 조회로 읽는 멤버 목록과 앨범이 기다리기 전 상태다. 그러면 그사이 나간 사람이 멤버로 보여 앨범을 열거나 담고,
 * 그사이 바뀐 앨범 이름을 옛 값으로 되돌린다. 앞 요청을 커밋하지 않은 트랜잭션 안에 두고, 그사이 다른 스레드에서 앨범 요청을 출발시킨 뒤
 * 커밋해 순서를 고정한다.
 */
class AlbumLockSnapshotIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @Autowired
    private AlbumService albumService;

    @Autowired
    private GroupInvitationService groupInvitationService;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Region tokyo;
    private Region osaka;
    private Member owner;
    private Member member;
    private Long groupId;

    @BeforeEach
    void setUp() {
        List<Long> regionIds = jdbcTemplate.queryForList(
                "SELECT id FROM region WHERE region_type = 'PROVINCE' ORDER BY id LIMIT 2", Long.class);
        tokyo = regionRepository.findById(regionIds.get(0)).orElseThrow();
        osaka = regionRepository.findById(regionIds.get(1)).orElseThrow();
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
        owner = socialMember();
        member = socialMember();
        groupId = travelGroupRepository.save(TravelGroup.create("제주 가족", owner.getId(), LocalDateTime.now())).getId();
        groupInvitationService.accept(member.getId(), groupInvitationService.create(owner.getId(), groupId).getToken());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void 나가는_사이에_앨범을_열어도_나간_사람의_앨범이_생기지_않는다() throws Exception {
        Long recordId = record(member);

        String outcome = whileCommitting(() -> groupLeaveService.leave(member.getId(), groupId),
                () -> albumService.open(member.getId(), groupId, recordId, "나간 뒤 앨범", null));

        assertThat(outcome).isEqualTo("GROUP_NOT_FOUND");
        assertThat(count("SELECT COUNT(*) FROM album WHERE opener_member_id = ?", member.getId())).isZero();
    }

    @Test
    void 나가는_사이에_앨범에_담아도_나간_사람의_항목이_생기지_않는다() throws Exception {
        Long albumId = albumService.open(owner.getId(), groupId, record(owner), "도쿄", null).id();
        Long recordId = record(member);

        String outcome = whileCommitting(() -> groupLeaveService.leave(member.getId(), groupId),
                () -> albumService.addEntry(member.getId(), groupId, albumId, recordId));

        assertThat(outcome).isEqualTo("GROUP_NOT_FOUND");
        assertThat(count("SELECT COUNT(*) FROM album_entry WHERE member_id = ?", member.getId())).isZero();
    }

    @Test
    void 이름을_바꾸는_사이에_다른_멤버가_지역을_바꿔도_이름이_남는다() throws Exception {
        Long albumId = albumService.open(owner.getId(), groupId, record(owner), "도쿄 3월", tokyo.getId()).id();

        String outcome = whileCommitting(() -> albumService.update(owner.getId(), groupId, albumId, "도쿄 여행", null),
                () -> albumService.update(member.getId(), groupId, albumId, null, osaka.getId()));

        assertThat(outcome).isEqualTo("OK");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM album WHERE id = ?", String.class, albumId))
                .as("먼저 커밋한 이름 바꾸기").isEqualTo("도쿄 여행");
        assertThat(jdbcTemplate.queryForObject("SELECT region_id FROM album WHERE id = ?", Long.class, albumId))
                .as("뒤에 커밋한 지역 바꾸기").isEqualTo(osaka.getId());
    }

    /**
     * first를 커밋하지 않은 트랜잭션 안에서 부르고, 그사이 다른 스레드에서 second를 출발시킨다. second가 그룹 잠금을 기다리는 것을
     * 확인한 뒤 first를 커밋하고 second의 결과를 OK 또는 에러 코드로 돌려준다.
     */
    private String whileCommitting(Runnable first, Callable<?> second) throws Exception {
        Future<String> future = new TransactionTemplate(transactionManager).execute(status -> {
            first.run();
            Future<String> started = executor.submit(() -> {
                try {
                    second.call();
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.getErrorCode().toString();
                }
            });
            waitUntilBlocked(started);
            return started;
        });
        return future.get(10, TimeUnit.SECONDS);
    }

    // second가 그룹 잠금을 기다리는 지점까지 갈 시간을 준다. 기다리지 않고 끝나면 이 테스트가 보려는 겹침이 아니다.
    private static void waitUntilBlocked(Future<String> future) {
        try {
            String outcome = future.get(1, TimeUnit.SECONDS);
            throw new AssertionError("뒤 요청이 그룹 잠금을 기다리지 않고 끝났다: " + outcome);
        } catch (TimeoutException blocked) {
            // 그룹 잠금을 기다리는 중이다.
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int count(String sql, Long memberId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, memberId);
    }

    private Long record(Member writer) {
        TravelRecord record = TravelRecord.of(writer, tokyo, "도쿄", "본문", LocalDate.of(2026, 3, 1), null, TODAY);
        record.synchronizeMedia(ObjectKeys.from(
                List.of("travel-records/" + writer.getId() + "/" + UUID.randomUUID() + "-a.jpg")));
        return travelRecordRepository.save(record).getId();
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
