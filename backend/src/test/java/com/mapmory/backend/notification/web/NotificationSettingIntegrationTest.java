package com.mapmory.backend.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class NotificationSettingIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private Member jisu;
    private Member minho;

    @BeforeEach
    void setUp() {
        jisu = socialMember("지수");
        minho = socialMember("민호");
    }

    @Test
    void 한_번도_바꾸지_않았으면_켜짐이다() throws Exception {
        find(jisu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupAlbum").value(true));
    }

    @Test
    void 끄면_내_설정만_꺼진다() throws Exception {
        change(jisu, "{\"groupAlbum\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupAlbum").value(false));

        find(jisu).andExpect(jsonPath("$.data.groupAlbum").value(false));
        find(minho).andExpect(jsonPath("$.data.groupAlbum").value(true));
    }

    @Test
    void 껐다가_다시_켤_수_있다() throws Exception {
        change(jisu, "{\"groupAlbum\": false}").andExpect(status().isOk());
        change(jisu, "{\"groupAlbum\": true}").andExpect(status().isOk());

        find(jisu).andExpect(jsonPath("$.data.groupAlbum").value(true));
        assertThat(notificationSettingRepository.count()).isEqualTo(1);
    }

    @Test
    void 처음_바꾸는_요청이_동시에_와도_실패하지_않고_한_행만_남는다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = IntStream.range(0, 2)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return change(jisu, "{\"groupAlbum\": false}").andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();

            for (Future<Integer> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            }
            assertThat(notificationSettingRepository.count()).isEqualTo(1);
            find(jisu).andExpect(jsonPath("$.data.groupAlbum").value(false));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 받을_멤버_중_끈_회원만_고른다() throws Exception {
        change(jisu, "{\"groupAlbum\": false}").andExpect(status().isOk());
        change(minho, "{\"groupAlbum\": true}").andExpect(status().isOk());
        Member never = socialMember("수아");

        assertThat(notificationSettingRepository.findGroupAlbumOffAmong(
                List.of(jisu.getId(), minho.getId(), never.getId())))
                .containsExactly(jisu.getId());
    }

    @Test
    void 값을_보내지_않으면_400이다() throws Exception {
        change(jisu, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 탈퇴한_회원의_남은_토큰으로는_바꾸지_못한다() throws Exception {
        jisu.withdraw(LocalDateTime.now());
        memberRepository.save(jisu);

        change(jisu, "{\"groupAlbum\": false}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INACTIVE_MEMBER"));
    }

    private ResultActions find(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions change(Member member, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/notification-settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
