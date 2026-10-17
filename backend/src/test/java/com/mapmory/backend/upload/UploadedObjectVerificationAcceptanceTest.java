package com.mapmory.backend.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class UploadedObjectVerificationAcceptanceTest extends IntegrationTest {

    private static final String UPLOADED_KEY = "mapmory/travel-records/1/uploaded.jpg";
    private static final String ANOTHER_UPLOADED_KEY = "mapmory/travel-records/1/uploaded-2.jpg";
    private static final String MISSING_KEY = "mapmory/travel-records/1/missing.jpg";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willReturn(false).given(uploadedObjectChecker).exists(MISSING_KEY);
        willReturn(new ExpiringUrl("https://download.example/image.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void 업로드된_사진은_기록에_붙는다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(UPLOADED_KEY)))
                .andExpect(status().isCreated());

        verify(uploadedObjectChecker).exists(UPLOADED_KEY);
    }

    @Test
    void 올라오지_않은_사진을_붙이려_하면_기록_전체가_저장되지_않는다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(UPLOADED_KEY, MISSING_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"));

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 사진이_없는_기록은_저장소를_확인하지_않고_저장된다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody()))
                .andExpect(status().isCreated());

        verify(uploadedObjectChecker, never()).exists(anyString());
    }

    @Test
    void 수정에서는_새로_추가한_사진만_확인한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, UPLOADED_KEY);
        clearInvocations(uploadedObjectChecker);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(UPLOADED_KEY, ANOTHER_UPLOADED_KEY)))
                .andExpect(status().isOk());

        verify(uploadedObjectChecker).exists(ANOTHER_UPLOADED_KEY);
        verify(uploadedObjectChecker, never()).exists(eq(UPLOADED_KEY));
    }

    @Test
    void 저장소를_확인할_수_없으면_재시도할_수_있도록_503을_응답한다() throws Exception {
        String accessToken = guestAccessToken();
        willThrow(new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE))
                .given(uploadedObjectChecker).exists(UPLOADED_KEY);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(UPLOADED_KEY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
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
