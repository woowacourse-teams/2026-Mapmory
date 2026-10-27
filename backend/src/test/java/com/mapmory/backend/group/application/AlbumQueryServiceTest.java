package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.support.SqlStatementCounter;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 앨범 조회의 SQL 수가 기록·앨범 수와 상관없이 일정한지 확인한다.
 */
class AlbumQueryServiceTest extends IntegrationTest {

    @Autowired
    private AlbumQueryService albumQueryService;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Region region;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
        Long provinceId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM region WHERE region_type = 'PROVINCE'", Long.class);
        region = regionRepository.findById(provinceId).orElseThrow();
    }

    @Test
    void 상세는_담긴_기록이_1개일_때와_10개일_때_SQL_수가_같다() {
        List<Member> members = members(10);
        TravelGroup group = groupOf(members);
        Long small = albumService.open(members.getFirst().getId(), group.getId(),
                record(members.getFirst()).getId(), "작은 앨범", null).id();
        Long large = albumService.open(members.getFirst().getId(), group.getId(),
                record(members.getFirst()).getId(), "큰 앨범", null).id();
        members.stream().skip(1).forEach(member ->
                albumService.addEntry(member.getId(), group.getId(), large, record(member).getId()));
        Long viewer = members.getFirst().getId();
        SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);

        long forOne = counter.count(() -> albumQueryService.detail(viewer, group.getId(), small));
        long forTen = counter.count(() -> albumQueryService.detail(viewer, group.getId(), large));

        assertThat(albumQueryService.detail(viewer, group.getId(), large).entries()).hasSize(10);
        assertThat(forTen).isEqualTo(forOne).isEqualTo(6);
    }

    @Test
    void 목록은_앨범과_기록이_늘어도_SQL_수가_같다() {
        List<Member> members = members(5);
        TravelGroup few = groupOf(members);
        TravelGroup many = groupOf(members);
        albumService.open(members.getFirst().getId(), few.getId(), record(members.getFirst()).getId(), "하나", null);
        for (int i = 0; i < 5; i++) {
            Long albumId = albumService.open(members.getFirst().getId(), many.getId(),
                    record(members.getFirst()).getId(), "앨범 " + i, null).id();
            members.stream().skip(1).forEach(member ->
                    albumService.addEntry(member.getId(), many.getId(), albumId, record(member).getId()));
        }
        Long viewer = members.getFirst().getId();
        SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);

        long forOne = counter.count(() -> albumQueryService.list(viewer, few.getId(), 0, 20));
        long forFive = counter.count(() -> albumQueryService.list(viewer, many.getId(), 0, 20));

        assertThat(albumQueryService.list(viewer, many.getId(), 0, 20).items()).hasSize(5);
        assertThat(forFive).isEqualTo(forOne);
    }

    private List<Member> members(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원" + i,
                            UUID.randomUUID());
                    return memberRepository.save(member);
                })
                .toList();
    }

    private TravelGroup groupOf(List<Member> members) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 1, 0, 0);
        TravelGroup group = travelGroupRepository.save(
                TravelGroup.create("도쿄 친구들", members.getFirst().getId(), joinedAt));
        members.stream().skip(1).forEach(member -> jdbcTemplate.update(
                "INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                group.getId(), member.getId(), joinedAt));
        return group;
    }

    private TravelRecord record(Member member) {
        TravelRecord record = TravelRecord.of(member, region, "도쿄", "본문", LocalDate.of(2026, 3, 1), null);
        record.synchronizeMedia(List.of("travel-records/" + member.getId() + "/" + UUID.randomUUID() + ".jpg"));
        return travelRecordRepository.save(record);
    }
}
