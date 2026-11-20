package com.mapmory.backend.common.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.common.ProblemDetailFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class LockConflictExceptionHandlerTest {

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

    @Test
    void 읽은_뒤_바뀐_행을_고치다_실패해도_503과_Retry_After를_준다() throws Exception {
        mockMvc.perform(get("/test/stale-write"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("RETRY_LATER"));
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
            throw new CannotAcquireLockException("Deadlock found when trying to get lock");
        }

        @GetMapping("/test/stale-write")
        void staleWrite() {
            throw new ObjectOptimisticLockingFailureException("delete from travel_record_tag where id=?", null);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("unexpected");
        }
    }
}
