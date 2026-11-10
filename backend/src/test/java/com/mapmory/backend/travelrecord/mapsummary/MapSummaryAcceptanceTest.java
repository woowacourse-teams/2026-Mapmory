package com.mapmory.backend.travelrecord.mapsummary;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 지도 전체 요약이 서버 캐시를 거쳐도 기록·태그 변경 직후 바로 바뀌는지 API로 확인한다.
 *
 * <p>요약은 회원별 메모리 캐시에 남는다. 기록·태그 변경 이벤트가 커밋된 뒤 그 회원 캐시를 지우지 않으면
 * 아래 두 번째 조회부터 옛 결과가 나온다.
 */
class MapSummaryAcceptanceTest extends IntegrationTest {

    private static final String SUMMARY_URL = "/api/v1/travel-records/map-summary";
    private static final String SEOUL = "11";
    private static final String JONGNO = "11110";
    private static final String BUSAN = "26";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 기록을_만들고_고치고_지우면_다음_전체_요약에_바로_반영된다() throws Exception {
        String token = guestAccessToken();

        long recordId = createRecord(token, districtBody("종로 여행", null));
        summary(token)
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].code").value("KR"))
                .andExpect(jsonPath("$.data[0].count").value(1))
                .andExpect(jsonPath("$.data[1].code").value(SEOUL))
                .andExpect(jsonPath("$.data[1].parentRegionId").value(regionIdOf(token, "KR")))
                .andExpect(jsonPath("$.data[2].code").value(JONGNO))
                .andExpect(jsonPath("$.data[2].regionType").value("DISTRICT"));

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provinceBody("부산으로 옮긴 여행")))
                .andExpect(status().isOk());
        summary(token)
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].code").value("KR"))
                .andExpect(jsonPath("$.data[1].code").value(BUSAN))
                .andExpect(jsonPath("$.data[1].count").value(1));

        mockMvc.perform(delete("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
        summary(token)
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 태그를_지우면_그_태그로_본_전체_요약은_캐시에서_나오지_않고_TAG_NOT_FOUND가_된다() throws Exception {
        String token = guestAccessToken();
        long tagId = createTag(token, "가족");
        createRecord(token, districtBody("가족과 종로", tagId));
        mockMvc.perform(get(SUMMARY_URL).param("tagId", String.valueOf(tagId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(delete("/api/v1/tags/" + tagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SUMMARY_URL).param("tagId", String.valueOf(tagId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }

    private ResultActions summary(String token) throws Exception {
        return mockMvc.perform(get(SUMMARY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private int regionIdOf(String token, String code) throws Exception {
        String body = mockMvc.perform(get(SUMMARY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data[?(@.code == '" + code + "')].regionId");
        return ids.getFirst();
    }

    private long createRecord(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createTag(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String districtBody(String title, Long tagId) {
        String tagIds = tagId == null ? "[]" : "[" + tagId + "]";
        return """
                {
                  "countryCode": "KR",
                  "provinceCode": "%s",
                  "districtCode": "%s",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "tagIds": %s,
                  "objectKeys": ["%s"]
                }
                """.formatted(SEOUL, JONGNO, title, tagIds, newPhotoKey());
    }

    private String provinceBody(String title) {
        return """
                {
                  "countryCode": "KR",
                  "provinceCode": "%s",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": ["%s"]
                }
                """.formatted(BUSAN, title, newPhotoKey());
    }

    // 기록에는 사진이 1장 이상 있어야 한다. S3 확인은 통과시키고 조회 URL 서명은 대신한다.
    private String newPhotoKey() {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willReturn(new ExpiringUrl("https://download.example/photo.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
        return photoKeyOfLatestMember(UUID.randomUUID() + ".jpg");
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
