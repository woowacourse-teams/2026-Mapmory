package com.mapmory.backend.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V28 테이블과 엔티티 매핑, 그리고 DB 제약을 확인한다.
 */
@DataJpaTest
class GroupRepositoryTest extends MySqlTestContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member friend;

    @BeforeEach
    void setUp() {
        owner = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "소현",
                UUID.randomUUID()));
        friend = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "지수",
                UUID.randomUUID()));
    }

    @Test
    void 그룹을_저장하면_만든_사람이_멤버로_함께_저장된다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));

        TravelGroup found = travelGroupRepository.findById(group.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("도쿄 친구들");
        assertThat(found.getMembers()).extracting(GroupMember::getMemberId).containsExactly(owner.getId());
        assertThat(travelGroupRepository.existsMember(group.getId(), owner.getId())).isTrue();
        assertThat(travelGroupRepository.existsMember(group.getId(), friend.getId())).isFalse();
    }

    @Test
    void 같은_그룹에_같은_회원은_한_번만_들어간다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                group.getId(), owner.getId(), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 초대_코드는_겹치지_않는다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));
        groupInvitationRepository.saveAndFlush(
                new GroupInvitation(group.getId(), "token-1", "ABC123", owner.getId(), NOW.plusDays(1)));

        assertThatThrownBy(() -> groupInvitationRepository.saveAndFlush(
                new GroupInvitation(group.getId(), "token-2", "ABC123", owner.getId(), NOW.plusDays(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 앨범은_연_사람의_기록과_함께_저장된다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));
        Album album = albumRepository.saveAndFlush(Album.open(group.getId(), "도쿄 3박 4일", japan(), owner.getId(), 101L));

        Album found = albumRepository.findById(album.getId()).orElseThrow();

        assertThat(found.getRegion().getId()).isEqualTo(japan().getId());
        assertThat(found.travelRecordIds()).containsExactly(101L);
    }

    @Test
    void 한_앨범에_같은_기록은_한_번만_담긴다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));
        Album album = albumRepository.saveAndFlush(Album.open(group.getId(), "도쿄 3박 4일", japan(), owner.getId(), 101L));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO album_entry (album_id, travel_record_id, member_id) VALUES (?, ?, ?)",
                album.getId(), 101L, owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_앨범에_같은_멤버의_다른_기록은_담긴다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("도쿄 친구들", owner.getId(), NOW));
        Album album = albumRepository.saveAndFlush(Album.open(group.getId(), "도쿄 3박 4일", japan(), owner.getId(), 101L));

        jdbcTemplate.update(
                "INSERT INTO album_entry (album_id, travel_record_id, member_id) VALUES (?, ?, ?)",
                album.getId(), 102L, owner.getId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM album_entry WHERE album_id = ? AND member_id = ?",
                Long.class, album.getId(), owner.getId()))
                .isEqualTo(2L);
    }

    @Test
    void 한_기록을_여러_앨범에_담을_수_있다() {
        TravelGroup group = travelGroupRepository.saveAndFlush(TravelGroup.create("일본 친구들", owner.getId(), NOW));
        albumRepository.saveAndFlush(Album.open(group.getId(), "도쿄", japan(), owner.getId(), 101L));
        Album osaka = albumRepository.saveAndFlush(Album.open(group.getId(), "오사카", japan(), owner.getId(), 101L));

        assertThat(albumRepository.findById(osaka.getId()).orElseThrow().travelRecordIds()).containsExactly(101L);
    }

    private Region japan() {
        return regionRepository.findByParentIdAndRegionTypeAndRegionCode(null, RegionType.COUNTRY, "JP").orElseThrow();
    }
}
