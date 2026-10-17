package com.mapmory.backend.upload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 업로드 키 prefix 인수 테스트.
 *
 * 운영 버킷은 여러 팀이 함께 쓰는 공용 버킷이라, 우리 객체는 서비스 prefix 아래에만 놓여야 한다.
 * S3 호출은 외부 시스템이므로 대역으로 대체하고, 서버가 발급하는 objectKey만 검증한다.
 */
class UploadKeyPrefixIntegrationTest extends IntegrationTest {

    private static final String REQUEST_BODY = """
            {
              "files": [
                { "fileName": "jeju.jpg", "contentType": "image/jpeg", "fileSize": 1024 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 발급된_objectKey는_서비스_prefix_아래에_있다() throws Exception {
        willReturn(URI.create("https://bucket.s3.ap-northeast-2.amazonaws.com/signed"))
                .given(presignedUrlProvider).createPresignedPutUrl(anyString(), anyString(), anyLong(), any());

        mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploads[0].objectKey")
                        .value(org.hamcrest.Matchers.startsWith("mapmory/travel-records/")));
    }

    private String accessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
