package com.mapmory.backend.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.group.domain.AlbumRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 앨범 열기·담기·이름과 지역 바꾸기(T4)를 HTTP부터 DB까지 확인한다.
 */
class AlbumIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member jisu;
    private Member minho;
    private TravelGroup group;
    private Region seoul;
    private Region busan;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
        jisu = socialMember("지수");
        minho = socialMember("민호");
        group = groupWith(jisu, minho);
        List<Long> provinceIds = jdbcTemplate.queryForList(
                "SELECT id FROM region WHERE region_type = 'PROVINCE' ORDER BY id LIMIT 2", Long.class);
        seoul = regionRepository.findById(provinceIds.get(0)).orElseThrow();
        busan = regionRepository.findById(provinceIds.get(1)).orElseThrow();
    }

    @Test
    void 내_기록으로_앨범을_열면_연_사람의_기록이_첫_항목이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), "a.jpg", "b.jpg");

        open(jisu, group.getId(), record.getId(), "  도쿄 3월 ", busan.getId())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("/api/v1/groups/" + group.getId() + "/albums/")))
                .andExpect(jsonPath("$.data.groupId").value(group.getId()))
                .andExpect(jsonPath("$.data.name").value("도쿄 3월"))
                .andExpect(jsonPath("$.data.region.id").value(busan.getId()))
                .andExpect(jsonPath("$.data.region.code").value(busan.getRegionCode()))
                .andExpect(jsonPath("$.data.region.type").value("PROVINCE"))
                .andExpect(jsonPath("$.data.region.name").value(busan.getName()))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-03"))
                .andExpect(jsonPath("$.data.opener.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.opener.nickname").value("지수"))
                .andExpect(jsonPath("$.data.opener.me").value(true))
                .andExpect(jsonPath("$.data.myTravelRecordId").value(record.getId()))
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].travelRecordId").value(record.getId()))
                .andExpect(jsonPath("$.data.entries[0].owner.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.entries[0].title").value("도쿄"))
                .andExpect(jsonPath("$.data.entries[0].content").value("본문"))
                .andExpect(jsonPath("$.data.entries[0].addedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[0].photos.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].photos[0].id").isNumber())
                .andExpect(jsonPath("$.data.entries[0].photos[0].viewUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[0].photos[0].viewUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data.entries[0].photos[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.entries[0].photos[0].objectKey").doesNotExist());
    }

    @Test
    void 지역을_보내지_않으면_기록의_지역이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.region.id").value(seoul.getId()))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-01"));
    }

    @Test
    void 남의_기록이나_없는_기록으로는_열_수_없다() throws Exception {
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), minhoRecord.getId(), "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
        open(jisu, group.getId(), Long.MAX_VALUE, "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
        assertThat(albumRepository.count()).isZero();
    }

    @Test
    void 없는_지역이면_404다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "도쿄", Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));
    }

    @Test
    void 이름이_규칙에_맞지_않으면_400이다() throws Exception {
        TravelRecord record = record(jisu, LocalDate.of(2026, 3, 1), null);

        open(jisu, group.getId(), record.getId(), "가".repeat(201), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        open(jisu, group.getId(), record.getId(), " ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 게스트는_앨범을_열_수_없다() throws Exception {
        Member guest = memberRepository.save(Member.ofGuest(UUID.randomUUID().toString(), "게스트", UUID.randomUUID()));
        TravelRecord record = record(guest, LocalDate.of(2026, 3, 1), null);

        open(guest, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
    }

    @Test
    void 멤버가_아니면_그룹이_있어도_404다() throws Exception {
        Member stranger = socialMember("낯선 사람");
        TravelRecord record = record(stranger, LocalDate.of(2026, 3, 1), null);
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        open(stranger, group.getId(), record.getId(), "도쿄", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        addEntry(stranger, group.getId(), albumId, record.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        update(stranger, group.getId(), albumId, "{\"name\": \"남의 앨범\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 다른_그룹의_앨범은_내_그룹_경로로_찾을_수_없다() throws Exception {
        TravelGroup other = groupWith(jisu);
        Long otherAlbumId = openedAlbumId(jisu, other.getId(), record(jisu, LocalDate.of(2026, 3, 1), null));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 3, 2), null);

        addEntry(minho, group.getId(), otherAlbumId, minhoRecord.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
        update(jisu, group.getId(), otherAlbumId, "{\"name\": \"바꿈\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    void 처음_담으면_201이고_같은_기록을_다시_보내면_200이다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));
        TravelRecord minhoRecord = record(minho, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 4), "c.jpg");

        addEntry(minho, group.getId(), albumId, minhoRecord.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startDate").value("2026-02-28"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-04"))
                .andExpect(jsonPath("$.data.opener.memberId").value(jisu.getId()))
                .andExpect(jsonPath("$.data.opener.me").value(false))
                .andExpect(jsonPath("$.data.myTravelRecordId").value(minhoRecord.getId()))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.entries[1].owner.nickname").value("민호"))
                .andExpect(jsonPath("$.data.entries[1].owner.me").value(true))
                .andExpect(jsonPath("$.data.entries[1].addedAt").isNotEmpty());
        addEntry(minho, group.getId(), albumId, minhoRecord.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(2));
    }

    @Test
    void 이미_다른_기록을_담았으면_409다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        addEntry(jisu, group.getId(), albumId, record(jisu, LocalDate.of(2026, 3, 2), null).getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALBUM_ENTRY_ALREADY_EXISTS"));
    }

    @Test
    void 남의_기록은_담을_수_없다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        addEntry(minho, group.getId(), albumId, record(jisu, LocalDate.of(2026, 3, 2), null).getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
    }

    @Test
    void 멤버는_누구나_보낸_값만_바꾼다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        update(minho, group.getId(), albumId, "{\"name\": \" 도쿄 봄 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("도쿄 봄"))
                .andExpect(jsonPath("$.data.region.id").value(seoul.getId()));
        update(minho, group.getId(), albumId, "{\"regionId\": " + busan.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("도쿄 봄"))
                .andExpect(jsonPath("$.data.region.id").value(busan.getId()));

        assertThat(albumRepository.findById(albumId).orElseThrow().getName()).isEqualTo("도쿄 봄");
    }

    @Test
    void 바꿀_값이_없거나_규칙에_맞지_않으면_400이다() throws Exception {
        Long albumId = openedAlbumId(jisu, record(jisu, LocalDate.of(2026, 3, 1), null));

        update(jisu, group.getId(), albumId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        update(jisu, group.getId(), albumId, "{\"name\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    private TravelGroup groupWith(Member owner, Member... others) {
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(others.length + 1);
        TravelGroup created = travelGroupRepository.save(TravelGroup.create("도쿄 친구들", owner.getId(), joinedAt));
        for (Member other : others) {
            joinedAt = joinedAt.plusMinutes(1);
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                    created.getId(), other.getId(), joinedAt);
        }
        return created;
    }

    // 사진 없는 기록은 이제 새로 만들 수 없지만(사진 1장 이상) 예전 기록으로 남아 있다. 그래서 사진이 없으면 붙이지 않는다.
    private TravelRecord record(Member member, LocalDate startDate, LocalDate endDate, String... fileNames) {
        TravelRecord record = TravelRecord.of(member, seoul, "도쿄", "본문", startDate, endDate, TODAY);
        if (fileNames.length > 0) {
            record.synchronizeMedia(ObjectKeys.from(Arrays.stream(fileNames)
                    .map(fileName -> "travel-records/" + member.getId() + "/" + UUID.randomUUID() + "-" + fileName)
                    .toList()));
        }
        return travelRecordRepository.save(record);
    }

    private Long openedAlbumId(Member member, TravelRecord record) throws Exception {
        return openedAlbumId(member, group.getId(), record);
    }

    private Long openedAlbumId(Member member, Long groupId, TravelRecord record) throws Exception {
        String body = open(member, groupId, record.getId(), "도쿄", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions open(Member member, Long groupId, Long travelRecordId, String name, Long regionId)
            throws Exception {
        String body = "{\"travelRecordId\": " + travelRecordId + ", \"name\": \"" + name + "\""
                + (regionId == null ? "" : ", \"regionId\": " + regionId) + "}";
        return mockMvc.perform(post("/api/v1/groups/{groupId}/albums", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions addEntry(Member member, Long groupId, Long albumId, Long travelRecordId) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/{groupId}/albums/{albumId}/entries", groupId, albumId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"travelRecordId\": " + travelRecordId + "}"));
    }

    private ResultActions update(Member member, Long groupId, Long albumId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/groups/{groupId}/albums/{albumId}", groupId, albumId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
