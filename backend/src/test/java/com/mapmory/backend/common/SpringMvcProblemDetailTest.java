package com.mapmory.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 이 테스트는 Spring MVC 기본 405 처리만 검증한다. 시큐리티 도입 후 슬라이스가
// auth 패키지의 Filter/WebMvcConfigurer 빈을 끌어와 컨텍스트가 깨지므로,
// auth 빈을 슬라이스에서 제외하고 시큐리티 필터도 끈다.
@WebMvcTest(
        controllers = SpringMvcProblemDetailTest.MethodRestrictedController.class,
        properties = "spring.mvc.problemdetails.enabled=true",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.mapmory\\.backend\\.auth\\..*")
)
@AutoConfigureMockMvc(addFilters = false)
@Import({ProblemDetailFactory.class, SpringMvcProblemDetailTest.MethodRestrictedController.class})
@DisplayName("Spring MVC 기본 Problem Details")
class SpringMvcProblemDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 Problem Details로 반환한다")
    void returnsProblemDetailWhenHttpMethodIsNotSupported() throws Exception {
        mockMvc.perform(post("/test/method-restricted"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                // 매칭된 핸들러가 없어 경로 패턴이 없다. Spring이 채운 원본 경로 대신 UNKNOWN이 나간다(ProblemDetailInstanceAdvice).
                .andExpect(jsonPath("$.instance").value("UNKNOWN"));
    }

    @RestController
    static class MethodRestrictedController {

        @GetMapping("/test/method-restricted")
        void get() {
        }
    }
}
