package com.mapmory.backend.travelrecord;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 지역을 함께 보낸 기록 저장·수정에서 Google 장소를 붙일 때 Google 상세 조회를 하지 않는지 확인한다.
 *
 * <p>장소를 고를 때(select) 이미 상세 조회를 했다. 지역이 정해져 있으면 저장 때 다시 조회해도 쓸 값이 국가 코드뿐인데,
 * Google 이름은 저장하지 않고 출처 표기는 상수라 Google 없이 끝낼 수 있다. Google 주소는 IntegrationTest가
 * 닿지 않는 곳으로 둬서, 조회를 시도하면 PLACE_PROVIDER_UNAVAILABLE로 실패한다.
 */
class TravelRecordGooglePlaceTest extends IntegrationTest {

    private static final String GOOGLE_PLACE_ID = "ChIJTokyoTower";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willReturn(new ExpiringUrl("https://download.example/image.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void Google_장소로_기록을_저장할_때_Google_상세_조회를_하지_않는다() throws Exception {
        String accessToken = guestAccessToken();

        long recordId = createRecord(accessToken);

        verify(googleRequestLimiter, never()).reserveDetails();
        mockMvc.perform(get("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeId").value(GOOGLE_PLACE_ID))
                .andExpect(jsonPath("$.data.placeName").isEmpty())
                .andExpect(jsonPath("$.data.placeAttribution").value("Google Maps"));
    }

    @Test
    void Google_장소로_기록을_수정할_때_Google_상세_조회를_하지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("JP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeId").value(GOOGLE_PLACE_ID));

        verify(googleRequestLimiter, never()).reserveDetails();
    }

    private long createRecord(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("JP")))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String recordBody(String countryCode) {
        return """
                {
                  "countryCode": "%s",
                  "title": "도쿄 여행",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": ["mapmory/travel-records/1/a.jpg"],
                  "placeId": "%s"
                }
                """.formatted(countryCode, GOOGLE_PLACE_ID);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
