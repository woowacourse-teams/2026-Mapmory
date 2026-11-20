package com.mapmory.backend.common.handler;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.AcceptedLockConflictException;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class LockConflictExceptionHandlerTest {

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(LockConflictExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private MockMvc mockMvc;

    // 모든 예외를 받는 UnexpectedExceptionHandler와 함께 등록해, 잠금 충돌을 500이 아니라 이 처리기가 받는지 본다.
    @BeforeEach
    void setUp() {
        ProblemDetailFactory problemDetailFactory = new ProblemDetailFactory();
        mockMvc = MockMvcBuilders.standaloneSetup(new LockConflictController())
                .setControllerAdvice(
                        new UnexpectedExceptionHandler(problemDetailFactory),
                        new LockConflictExceptionHandler(problemDetailFactory))
                .build();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
    }

    @Test
    void 데드락이면_다시_시도하라고_503과_Retry_After를_준다() throws Exception {
        mockMvc.perform(get("/test/deadlock"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("RETRY_LATER"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1));
    }

    // ADR 0028은 데드락을 대개 잠금 순서를 어긴 버그로 본다. 받아들인 경합이 아니면 알람이 가도록 ERROR로 남긴다.
    @Test
    void 받아들인_경합이_아닌_잠금_충돌은_ERROR로_남긴다() throws Exception {
        mockMvc.perform(get("/test/deadlock")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/test/stale-write")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/test/lock-wait-timeout")).andExpect(status().isServiceUnavailable());

        assertThat(logs.list)
                .extracting(event -> field(event, "event") + ":" + field(event, "accepted") + ":" + event.getLevel())
                .containsExactly("DEADLOCK:false:ERROR", "STALE_WRITE:false:ERROR", "LOCK_WAIT_TIMEOUT:false:ERROR");
    }

    @Test
    void 받아들인_경합은_같은_503으로_응답하고_WARN으로_남긴다() throws Exception {
        mockMvc.perform(get("/test/accepted-deadlock"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("RETRY_LATER"));

        assertThat(logs.list)
                .extracting(event -> field(event, "event") + ":" + field(event, "decision") + ":" + event.getLevel())
                .containsExactly("DEADLOCK:#753:" + Level.WARN);
    }

    @Test
    void 잠금_충돌이_아닌_예외는_그대로_500이다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError());
    }

    @RestController
    private static class LockConflictController {

        @GetMapping("/test/deadlock")
        void deadlock() {
            throw deadlockFailure();
        }

        @GetMapping("/test/accepted-deadlock")
        void acceptedDeadlock() {
            throw new AcceptedLockConflictException("#753", deadlockFailure());
        }

        @GetMapping("/test/stale-write")
        void staleWrite() {
            throw new ObjectOptimisticLockingFailureException("delete from travel_record_tag where id=?", null);
        }

        @GetMapping("/test/lock-wait-timeout")
        void lockWaitTimeout() {
            throw new CannotAcquireLockException("could not execute statement",
                    new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205));
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("unexpected");
        }

        private static CannotAcquireLockException deadlockFailure() {
            return new CannotAcquireLockException("could not execute statement",
                    new SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001", 1213));
        }
    }
}
