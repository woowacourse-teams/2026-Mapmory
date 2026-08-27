package com.mapmory.backend.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.upload.storage.UploadedObjectChecker;
import com.mapmory.backend.upload.storage.UploadedObjectDeleter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class UploadedObjectCleanupAcceptanceTest extends IntegrationTest {

    private static final String MEDIA_A = "mapmory/travel-records/1/a.jpg";
    private static final String MEDIA_B = "mapmory/travel-records/1/b.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadedObjectChecker uploadedObjectChecker;

    @MockitoBean
    private UploadedObjectDeleter uploadedObjectDeleter;

    @BeforeEach
    void setUp() {
        given(uploadedObjectChecker.exists(anyString())).willReturn(true);
    }

    @Test
    void 기록_수정이_커밋되면_빠진_이미지만_S3에서_삭제한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, MEDIA_A, MEDIA_B);
        clearInvocations(uploadedObjectDeleter);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(MEDIA_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKeys[0]").value(MEDIA_B));

        verify(uploadedObjectDeleter).deleteAll(List.of(MEDIA_A));
    }

    @Test
    void 기록_삭제가_커밋되면_연결된_이미지를_모두_S3에서_삭제한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, MEDIA_A, MEDIA_B);
        clearInvocations(uploadedObjectDeleter);

        mockMvc.perform(delete("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(uploadedObjectDeleter).deleteAll(List.of(MEDIA_A, MEDIA_B));
    }

    @Test
    void S3_삭제가_실패해도_이미_커밋된_기록_수정은_성공한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, MEDIA_A);
        clearInvocations(uploadedObjectDeleter);
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(uploadedObjectDeleter).deleteAll(List.of(MEDIA_A));

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKeys.length()").value(0));

        verify(uploadedObjectDeleter).deleteAll(List.of(MEDIA_A));
    }

    private long createRecord(String accessToken, String... objectKeys) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(objectKeys)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String recordBody(String... objectKeys) {
        String keys = Arrays.stream(objectKeys)
                .map("\"%s\""::formatted)
                .collect(Collectors.joining(", "));
        return """
                {
                  "countryCode": "JP",
                  "title": "도쿄 여행",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": [%s]
                }
                """.formatted(keys);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
