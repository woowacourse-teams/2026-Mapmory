package com.mapmory.backend.upload;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import com.mapmory.backend.upload.storage.UploadedObjectChecker;
import java.util.Arrays;
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

/**
 * 업로드된 객체의 실존 검증 인수 테스트. (ADR 0016)
 *
 * 이미지는 앱이 presigned URL로 S3에 직접 올리므로, 서버는 기록 저장 요청이 도착하기 전까지
 * 업로드 성공 여부를 모른다. "올라오지 않은 사진이 기록에 붙어 영구히 깨져 보이는 일이 없다"를
 * 사용자 관점에서 검증한다.
 *
 * S3는 외부 시스템이므로 대역으로 대체하고, 서버가 검증을 거는 지점과 그 결과만 본다.
 */
@AutoConfigureMockMvc
class UploadedObjectVerificationIntegrationTest extends IntegrationTest {

    private static final String UPLOADED_KEY = "travel-records/1/uploaded.jpg";
    private static final String ANOTHER_UPLOADED_KEY = "travel-records/1/uploaded-2.jpg";
    private static final String MISSING_KEY = "travel-records/1/missing.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadedObjectChecker uploadedObjectChecker;

    @BeforeEach
    void setUp() {
        given(uploadedObjectChecker.exists(anyString())).willReturn(true);
        given(uploadedObjectChecker.exists(MISSING_KEY)).willReturn(false);
    }

    @Test
    void 업로드된_사진은_기록에_붙는다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", UPLOADED_KEY)))
                .andExpect(status().isCreated());

        verify(uploadedObjectChecker).exists(UPLOADED_KEY);
    }

    @Test
    void 올라오지_않은_사진을_붙이려_하면_거절된다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", MISSING_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"));
    }

    @Test
    void 한_장이라도_올라오지_않았으면_기록_전체가_저장되지_않는다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", UPLOADED_KEY, MISSING_KEY)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 사진이_없는_기록은_검증_없이_저장된다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("사진 없는 기록")))
                .andExpect(status().isCreated());

        verify(uploadedObjectChecker, never()).exists(anyString());
    }

    @Test
    void 수정으로_새로_추가되는_사진도_검증된다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, UPLOADED_KEY);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", UPLOADED_KEY, MISSING_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"));
    }

    @Test
    void 수정에서_이미_붙어있던_사진은_다시_확인하지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, UPLOADED_KEY);
        // 생성 때 한 번 확인했으므로, 수정 시점의 호출만 세기 위해 기록을 지운다
        clearInvocations();

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", UPLOADED_KEY, ANOTHER_UPLOADED_KEY)))
                .andExpect(status().isOk());

        verify(uploadedObjectChecker).exists(ANOTHER_UPLOADED_KEY);
        verify(uploadedObjectChecker, never()).exists(eq(UPLOADED_KEY));
    }

    @Test
    void 스토리지_확인이_실패하면_503으로_응답한다() throws Exception {
        String accessToken = guestAccessToken();
        willThrow(new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE))
                .given(uploadedObjectChecker).exists(UPLOADED_KEY);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", UPLOADED_KEY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    }

    private void clearInvocations() {
        org.mockito.Mockito.clearInvocations(uploadedObjectChecker);
    }

    private long createRecord(String accessToken, String... objectKeys) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주도 여행", objectKeys)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String recordBody(String title, String... objectKeys) {
        String keys = Arrays.stream(objectKeys)
                .map("\"%s\""::formatted)
                .collect(Collectors.joining(", "));
        return """
                {
                  "countryCode": "KR",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": [%s]
                }
                """.formatted(title, keys);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
