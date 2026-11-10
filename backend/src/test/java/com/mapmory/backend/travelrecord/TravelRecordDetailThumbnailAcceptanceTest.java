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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 기록 상세와 수정 응답이 사진마다 그리드용 썸네일 주소와 뷰어용 원본 주소를 함께 주는지 확인한다(#662).
 *
 * <p>썸네일은 워커가 나중에 만들므로 한 기록 안에서도 사진마다 있을 수도, 없을 수도 있다.
 * 없는 사진은 썸네일 주소로 원본 주소를 준다. 목록, 앨범과 같은 규칙이다.
 * 조회 URL 서명은 키를 그대로 담은 주소로 대신해, 어떤 키로 서명했는지 응답에서 보이게 한다.
 */
class TravelRecordDetailThumbnailAcceptanceTest extends IntegrationTest {

    private static final String SIGNED_URL_PREFIX = "https://download.example/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;
    private String firstPhotoKey;
    private String secondPhotoKey;
    private String firstThumbnailKey;
    private long recordId;

    @BeforeEach
    void createRecordWhoseFirstPhotoHasThumbnail() throws Exception {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willAnswer(invocation -> new ExpiringUrl(SIGNED_URL_PREFIX + invocation.getArgument(0), 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());

        accessToken = guestAccessToken();
        firstPhotoKey = photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
        secondPhotoKey = photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
        firstThumbnailKey = "derived/thumb/" + UUID.randomUUID() + ".webp";

        MvcResult created = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("제주 여행")))
                .andExpect(status().isCreated())
                .andReturn();
        recordId = ((Integer) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();

        jdbcTemplate.update(
                "UPDATE record_media SET thumb_key = ? WHERE travel_record_id = ? AND sort_order = 0",
                firstThumbnailKey, recordId);
    }

    @Test
    void 상세_조회는_사진마다_썸네일_주소와_원본_주소를_함께_준다() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        expectThumbnailOnlyOnFirstPhoto(result);
    }

    @Test
    void 수정_응답도_사진마다_썸네일_주소와_원본_주소를_함께_준다() throws Exception {
        ResultActions result = mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody("수정한 제주 여행")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정한 제주 여행"));

        expectThumbnailOnlyOnFirstPhoto(result);
    }

    private void expectThumbnailOnlyOnFirstPhoto(ResultActions result) throws Exception {
        result
                .andExpect(jsonPath("$.data.media.length()").value(2))
                .andExpect(jsonPath("$.data.media[0].objectKey").value(firstPhotoKey))
                .andExpect(jsonPath("$.data.media[0].viewUrl").value(SIGNED_URL_PREFIX + firstPhotoKey))
                .andExpect(jsonPath("$.data.media[0].thumbnailUrl").value(SIGNED_URL_PREFIX + firstThumbnailKey))
                .andExpect(jsonPath("$.data.media[0].thumbnailUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data.media[1].objectKey").value(secondPhotoKey))
                .andExpect(jsonPath("$.data.media[1].viewUrl").value(SIGNED_URL_PREFIX + secondPhotoKey))
                .andExpect(jsonPath("$.data.media[1].thumbnailUrl").value(SIGNED_URL_PREFIX + secondPhotoKey))
                .andExpect(jsonPath("$.data.media[1].thumbnailUrlExpiresIn").value(300))
                .andExpect(jsonPath("$.data.objectKeys[0]").value(firstPhotoKey))
                .andExpect(jsonPath("$.data.objectKeys[1]").value(secondPhotoKey));
    }

    private String recordBody(String title) {
        return """
                {
                  "countryCode": "JP",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": ["%s", "%s"]
                }
                """.formatted(title, firstPhotoKey, secondPhotoKey);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
