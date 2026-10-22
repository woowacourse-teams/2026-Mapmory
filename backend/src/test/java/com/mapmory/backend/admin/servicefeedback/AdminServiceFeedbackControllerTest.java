package com.mapmory.backend.admin.servicefeedback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.token.jwt.JwtConfig;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.servicefeedback.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminServiceFeedbackController.class, ServiceFeedbackController.class})
@Import({JwtConfig.class, JwtProvider.class, ProblemDetailFactory.class,
        com.mapmory.backend.auth.security.config.SecurityConfig.class,
        com.mapmory.backend.auth.security.config.SecurityContextRepositoryConfig.class,
        com.mapmory.backend.auth.security.filter.JwtAuthenticationFilter.class,
        com.mapmory.backend.auth.security.handler.RestAuthenticationEntryPoint.class,
        com.mapmory.backend.auth.security.handler.RestAccessDeniedHandler.class,
        AdminServiceFeedbackControllerTest.HandlerConfig.class})
class AdminServiceFeedbackControllerTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.context.annotation.ComponentScan("com.mapmory.backend.auth.security.handler")
    static class HandlerConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<com.mapmory.backend.auth.security.filter.JwtAuthenticationFilter> jwtRegistration(
                com.mapmory.backend.auth.security.filter.JwtAuthenticationFilter filter
        ) {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @MockitoBean private EntityManager entityManager;
    @MockitoBean private ServiceFeedbackService service;

    @Test
    void 관리자_인증_없이는_상세를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/service-feedback/10")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 관리자_권한으로_상세를_조회한다() throws Exception {
        when(service.getFeedbackDetail(10L)).thenReturn(response(ServiceFeedbackStatus.UNCONFIRMED));
        mockMvc.perform(get("/api/v1/admin/service-feedback/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.feedbackId").value(10))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-14T01:00:00"))
                .andExpect(jsonPath("$.data.member").doesNotExist());
    }

    @Test
    void 필터와_페이지를_적용한_목록_형식을_반환한다() throws Exception {
        when(service.getFeedback(ServiceFeedbackStatus.UNCONFIRMED, ServiceFeedbackCategory.BUG_REPORT,
                ServiceFeedbackPlatform.IOS, null, null, 1, 2))
                .thenReturn(new AdminServiceFeedbackListResponse(List.of(response(ServiceFeedbackStatus.UNCONFIRMED)), 1, 2, 3, 2));
        mockMvc.perform(get("/api/v1/admin/service-feedback").with(user("admin").roles("ADMIN"))
                        .param("status", "UNCONFIRMED").param("category", "BUG_REPORT")
                        .param("platform", "IOS").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.feedback[0].feedbackId").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "status=UNKNOWN", "category=UNKNOWN", "platform=UNKNOWN"})
    void 잘못된_목록_파라미터를_거부한다(String query) throws Exception {
        mockMvc.perform(get("/api/v1/admin/service-feedback?" + query).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void CSRF_토큰_없이는_상태를_변경할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/service-feedback/10/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 일반_사용자는_상태를_변경할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/service-feedback/10/status").with(user("member").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}", "{"})
    void 잘못된_상태_요청을_거부한다(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/service-feedback/10/status").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 관리자와_CSRF_토큰으로_상태를_변경한다() throws Exception {
        when(service.changeStatus(10L, ServiceFeedbackStatus.COMPLETED)).thenReturn(response(ServiceFeedbackStatus.COMPLETED));
        mockMvc.perform(patch("/api/v1/admin/service-feedback/10/status").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void 없는_의견은_404를_반환한다() throws Exception {
        when(service.getFeedbackDetail(99L)).thenThrow(new BusinessException(ServiceFeedbackErrorCode.SERVICE_FEEDBACK_NOT_FOUND));
        mockMvc.perform(get("/api/v1/admin/service-feedback/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SERVICE_FEEDBACK_NOT_FOUND"));
    }

    @Test
    void 앱_JWT로_제출하면_접수_ID와_시각을_반환한다() throws Exception {
        when(entityManager.getReference(Member.class, 1L)).thenReturn(Member.of("회원", UUID.randomUUID()));
        when(service.submit(any(Member.class), any(ServiceFeedbackRequest.class)))
                .thenReturn(new ServiceFeedbackSubmitResponse(10L, LocalDateTime.of(2026, 10, 14, 1, 0)));
        mockMvc.perform(post("/api/v1/service-feedback").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issueAccessToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BUG_REPORT\",\"content\":\"앱이 종료돼요\",\"platform\":\"IOS\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.feedbackId").value(10))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-14T01:00:00"));
    }

    private ServiceFeedbackResponse response(ServiceFeedbackStatus status) {
        return new ServiceFeedbackResponse(10L, ServiceFeedbackCategory.BUG_REPORT, "앱이 종료돼요", ServiceFeedbackPlatform.IOS,
                status, LocalDateTime.of(2026, 10, 14, 1, 0));
    }
}
