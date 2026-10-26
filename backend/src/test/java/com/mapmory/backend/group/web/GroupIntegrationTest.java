package com.mapmory.backend.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
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
 * 그룹 만들기·목록·조회·이름 바꾸기(T1)를 HTTP부터 DB까지 확인한다.
 */
class GroupIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member jisu;
    private Member minho;

    @BeforeEach
    void setUp() {
        jisu = socialMember("지수");
        minho = socialMember("민호");
    }

    @Test
    void 그룹을_만들면_만든_사람이_방장인_멤버로_응답한다() throws Exception {
        createGroup(jisu, "  제주 가족 ")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/groups/")))
                .andExpect(jsonPath("$.data.name").value("제주 가족"))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.members[0].owner").value(true))
                .andExpect(jsonPath("$.data.members[0].me").value(true))
                .andExpect(jsonPath("$.data.members[0].memberId").doesNotExist());
    }

    @Test
    void 그룹을_조회하면_멤버를_들어온_순서로_보여_주고_나를_표시한다() throws Exception {
        TravelGroup group = groupWith(jisu, minho);

        mockMvc.perform(get("/api/v1/groups/{groupId}", group.getId()).header(HttpHeaders.AUTHORIZATION, bearer(minho)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(group.getId()))
                .andExpect(jsonPath("$.data.members[0].nickname").value("지수"))
                .andExpect(jsonPath("$.data.members[0].owner").value(true))
                .andExpect(jsonPath("$.data.members[0].me").value(false))
                .andExpect(jsonPath("$.data.members[1].nickname").value("민호"))
                .andExpect(jsonPath("$.data.members[1].owner").value(false))
                .andExpect(jsonPath("$.data.members[1].me").value(true));
    }

    @Test
    void 내_그룹_목록은_들어간_순서이고_멤버_수와_앨범_수를_담는다() throws Exception {
        TravelGroup first = groupWith(jisu, minho);
        Long second = createdGroupId(minho, "회사 동기");
        createdGroupId(jisu, "다른 그룹");
        insertAlbum(first.getId(), minho);

        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(minho)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data[0].memberCount").value(2))
                .andExpect(jsonPath("$.data[0].albumCount").value(1))
                .andExpect(jsonPath("$.data[1].id").value(second))
                .andExpect(jsonPath("$.data[1].name").value("회사 동기"))
                .andExpect(jsonPath("$.data[1].memberCount").value(1))
                .andExpect(jsonPath("$.data[1].albumCount").value(0));
    }

    @Test
    void 그룹이_없으면_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(jisu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 멤버는_누구나_이름을_바꾼다() throws Exception {
        TravelGroup group = groupWith(jisu, minho);

        rename(minho, group.getId(), "제주 가족여행")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("제주 가족여행"))
                .andExpect(jsonPath("$.data.members.length()").value(2));

        assertThat(travelGroupRepository.findById(group.getId()).orElseThrow().getName())
                .isEqualTo("제주 가족여행");
    }

    @Test
    void 규칙에_맞지_않는_이름은_400이다() throws Exception {
        TravelGroup group = groupWith(jisu);

        createGroup(jisu, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rename(jisu, group.getId(), "가".repeat(31))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 멤버가_아니면_그룹이_있어도_404다() throws Exception {
        TravelGroup group = groupWith(jisu);

        mockMvc.perform(get("/api/v1/groups/{groupId}", group.getId()).header(HttpHeaders.AUTHORIZATION, bearer(minho)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        rename(minho, group.getId(), "뺏은 이름")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/groups/{groupId}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(jisu)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 게스트는_그룹을_쓸_수_없다() throws Exception {
        Member guest = memberRepository.save(Member.ofGuest(UUID.randomUUID().toString(), "게스트", UUID.randomUUID()));

        createGroup(guest, "제주 가족")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
        mockMvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(guest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
        assertThat(travelGroupRepository.count()).isZero();
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    // 초대(T2)가 아직 없어서 멤버는 저장소로 바로 넣는다.
    private TravelGroup groupWith(Member owner, Member... others) {
        TravelGroup group = travelGroupRepository.save(TravelGroup.create("제주 가족", owner.getId(), LocalDateTime.now()));
        LocalDateTime joinedAt = LocalDateTime.now();
        for (Member other : others) {
            joinedAt = joinedAt.plusMinutes(1);
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                    group.getId(), other.getId(), joinedAt);
        }
        return group;
    }

    private void insertAlbum(Long groupId, Member opener) {
        Long regionId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM region", Long.class);
        jdbcTemplate.update("INSERT INTO album (group_id, name, region_id, opener_member_id) VALUES (?, ?, ?, ?)",
                groupId, "도쿄 3월", regionId, opener.getId());
    }

    private Long createdGroupId(Member member, String name) throws Exception {
        String body = createGroup(member, name)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions createGroup(Member member, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/groups")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private ResultActions rename(Member member, Long groupId, String name) throws Exception {
        return mockMvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
