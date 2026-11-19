package com.mapmory.backend.common.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.common.ProblemDetailFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class UnexpectedExceptionHandlerTest {

    private static final String INVITATION_TOKEN = "invitation-secret-token-0123456789abcdefghij";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProblemDetailFactory problemDetailFactory = new ProblemDetailFactory();
        mockMvc = MockMvcBuilders.standaloneSetup(new UnexpectedExceptionController())
                .setControllerAdvice(new UnexpectedExceptionHandler(problemDetailFactory))
                .build();
    }

    @Test
    void hidesInternalExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("요청을 처리하는 중 오류가 발생했습니다."))
                .andExpect(jsonPath("$.detail").value(not("sensitive message")));
    }

    @Test
    void 초대_수락_중_예상하지_못한_예외도_토큰_대신_경로_패턴을_남긴다(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(UnexpectedExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String body;
        try {
            body = mockMvc.perform(post("/api/v1/invitations/{tokenOrCode}/accept", INVITATION_TOKEN))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.instance").value("/api/v1/invitations/%7BtokenOrCode%7D/accept"))
                    .andReturn().getResponse().getContentAsString();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("uri");
                    assertThat(pair.value).isEqualTo("/api/v1/invitations/{tokenOrCode}/accept");
                })
                .allSatisfy(pair -> assertThat(String.valueOf(pair.value)).doesNotContain(INVITATION_TOKEN));
        assertThat(event.getFormattedMessage())
                .contains("/api/v1/invitations/{tokenOrCode}/accept")
                .doesNotContain(INVITATION_TOKEN);
        assertThat(output.getAll()).doesNotContain(INVITATION_TOKEN);
        assertThat(body).doesNotContain(INVITATION_TOKEN);
    }

    @RestController
    private static class UnexpectedExceptionController {

        @PostMapping("/api/v1/invitations/{tokenOrCode}/accept")
        void throwWhileAccepting(@PathVariable String tokenOrCode) {
            throw new IllegalStateException("unexpected failure");
        }

        @GetMapping("/test/unexpected-exception")
        void throwUnexpectedException() {
            throw new IllegalStateException("sensitive message");
        }
    }
}
