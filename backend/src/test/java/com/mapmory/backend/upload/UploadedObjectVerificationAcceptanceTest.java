package com.mapmory.backend.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
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

    private static final String MISSING = "missing.jpg";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willReturn(false).given(uploadedObjectChecker).exists(endsWith("/" + MISSING));
        willReturn(new ExpiringUrl("https://download.example/image.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void 업로드된_사진은_기록에_붙는다() throws Exception {
        String accessToken = guestAccessToken();
        String uploadedKey = photoKeyOfLatestMember("uploaded.jpg");

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(uploadedKey)))
                .andExpect(status().isCreated());

        verify(uploadedObjectChecker).exists(uploadedKey);
    }

    @Test
    void 올라오지_않은_사진을_붙이려_하면_기록_전체가_저장되지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        String uploadedKey = photoKeyOfLatestMember("uploaded.jpg");
        String missingKey = photoKeyOfLatestMember(MISSING);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(uploadedKey, missingKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"));

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 사진이_없는_기록은_저장소를_확인하지_않고_거절한다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_REQUIRED"));

        verify(uploadedObjectChecker, never()).exists(anyString());
    }

    @Test
    void 수정에서는_새로_추가한_사진만_확인한다() throws Exception {
        String accessToken = guestAccessToken();
        String uploadedKey = photoKeyOfLatestMember("uploaded.jpg");
        String anotherUploadedKey = photoKeyOfLatestMember("uploaded-2.jpg");
        long recordId = createRecord(accessToken, uploadedKey);
        clearInvocations(uploadedObjectChecker);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(uploadedKey, anotherUploadedKey)))
                .andExpect(status().isOk());

        verify(uploadedObjectChecker).exists(anotherUploadedKey);
        verify(uploadedObjectChecker, never()).exists(eq(uploadedKey));
    }

    @Test
    void 저장소를_확인할_수_없으면_재시도할_수_있도록_503을_응답한다() throws Exception {
        String accessToken = guestAccessToken();
        String uploadedKey = photoKeyOfLatestMember("uploaded.jpg");
        willThrow(new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE))
                .given(uploadedObjectChecker).exists(uploadedKey);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(uploadedKey)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void 다른_회원_경로의_키는_저장소를_확인하지_않고_업로드되지_않은_사진으로_거절한다() throws Exception {
        String accessToken = guestAccessToken();
        String ownKey = photoKeyOfLatestMember("uploaded.jpg");
        String otherMembersKey = ownKey.replaceFirst("/(\\d+)/uploaded\\.jpg$", "/999999/uploaded.jpg");

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(ownKey, otherMembersKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"));

        verify(uploadedObjectChecker, never()).exists(anyString());
    }

    @Test
    void 이미_다른_기록에_붙은_키로_기록을_만들면_400으로_거절한다() throws Exception {
        String accessToken = guestAccessToken();
        String uploadedKey = photoKeyOfLatestMember("uploaded.jpg");
        createRecord(accessToken, uploadedKey);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(uploadedKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OBJECT_KEY"));
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
