package com.mapmory.backend.waitlist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LaunchWaitlistControllerTest {

    @Mock
    private LaunchWaitlistService service;

    @InjectMocks
    private LaunchWaitlistController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 출시_알림을_신청하면_201로_응답한다() throws Exception {
        when(service.subscribe(any())).thenReturn(LaunchWaitlistStatus.SUBSCRIBED);

        mockMvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBSCRIBED"));
    }

    @Test
    void 이미_등록된_이메일은_200으로_응답한다() throws Exception {
        when(service.subscribe(any())).thenReturn(LaunchWaitlistStatus.ALREADY_SUBSCRIBED);

        mockMvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ALREADY_SUBSCRIBED"));
    }

    @Test
    void 동의하지_않으면_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "privacyConsent": false,
                                  "ageConfirmed": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return """
                {
                  "email": "user@example.com",
                  "privacyConsent": true,
                  "ageConfirmed": true
                }
                """;
    }
}
