package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 탈퇴하거나 버려지고 30일이 지난 회원의 데이터 파기. (#435)
 */
class MemberPurgeIntegrationTest extends IntegrationTest {

    @Autowired
    private MemberPurgeService memberPurgeService;

    @Autowired
    private List<MemberDataPurger> purgers;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private final List<String> deletedKeys = new ArrayList<>();
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        willAnswer(invocation -> {
            deletedKeys.addAll(invocation.<Collection<String>>getArgument(0));
            return List.of();
        }).given(objectDeleter).delete(anyCollection());
    }

    @Test
    void 공유_영상_기록_태그_의견_알림_장소기록_순서로_지운다() {
        assertThat(purgers).extracting(purger -> purger.getClass().getSimpleName()).containsExactly(
                "ShareVideoPurger", "TravelRecordPurger", "TagPurger", "ServiceFeedbackPurger", "NotificationPurger", "PlaceLookupLogPurger");
    }

    @Test
    void 탈퇴하고_30일이_지난_회원의_데이터를_지우고_행은_남긴다() {
        Long member = withdrawnDaysAgo(31);
        Long record = recordOf(member, "mapmory/travel-records/1/a.jpg", "mapmory/travel-records/1/a-thumb.jpg");
        Long tag = tagOf(member);
        link(record, tag);
        feedbackOf(member);
        videoOf(member, "mapmory/share-videos/1/result.mp4", "mapmory/share-videos/1/attempt.mp4");
        settingOf(member);
        outboxOf(member);

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(deletedKeys).containsExactlyInAnyOrder(
                "mapmory/travel-records/1/a.jpg", "mapmory/travel-records/1/a-thumb.jpg",
                "mapmory/share-videos/1/result.mp4", "mapmory/share-videos/1/attempt.mp4");
        assertThat(count("travel_record", member)).isZero();
        assertThat(count("tag", member)).isZero();
        assertThat(count("share_video_job", member)).isZero();
        assertThat(count("notification_setting", member)).isZero();
        assertThat(count("notification_outbox", member)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_media WHERE travel_record_id = ?", Long.class, record)).isZero();
        assertThat(count("service_feedback", member)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_feedback WHERE member_id IS NULL", Long.class)).isEqualTo(1);
        assertThat(memberRepository.findById(member).orElseThrow().getPurgedAt()).isNotNull();
    }

    // 썸네일 워커가 파기 도중 썸네일을 올리고 thumb_key를 채울 수 있다. 행을 지운 뒤 썸네일 키를 정리 큐에 넣어 남지 않게 한다.
    @Test
    void 기록을_지운_뒤_만들어졌을_수_있는_썸네일을_정리_큐에_넣는다() {
        Long member = withdrawnDaysAgo(31);
        recordOf(member, "mapmory/travel-records/1/a.jpg", null);

        memberPurgeService.run();

        assertThat(jdbcTemplate.queryForList("SELECT object_key FROM orphaned_media", String.class))
                .containsExactly("mapmory/derived/thumb/1/a.webp");
    }

    @Test
    void 버려지고_30일이_지난_게스트도_지운다() {
        Member guest = Member.ofGuest("guest-" + UUID.randomUUID(), "게스트", UUID.randomUUID());
        guest.abandon(now.minusDays(31));
        Long member = memberRepository.save(guest).getId();
        recordOf(member, "mapmory/travel-records/2/a.jpg", null);

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(count("travel_record", member)).isZero();
    }

    @Test
    void 삼십일이_지나지_않았거나_활동_중인_회원은_지우지_않는다() {
        Long recent = withdrawnDaysAgo(29);
        Long active = memberRepository.save(social()).getId();
        Long recentRecord = recordOf(recent, "mapmory/travel-records/3/a.jpg", null);
        Long activeRecord = recordOf(active, "mapmory/travel-records/4/a.jpg", null);

        assertThat(memberPurgeService.run()).isZero();

        verify(objectDeleter, never()).delete(anyCollection());
        assertThat(count("travel_record", recent)).isEqualTo(1);
        assertThat(count("travel_record", active)).isEqualTo(1);
        assertThat(recentRecord).isNotEqualTo(activeRecord);
    }

    @Test
    void 이미_파기한_회원은_다시_보지_않는다() {
        withdrawnDaysAgo(31);
        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(memberPurgeService.run()).isZero();
    }

    @Test
    void S3에서_지우지_못하면_행을_남기고_다음_회원을_처리하고_다음에_다시_지운다() {
        Long failing = withdrawnDaysAgo(31);
        Long succeeding = withdrawnDaysAgo(31);
        String failingKey = "mapmory/travel-records/5/a.jpg";
        recordOf(failing, failingKey, null);
        recordOf(succeeding, "mapmory/travel-records/6/a.jpg", null);
        willAnswer(invocation -> invocation.<Collection<String>>getArgument(0).contains(failingKey)
                ? List.of(failingKey)
                : List.of()).given(objectDeleter).delete(anyCollection());

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(count("travel_record", failing)).isEqualTo(1);
        assertThat(memberRepository.findById(failing).orElseThrow().getPurgedAt()).isNull();
        assertThat(count("travel_record", succeeding)).isZero();

        willReturn(List.of()).given(objectDeleter).delete(anyCollection());

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(count("travel_record", failing)).isZero();
        assertThat(memberRepository.findById(failing).orElseThrow().getPurgedAt()).isNotNull();
    }

    /**
     * 렌더러가 키를 조회한 뒤에 새 시도본을 올리면, 행이 지워져 어떤 키였는지 알 수 없는 객체가 남는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RENDERING"})
    void 끝나지_않은_공유_영상이_있으면_이번에는_건너뛰고_끝난_뒤_다시_지운다(String status) {
        Long member = withdrawnDaysAgo(31);
        recordOf(member, "mapmory/travel-records/7/" + status + ".jpg", null);
        videoOf(member, status, null, null);
        leaseExpiresAt(member, now.plusMinutes(5));

        assertThat(memberPurgeService.run()).isZero();

        verify(objectDeleter, never()).delete(anyCollection());
        assertThat(count("share_video_job", member)).isEqualTo(1);
        assertThat(count("travel_record", member)).isEqualTo(1);
        assertThat(memberRepository.findById(member).orElseThrow().getPurgedAt()).isNull();

        jdbcTemplate.update("UPDATE share_video_job SET status = 'FAILED' WHERE member_id = ?", member);

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(count("share_video_job", member)).isZero();
        assertThat(count("travel_record", member)).isZero();
    }

    /**
     * 워커가 렌더링 중에 죽으면 작업은 RENDERING으로 남고 되살리는 곳이 없다. 기다리면 파기가 영원히 멈춘다.
     */
    @Test
    void 선점이_만료되고_오래_지난_렌더링_작업은_끝난_것으로_보고_시도본까지_지운다() {
        Long member = withdrawnDaysAgo(31);
        String attemptKey = "mapmory/share-videos/9/attempt.mp4";
        videoOf(member, "RENDERING", null, attemptKey);
        leaseExpiresAt(member, now.minusHours(2));

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(deletedKeys).containsExactly(attemptKey);
        assertThat(count("share_video_job", member)).isZero();
    }

    /**
     * 만료 직후에는 죽은 줄 안 워커가 아직 올리는 중일 수 있다. 그때 지우면 업로드가 끝나며 객체가 다시 생긴다.
     */
    @Test
    void 선점이_막_만료된_렌더링_작업은_기다린다() {
        Long member = withdrawnDaysAgo(31);
        videoOf(member, "RENDERING", null, "mapmory/share-videos/10/attempt.mp4");
        leaseExpiresAt(member, now.minusMinutes(30));

        assertThat(memberPurgeService.run()).isZero();

        verify(objectDeleter, never()).delete(anyCollection());
        assertThat(count("share_video_job", member)).isEqualTo(1);
    }

    @Test
    void 선점_기록이_없는_옛_렌더링_작업은_끝난_것으로_본다() {
        Long member = withdrawnDaysAgo(31);
        videoOf(member, "RENDERING", null, null);

        assertThat(memberPurgeService.run()).isEqualTo(1);

        assertThat(count("share_video_job", member)).isZero();
    }

    private void leaseExpiresAt(Long memberId, LocalDateTime expiresAt) {
        jdbcTemplate.update("UPDATE share_video_job SET lease_expires_at = ? WHERE member_id = ?", expiresAt, memberId);
    }

    private Member social() {
        return Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "지수", UUID.randomUUID());
    }

    private Long withdrawnDaysAgo(int days) {
        Member member = social();
        member.withdraw(now.minusDays(days));
        return memberRepository.save(member).getId();
    }

    private Long recordOf(Long memberId, String objectKey, String thumbKey) {
        Region region = regionRepository.findById(
                jdbcTemplate.queryForObject("SELECT id FROM region ORDER BY id LIMIT 1", Long.class)).orElseThrow();
        Member member = memberRepository.findById(memberId).orElseThrow();
        LocalDate date = LocalDate.of(2026, 3, 1);
        TravelRecord record = TravelRecord.of(member, region, "도쿄", "본문", date, date, date);
        record.synchronizeMedia(ObjectKeys.from(List.of(objectKey)));
        Long recordId = travelRecordRepository.save(record).getId();
        jdbcTemplate.update("UPDATE record_media SET thumb_key = ? WHERE travel_record_id = ?", thumbKey, recordId);
        return recordId;
    }

    private Long tagOf(Long memberId) {
        jdbcTemplate.update("INSERT INTO tag (member_id, name, name_key) VALUES (?, '여행', ?)",
                memberId, "여행-" + memberId);
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE member_id = ?", Long.class, memberId);
    }

    private void link(Long recordId, Long tagId) {
        jdbcTemplate.update("INSERT INTO travel_record_tag (travel_record_id, tag_id) VALUES (?, ?)", recordId, tagId);
    }

    private void feedbackOf(Long memberId) {
        jdbcTemplate.update("""
                INSERT INTO service_feedback (member_id, category, content, platform, status)
                VALUES (?, 'BUG_REPORT', '내용', 'IOS', 'UNCONFIRMED')
                """, memberId);
    }

    private void videoOf(Long memberId, String resultKey, String attemptKey) {
        videoOf(memberId, "COMPLETED", resultKey, attemptKey);
    }

    private void videoOf(Long memberId, String status, String resultKey, String attemptKey) {
        jdbcTemplate.update("""
                INSERT INTO share_video_job (member_id, video_type, status, snapshot_json, result_object_key, attempt_object_key)
                VALUES (?, 'TRAVEL_TIMELINE', ?, '{}', ?, ?)
                """, memberId, status, resultKey, attemptKey);
    }

    private void settingOf(Long memberId) {
        jdbcTemplate.update("INSERT INTO notification_setting (member_id, group_album) VALUES (?, FALSE)", memberId);
    }

    private void outboxOf(Long memberId) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (event_type, event_key, payload, member_id, push_token_id, next_attempt_at)
                VALUES ('VIDEO_COMPLETED', ?, '{}', ?, 1, ?)
                """, UUID.randomUUID().toString(), memberId, now);
    }

    private long count(String table, Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
    }
}
