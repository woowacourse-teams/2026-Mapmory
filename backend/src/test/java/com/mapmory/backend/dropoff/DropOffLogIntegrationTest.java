package com.mapmory.backend.dropoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.dropoff.DropOffLog.Kind;
import com.mapmory.backend.dropoff.DropOffLog.Outcome;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class DropOffLogIntegrationTest extends IntegrationTest {

    private static final PlaceProvider GOOGLE =
            new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");
    private static final String TOKEN = "0b6f8f4e-5d0a-4c9a-9f43-0c1c2f5f2b11";
    private static final String IOS = "Mapmory/1.2.7 CFNetwork/1568 Darwin/24.0.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DropOffLogRepository repository;

    @Test
    void 결과가_0개인_검색은_검색어와_앱과_함께_남긴다() throws Exception {
        willReturn(List.of()).given(googlePlacesClient).search(any(), anyString());

        search(guestAccessToken(), " 대천해수욕장 ").andExpect(status().isOk());

        DropOffLog log = onlyLog();
        assertThat(log.getMemberId()).isNotNull();
        assertThat(log.getKind()).isEqualTo(Kind.SEARCH);
        assertThat(log.getOutcome()).isEqualTo(Outcome.NO_RESULTS);
        assertThat(log.getQuery()).isEqualTo("대천해수욕장");
        assertThat(log.getResultCount()).isZero();
        assertThat(log.getSessionToken()).isEqualTo(TOKEN);
        assertThat(log.getUserAgent()).isEqualTo(IOS);
    }

    @Test
    void 후보가_있는_검색은_후보_수를_남긴다() throws Exception {
        willReturn(List.of(new PlaceCandidate(PlaceId.from("ChIJ-1"), "경복궁", "서울 종로구", GOOGLE),
                new PlaceCandidate(PlaceId.from("ChIJ-2"), "경복궁역", "서울 종로구", GOOGLE)))
                .given(googlePlacesClient).search(any(), anyString());

        search(guestAccessToken(), "경복").andExpect(status().isOk());

        assertThat(onlyLog().getOutcome()).isEqualTo(Outcome.RESULTS);
        assertThat(onlyLog().getResultCount()).isEqualTo(2);
    }

    @Test
    void 지역을_찾은_선택은_국가와_지역_코드를_남긴다() throws Exception {
        givenDetails(new RegionAddress("KR", "서울특별시", List.of("종로구")));

        select(guestAccessToken()).andExpect(status().isOk());

        DropOffLog log = onlyLog();
        assertThat(log.getKind()).isEqualTo(Kind.SELECT);
        assertThat(log.getOutcome()).isEqualTo(Outcome.REGION_FOUND);
        assertThat(log.getPlaceId()).isEqualTo("ChIJ-place");
        assertThat(log.getCountryCode()).isEqualTo("KR");
        assertThat(log.getRegionCode()).isEqualTo("11110");
    }

    @Test
    void 국가는_알지만_지역을_못_찾은_선택은_지역_없이_남긴다() throws Exception {
        givenDetails(new RegionAddress("KR", null, List.of("어디에도없는구")));

        select(guestAccessToken()).andExpect(status().isOk());

        DropOffLog log = onlyLog();
        assertThat(log.getOutcome()).isEqualTo(Outcome.REGION_NOT_FOUND);
        assertThat(log.getCountryCode()).isEqualTo("KR");
        assertThat(log.getRegionCode()).isNull();
    }

    @Test
    void 국가도_모르는_선택은_국가_없이_남긴다() throws Exception {
        givenDetails(RegionAddress.UNKNOWN);

        select(guestAccessToken()).andExpect(status().isOk());

        assertThat(onlyLog().getOutcome()).isEqualTo(Outcome.COUNTRY_UNKNOWN);
        assertThat(onlyLog().getCountryCode()).isNull();
    }

    @Test
    void 장소_제공자가_실패해도_오류_코드를_남기고_같은_오류로_응답한다() throws Exception {
        willThrow(new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE))
                .given(googlePlacesClient).findDetails(any(), any());

        select(guestAccessToken()).andExpect(status().isServiceUnavailable());

        DropOffLog log = onlyLog();
        assertThat(log.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(log.getErrorCode()).isEqualTo("PLACE_PROVIDER_UNAVAILABLE");
        assertThat(log.getPlaceId()).isEqualTo("ChIJ-place");
    }

    @Test
    void 예상하지_못한_예외도_실패로_남긴다() throws Exception {
        willThrow(new IllegalStateException("주소 해석 실패"))
                .given(googlePlacesClient).findDetails(any(), any());

        select(guestAccessToken()).andExpect(status().isInternalServerError());

        assertThat(onlyLog().getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(onlyLog().getErrorCode()).isEqualTo("UNEXPECTED");
    }

    @Test
    void 용량을_넘은_사진_업로드는_그_파일의_크기와_형식을_남긴다() throws Exception {
        String accessToken = guestAccessToken();

        upload(accessToken, """
                { "files": [
                  { "fileName": "a.jpg", "contentType": "image/jpeg", "fileSize": 1024 },
                  { "fileName": "b.heic", "contentType": "image/heic", "fileSize": 12582912 }
                ] }""")
                .andExpect(status().isBadRequest());

        DropOffLog log = onlyLog();
        assertThat(log.getKind()).isEqualTo(Kind.UPLOAD);
        assertThat(log.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(log.getErrorCode()).isEqualTo("FILE_SIZE_EXCEEDED");
        assertThat(log.getFileCount()).isEqualTo(2);
        assertThat(log.getFileSize()).isEqualTo(12_582_912L);
        assertThat(log.getContentType()).isEqualTo("image/heic");
        assertThat(log.getUserAgent()).isEqualTo(IOS);
    }

    @Test
    void 받아들인_사진_업로드는_남기지_않는다() throws Exception {
        willReturn(new PresignedRequest(URI.create("https://upload.example/signed"), "PUT", Duration.ofMinutes(5)))
                .given(presignedUrlProvider).createPresignedPutUrl(anyString(), anyString(), anyLong());

        upload(guestAccessToken(), """
                { "files": [ { "fileName": "a.jpg", "contentType": "image/jpeg", "fileSize": 1024 } ] }""")
                .andExpect(status().isOk());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void 기록이_30일이_지나면_지운다() {
        LocalDateTime now = LocalDateTime.now(clock);
        DropOffContext context = new DropOffContext(1L, null);
        repository.save(new DropOffLog(context, Kind.SEARCH, Outcome.NO_RESULTS, now.minusDays(31)));
        repository.save(new DropOffLog(context, Kind.SEARCH, Outcome.NO_RESULTS, now.minusDays(29)));

        // 테스트 설정은 정리 스케줄러를 띄우지 않아 직접 만든다.
        int removed = new DropOffLogCleanup(repository, clock).run();

        assertThat(removed).isEqualTo(1);
        assertThat(repository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getCreatedAt()).isAfter(now.minusDays(30)));
    }

    private void givenDetails(RegionAddress address) {
        willReturn(new PlaceDetails(PlaceId.from("ChIJ-place"), null, address, GOOGLE))
                .given(googlePlacesClient).findDetails(any(), any());
    }

    private DropOffLog onlyLog() {
        List<DropOffLog> logs = repository.findAll();
        assertThat(logs).hasSize(1);
        return logs.getFirst();
    }

    private ResultActions search(String accessToken, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/places/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, IOS)
                .param("query", query)
                .param("sessionToken", TOKEN));
    }

    private ResultActions upload(String accessToken, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, IOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions select(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/places/ChIJ-place")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, IOS)
                .param("sessionToken", TOKEN));
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
