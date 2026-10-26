package com.mapmory.backend.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.group.domain.GroupInvitationRepository;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 초대 만들기·미리보기·수락(T2)을 HTTP부터 DB까지 확인한다.
 */
class GroupInvitationIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelGroupRepository travelGroupRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member jisu;
    private Member minho;
    private TravelGroup group;

    @BeforeEach
    void setUp() {
        jisu = socialMember("지수");
        minho = socialMember("민호");
        group = travelGroupRepository.save(TravelGroup.create("제주 가족", jisu.getId(), LocalDateTime.now()));
    }

    @Test
    void 멤버는_토큰과_6자리_코드가_든_1일짜리_초대를_만든다() throws Exception {
        createInvitation(jisu, group.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").value(matchesPattern("[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("[A-HJ-NP-Z2-9]{6}")))
                .andExpect(jsonPath("$.data.expiresAt").exists());

        assertThat(groupInvitationRepository.findAll()).singleElement()
                .satisfies(invitation -> {
                    assertThat(invitation.getInviterMemberId()).isEqualTo(jisu.getId());
                    assertThat(invitation.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
                });
    }

    @Test
    void 멤버가_아니면_초대를_만들_수_없다() throws Exception {
        createInvitation(minho, group.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 링크_토큰으로_수락하면_멤버가_되고_그룹을_돌려준다() throws Exception {
        String token = JsonPath.read(invitationBody(jisu), "$.data.token");

        accept(minho, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(group.getId()))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[1].nickname").value("민호"))
                .andExpect(jsonPath("$.data.members[1].me").value(true))
                .andExpect(jsonPath("$.data.members[1].owner").value(false));

        assertThat(travelGroupRepository.existsMember(group.getId(), minho.getId())).isTrue();
    }

    @Test
    void 코드는_소문자로_넣어도_수락된다() throws Exception {
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");

        accept(minho, code.toLowerCase(Locale.ROOT))
                .andExpect(status().isOk());

        assertThat(travelGroupRepository.existsMember(group.getId(), minho.getId())).isTrue();
    }

    @Test
    void 이미_멤버면_아무것도_바꾸지_않고_성공한다() throws Exception {
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");

        accept(jisu, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1));
    }

    @Test
    void 미리보기는_그룹_이름과_인원과_내가_멤버인지를_보여_준다() throws Exception {
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");

        preview(minho, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("제주 가족"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.alreadyMember").value(false));
        preview(jisu, code)
                .andExpect(jsonPath("$.data.alreadyMember").value(true));
    }

    @Test
    void 게스트는_미리보기만_되고_수락은_403이다() throws Exception {
        Member guest = memberRepository.save(Member.ofGuest(UUID.randomUUID().toString(), "게스트", UUID.randomUUID()));
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");

        preview(guest, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("제주 가족"));
        accept(guest, code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_REQUIRED"));
        assertThat(travelGroupRepository.existsMember(group.getId(), guest.getId())).isFalse();
    }

    @Test
    void 만료되었거나_없는_초대는_404다() throws Exception {
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");
        jdbcTemplate.update("UPDATE group_invitation SET expires_at = ?", LocalDateTime.now().minusMinutes(1));

        preview(minho, code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        accept(minho, code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        accept(minho, "ZZZZZZ")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        assertThat(travelGroupRepository.existsMember(group.getId(), minho.getId())).isFalse();
    }

    @Test
    void 서른_명이면_수락을_막는다() throws Exception {
        LocalDateTime joinedAt = LocalDateTime.now();
        for (int i = 0; i < 29; i++) {
            Member member = socialMember("멤버" + i);
            jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, joined_at) VALUES (?, ?, ?)",
                    group.getId(), member.getId(), joinedAt);
        }
        jdbcTemplate.update("UPDATE travel_group SET member_count = 30 WHERE id = ?", group.getId());
        String code = JsonPath.read(invitationBody(jisu), "$.data.code");

        accept(minho, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));
        assertThat(travelGroupRepository.existsMember(group.getId(), minho.getId())).isFalse();
    }

    @Test
    void 미리보기와_수락을_합쳐_1분에_10번을_넘으면_429다() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            preview(minho, "ZZZZZZ").andExpect(status().isNotFound());
            accept(minho, "ZZZZZZ").andExpect(status().isNotFound());
        }

        preview(minho, "ZZZZZZ")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INVITATION_RATE_LIMITED"));
        preview(jisu, "ZZZZZZ").andExpect(status().isNotFound());
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    private String invitationBody(Member inviter) throws Exception {
        return createInvitation(inviter, group.getId())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions createInvitation(Member member, Long groupId) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/{groupId}/invitations", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions preview(Member member, String tokenOrCode) throws Exception {
        return mockMvc.perform(get("/api/v1/invitations/{tokenOrCode}", tokenOrCode)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions accept(Member member, String tokenOrCode) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/{tokenOrCode}/accept", tokenOrCode)
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
