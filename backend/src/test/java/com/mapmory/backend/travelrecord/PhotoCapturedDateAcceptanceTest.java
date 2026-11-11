package com.mapmory.backend.travelrecord;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 앱이 v2 쓰기 API로 보낸 사진 촬영일을 저장해 기록 상세가 돌려주는지 확인한다.
 *
 * <p>촬영일이 서버에 없으면 사진을 고른 기기가 아닌 곳(다른 기기, 재설치, 그룹 멤버)에서 사진이 "날짜 미상"으로 묶인다.
 */
class PhotoCapturedDateAcceptanceTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String accessToken;
    private String firstPhotoKey;
    private String secondPhotoKey;

    @BeforeEach
    void setUp() throws Exception {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willAnswer(invocation -> new ExpiringUrl("https://download.example/" + invocation.getArgument(0), 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
        accessToken = guestAccessToken();
        firstPhotoKey = photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
        secondPhotoKey = photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
    }

    @Test
    void v2로_받은_촬영일을_저장하고_상세에서_돌려준다() throws Exception {
        long recordId = createV2(photo(firstPhotoKey, "2026-08-01"), photo(secondPhotoKey, null));

        expectCapturedDates(recordId, "2026-08-01", null);
    }

    @Test
    void v1로_수정해도_저장된_촬영일은_남는다() throws Exception {
        long recordId = createV2(photo(firstPhotoKey, "2026-08-01"), photo(secondPhotoKey, null));

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"objectKeys\": [\"%s\", \"%s\"]".formatted(firstPhotoKey, secondPhotoKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media[0].capturedDate").value("2026-08-01"));
    }

    @Test
    void v2_수정은_사진_순서를_바꾸고_비어_있던_촬영일을_채운다() throws Exception {
        long recordId = createV2(photo(firstPhotoKey, null), photo(secondPhotoKey, null));

        mockMvc.perform(put("/api/v2/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v2Body(photo(secondPhotoKey, "2026-08-02"), photo(firstPhotoKey, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media[0].objectKey").value(secondPhotoKey))
                .andExpect(jsonPath("$.data.media[0].capturedDate").value("2026-08-02"))
                .andExpect(jsonPath("$.data.media[1].capturedDate").doesNotExist());
    }

    @Test
    void 카메라_시계가_틀리거나_형식이_틀린_촬영일이_있어도_기록은_저장한다() throws Exception {
        long recordId = createV2(photo(firstPhotoKey, "2999-01-01"), photo(secondPhotoKey, "2026:08:02"));

        expectCapturedDates(recordId, null, null);
    }

    @Test
    void v2에_사진이_없으면_거절한다() throws Exception {
        mockMvc.perform(post("/api/v2/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"photos\": []")))
                .andExpect(status().isBadRequest());
    }

    private void expectCapturedDates(long recordId, String first, String second) throws Exception {
        var result = mockMvc.perform(get("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        result.andExpect(first == null
                ? jsonPath("$.data.media[0].capturedDate").doesNotExist()
                : jsonPath("$.data.media[0].capturedDate").value(first));
        result.andExpect(second == null
                ? jsonPath("$.data.media[1].capturedDate").doesNotExist()
                : jsonPath("$.data.media[1].capturedDate").value(second));
    }

    private long createV2(String... photos) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v2/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v2Body(photos)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Integer) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String photo(String objectKey, String capturedDate) {
        String date = capturedDate == null ? "null" : "\"" + capturedDate + "\"";
        return "{ \"objectKey\": \"%s\", \"capturedDate\": %s }".formatted(objectKey, date);
    }

    private String v2Body(String... photos) {
        return body("\"photos\": [" + String.join(", ", photos) + "]");
    }

    private String body(String photosField) {
        return """
                {
                  "countryCode": "JP",
                  "title": "도쿄 여행",
                  "startDate": "2026-08-01",
                  "endDate": "2026-08-02",
                  %s
                }
                """.formatted(photosField);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
