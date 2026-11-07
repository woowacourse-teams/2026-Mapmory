package com.mapmory.backend.upload;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 한 회원이 발급받을 수 있는 업로드 URL의 양을 제한한다. (#481)
 */
class UploadRateLimitIntegrationTest extends IntegrationTest {

    private static final String HUNDRED_FILES = IntStream.range(0, 100)
            .mapToObj(index -> "{ \"fileName\": \"%d.jpg\", \"contentType\": \"image/jpeg\", \"fileSize\": 1024 }"
                    .formatted(index))
            .collect(Collectors.joining(",", "{ \"files\": [", "] }"));

    @Autowired
    private MockMvc mockMvc;

    // 운영 설정은 시간당 300장이다. 요청 횟수가 아니라 파일 수를 세므로 100장 요청은 세 번까지 된다.
    @Test
    void 시간당_파일_수_한도를_넘으면_429로_거절한다() throws Exception {
        willReturn(new PresignedRequest(URI.create("https://upload.example/signed"), "PUT", Duration.ofMinutes(5)))
                .given(presignedUrlProvider).createPresignedPutUrl(anyString(), anyString(), anyLong());
        String accessToken = accessToken();

        for (int request = 0; request < 3; request++) {
            requestHundredFiles(accessToken).andExpect(status().isOk());
        }

        requestHundredFiles(accessToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UPLOAD_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds", allOf(greaterThan(0), lessThanOrEqualTo(3600))));
    }

    private ResultActions requestHundredFiles(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(HUNDRED_FILES));
    }

    private String accessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
