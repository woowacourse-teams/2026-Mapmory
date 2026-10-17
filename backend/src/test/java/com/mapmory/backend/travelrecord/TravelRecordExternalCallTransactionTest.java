package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 기록을 저장·수정할 때 외부 호출(Google 장소 조회, S3 업로드 확인)이 DB 트랜잭션 밖에서 일어나는지 확인한다.
 *
 * <p>트랜잭션 안에서 외부를 부르면 응답을 기다리는 동안 커넥션을 쥐고 있다. 부하 테스트에서 Google이 멈추자
 * 커넥션 풀이 고갈돼 기록과 무관한 API까지 실패했다.
 */
class TravelRecordExternalCallTransactionTest extends IntegrationTest {

    private static final String GOOGLE_PLACE_ID = "ChIJTokyoTower";
    private static final String PHOTO_A = "mapmory/travel-records/1/a.jpg";
    private static final String PHOTO_B = "mapmory/travel-records/1/b.jpg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TravelRecordService travelRecordService;

    @Autowired
    private MemberRepository memberRepository;

    private final List<Boolean> transactionActiveAtExternalCall = new ArrayList<>();

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> {
            recordTransactionState();
            return new PlaceDetails(GOOGLE_PLACE_ID, null, "JP", null, null, List.of());
        }).given(googlePlacesClient).findById(GOOGLE_PLACE_ID);
        willReturn("GOOGLE").given(googlePlacesClient).providerCode();
        willAnswer(invocation -> {
            recordTransactionState();
            return true;
        }).given(uploadedObjectChecker).exists(anyString());
        willReturn(new ExpiringUrl("https://download.example/image.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void 기록을_저장할_때_외부_호출은_트랜잭션_밖에서_한다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(PHOTO_A)))
                .andExpect(status().isCreated());

        assertThat(transactionActiveAtExternalCall).containsExactly(false, false);
    }

    @Test
    void 기록을_수정할_때_외부_호출은_트랜잭션_밖에서_한다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, PHOTO_A);
        transactionActiveAtExternalCall.clear();

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(PHOTO_A, PHOTO_B)))
                .andExpect(status().isOk());

        // Google 조회 1번, 새로 추가된 사진(B) 확인 1번
        assertThat(transactionActiveAtExternalCall).containsExactly(false, false);
    }

    @Test
    void 사진을_확인하는_사이_기록이_삭제되면_수정은_TRAVEL_RECORD_NOT_FOUND로_끝난다() throws Exception {
        String accessToken = guestAccessToken();
        long recordId = createRecord(accessToken, PHOTO_A);
        willAnswer(invocation -> {
            // 다른 기기에서 같은 기록을 지운 상황
            travelRecordService.delete(memberRepository.findAll().getFirst(), recordId);
            return true;
        }).given(uploadedObjectChecker).exists(PHOTO_B);

        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(PHOTO_A, PHOTO_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_RECORD_NOT_FOUND"));
    }

    private void recordTransactionState() {
        transactionActiveAtExternalCall.add(TransactionSynchronizationManager.isActualTransactionActive());
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

    // 지역 없이 장소만 보내 서버가 Google 조회로 지역을 정하는 경로를 탄다. 지역을 보내면 Google을 부르지 않는다.
    private String recordBody(String... objectKeys) {
        String keys = String.join(", ", java.util.Arrays.stream(objectKeys).map("\"%s\""::formatted).toList());
        return """
                {
                  "title": "도쿄 여행",
                  "content": "기록 본문",
                  "startDate": "2026-08-01",
                  "objectKeys": [%s],
                  "placeId": "%s"
                }
                """.formatted(keys, GOOGLE_PLACE_ID);
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
