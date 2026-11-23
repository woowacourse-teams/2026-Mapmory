package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import com.mapmory.backend.travelrecord.application.PhotoRecordNotificationOutboxWriter;
import com.mapmory.backend.travelrecord.application.TravelRecordCommand;
import com.mapmory.backend.travelrecord.application.TravelRecordService;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PhotoRecordCompletionPushIntegrationTest extends IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PushTokenRepository tokens;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired TravelRecordRepository records;
    @Autowired PhotoRecordNotificationOutboxWriter writer;
    @Autowired TravelRecordService service;
    @Autowired PlatformTransactionManager transactionManager;
    private String accessToken;
    private Member member;
    private PushToken token;
    private String photoKey;

    @BeforeEach
    void setUp() throws Exception {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        String login = mvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(login, "$.data.accessToken");
        member = members.findAll().stream().max(java.util.Comparator.comparing(Member::getId)).orElseThrow();
        photoKey = photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
        token = tokens.saveAndFlush(new PushToken(member.getId(), UUID.randomUUID().toString(), PushPlatform.IOS));
    }

    @AfterEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    @Test
    void 요청한_기기에_기록_완료_이벤트를_한_번만_저장한다() throws Exception {
        String response = create(token.getId()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                writer.save(recordId, member.getId(), token.getId()));
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("PHOTO_RECORD_COMPLETED");
            assertThat(event.getEventKey()).isEqualTo("photo-record:" + recordId + ":completed");
            assertThat(event.getPushTokenId()).isEqualTo(token.getId());
            assertThat(event.getMemberId()).isEqualTo(member.getId());
            assertThat(((Number) JsonPath.read(event.getPayload(), "$.recordId")).longValue()).isEqualTo(recordId);
        });
    }

    @Test
    void 푸시_등록을_생략한_기존_요청은_기록만_저장한다() throws Exception {
        create(null).andExpect(status().isCreated());
        assertThat(records.count()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void 다른_회원의_기기와_없는_기기는_기록도_저장하지_않는다() throws Exception {
        Member other = members.save(Member.of("다른 회원", UUID.randomUUID()));
        PushToken foreign = tokens.saveAndFlush(new PushToken(other.getId(), UUID.randomUUID().toString(), PushPlatform.ANDROID));
        create(foreign.getId()).andExpect(status().isNotFound());
        create(Long.MAX_VALUE).andExpect(status().isNotFound());
        assertThat(records.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void 잘못된_등록_ID는_입력_오류다() throws Exception {
        create(0L).andExpect(status().isBadRequest());
        assertThat(records.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void 기록_생성_트랜잭션이_실패하면_알림도_롤백한다() {
        TravelRecordCommand command = new TravelRecordCommand("JP", null, null, "여행", "",
                LocalDate.of(2026, 8, 1), null,
                List.of(photoKey), List.of(), null, token.getId());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.create(member, command);
            assertThat(outbox.count()).isEqualTo(1);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(records.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    private ResultActions create(Long pushTokenId) throws Exception {
        return mvc.perform(post("/api/v1/travel-records")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"countryCode":"JP","title":"여행","startDate":"2026-08-01","objectKeys":["%s"]%s}
                        """.formatted(photoKey,
                        pushTokenId == null ? "" : ",\"pushTokenId\":" + pushTokenId)));
    }
}
