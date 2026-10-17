package com.mapmory.backend.travelrecord;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 상세 응답의 지역 계층이 트랜잭션 밖에서도 채워지는지 확인한다.
 *
 * <p>open-in-view가 꺼져 있어 컨트롤러 시점에는 영속성 컨텍스트가 닫혀 있다.
 * RegionDetailResponse는 시·군·구 기록에서 Region의 parent·root를 읽는데, 이 둘은 지연 로딩이라
 * 서비스가 미리 초기화해 두지 않으면 LazyInitializationException이 난다.
 * 기존 인수 테스트는 모두 국가 단위(JP) 기록만 다뤄 이 경로를 지나지 않았다.
 * 시·도까지만 고른 기록(부산광역시)도 저장되고 시·도 필터로 찾히는지 함께 확인한다.
 */
class TravelRecordDetailAcceptanceTest extends IntegrationTest {

    private static final String SEOUL_PROVINCE_CODE = "11";
    private static final String JONGNO_DISTRICT_CODE = "11110";
    private static final String BUSAN_PROVINCE_CODE = "26";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 시군구_기록의_상세_조회는_국가_시도_시군구를_모두_응답한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createDistrictRecord(accessToken);

        mockMvc.perform(get("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region.country.code").value("KR"))
                .andExpect(jsonPath("$.data.region.province.code").value(SEOUL_PROVINCE_CODE))
                .andExpect(jsonPath("$.data.region.district.code").value(JONGNO_DISTRICT_CODE));
    }

    @Test
    void 시군구_기록의_수정_응답도_국가_시도_시군구를_모두_담는다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createDistrictRecord(accessToken);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(districtRecordBody("수정한 종로 여행")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정한 종로 여행"))
                .andExpect(jsonPath("$.data.region.country.code").value("KR"))
                .andExpect(jsonPath("$.data.region.province.code").value(SEOUL_PROVINCE_CODE))
                .andExpect(jsonPath("$.data.region.district.code").value(JONGNO_DISTRICT_CODE));
    }

    @Test
    void 시군구_없이_시도만_고른_부산광역시_기록을_저장하고_시도_필터로_찾는다() throws Exception {
        String accessToken = guestAccessToken();
        MvcResult created = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countryCode": "KR",
                                  "provinceCode": "%s",
                                  "title": "부산 여행",
                                  "content": "기록 본문",
                                  "startDate": "2026-08-01",
                                  "objectKeys": []
                                }
                                """.formatted(BUSAN_PROVINCE_CODE)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer recordId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region.country.code").value("KR"))
                .andExpect(jsonPath("$.data.region.province.code").value(BUSAN_PROVINCE_CODE))
                .andExpect(jsonPath("$.data.region.district").isEmpty());

        mockMvc.perform(get("/api/v1/travel-records")
                        .param("countryCode", "KR")
                        .param("provinceCode", BUSAN_PROVINCE_CODE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(recordId));
    }

    @Test
    void 목록_지역_필터_코드_형식이_틀리면_400으로_거부한다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(get("/api/v1/travel-records")
                        .param("countryCode", "kr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/travel-records")
                        .param("countryCode", "KR")
                        .param("provinceCode", " ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 목록_지역_필터에_상위_지역_없이_하위_지역만_오면_400으로_거부한다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(get("/api/v1/travel-records")
                        .param("provinceCode", BUSAN_PROVINCE_CODE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGION_HIERARCHY"));
    }

    private long createDistrictRecord(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(districtRecordBody("종로 여행")))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String districtRecordBody(String title) {
        return """
                {
                  "countryCode": "KR",
                  "provinceCode": "%s",
                  "districtCode": "%s",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": []
                }
                """.formatted(SEOUL_PROVINCE_CODE, JONGNO_DISTRICT_CODE, title);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
