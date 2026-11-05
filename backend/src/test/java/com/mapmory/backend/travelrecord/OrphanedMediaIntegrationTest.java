package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.travelrecord.application.OrphanedMediaCleanup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 기록에서 떨어져 나간 S3 객체 정리. (#162)
 */
class OrphanedMediaIntegrationTest extends IntegrationTest {

    private static final String GOOGLE_PLACE_ID = "ChIJTokyoTower";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrphanedMediaCleanup cleanup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> deletedKeys = new ArrayList<>();
    private String accessToken;
    private Long memberId;

    @BeforeEach
    void setUp() throws Exception {
        willReturn(new PlaceDetails(PlaceId.from(GOOGLE_PLACE_ID), null, new RegionAddress("JP", null, List.of()),
                new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps")))
                .given(googlePlacesClient).findDetails(PlaceId.from(GOOGLE_PLACE_ID), null);
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willReturn(new ExpiringUrl("https://download.example/image.jpg", 300L))
                .given(recordMediaUrlService).createViewUrl(anyString());
        willAnswer(invocation -> {
            deletedKeys.addAll(invocation.<Collection<String>>getArgument(0));
            return List.of();
        }).given(objectDeleter).delete(anyCollection());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login/guest")).andExpect(status().isOk()).andReturn();
        accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        memberId = memberRepository.findAll().stream().map(Member::getId).max(Long::compare).orElseThrow();
    }

    @Test
    void 기록을_지우면_사진과_썸네일을_S3에서_지운다() throws Exception {
        String photo = photo("a.jpg");
        long recordId = createRecord(photo);
        String thumb = "mapmory/travel-records/" + memberId + "/a-thumb.jpg";
        jdbcTemplate.update("UPDATE record_media SET thumb_key = ? WHERE travel_record_id = ?", thumb, recordId);

        deleteRecord(recordId);

        assertThat(cleanup.run()).isEqualTo(2);
        assertThat(deletedKeys).containsExactlyInAnyOrder(photo, thumb);
        assertThat(queued()).isZero();
    }

    @Test
    void 수정으로_뺀_사진만_S3에서_지운다() throws Exception {
        String kept = photo("kept.jpg");
        String removed = photo("removed.jpg");
        long recordId = createRecord(kept, removed);

        updateRecord(recordId, kept);
        cleanup.run();

        assertThat(deletedKeys).containsExactly(removed);
    }

    @Test
    void 뺐다가_다시_붙인_사진은_지우지_않는다() throws Exception {
        String kept = photo("kept.jpg");
        String returned = photo("returned.jpg");
        long recordId = createRecord(kept, returned);
        updateRecord(recordId, kept);
        updateRecord(recordId, kept, returned);

        assertThat(cleanup.run()).isZero();

        assertThat(deletedKeys).isEmpty();
        assertThat(queued()).isZero();
    }

    @Test
    void 같은_사진을_두_번_빼도_수정이_실패하지_않는다() throws Exception {
        String kept = photo("kept.jpg");
        String twice = photo("twice.jpg");
        long recordId = createRecord(kept, twice);
        updateRecord(recordId, kept);
        updateRecord(recordId, kept, twice);
        updateRecord(recordId, kept);

        assertThat(queued()).isEqualTo(1);
    }

    @Test
    void 지우지_못한_키는_남겨_다음에_다시_지운다() throws Exception {
        String photo = photo("a.jpg");
        deleteRecord(createRecord(photo));
        willReturn(List.of(photo)).given(objectDeleter).delete(anyCollection());

        assertThat(cleanup.run()).isZero();
        assertThat(queued()).isEqualTo(1);

        willReturn(List.of()).given(objectDeleter).delete(anyCollection());

        assertThat(cleanup.run()).isEqualTo(1);
        assertThat(queued()).isZero();
    }

    @Test
    void 한_묶음을_넘어도_모두_지운다() {
        List<String> keys = enqueue(501);

        assertThat(cleanup.run()).isEqualTo(501);

        assertThat(deletedKeys).containsExactlyInAnyOrderElementsOf(keys);
        assertThat(queued()).isZero();
    }

    @Test
    void 앞_묶음의_실패가_뒤_묶음을_막지_않는다() {
        List<String> keys = enqueue(501);
        String failed = keys.getFirst();
        willAnswer(invocation -> {
            Collection<String> requested = invocation.getArgument(0);
            deletedKeys.addAll(requested);
            return requested.contains(failed) ? List.of(failed) : List.of();
        }).given(objectDeleter).delete(anyCollection());

        assertThat(cleanup.run()).isEqualTo(500);

        assertThat(deletedKeys).containsExactlyInAnyOrderElementsOf(keys);
        assertThat(queued()).isEqualTo(1);
    }

    @Test
    void 지울_것이_없으면_S3를_부르지_않는다() {
        assertThat(cleanup.run()).isZero();

        verify(objectDeleter, never()).delete(anyCollection());
    }

    private List<String> enqueue(int count) {
        List<String> keys = IntStream.range(0, count).mapToObj(index -> photo(index + ".jpg")).toList();
        jdbcTemplate.batchUpdate("INSERT INTO orphaned_media (object_key, created_at) VALUES (?, NOW())", keys, keys.size(),
                (statement, key) -> statement.setString(1, key));
        return keys;
    }

    private String photo(String fileName) {
        return "mapmory/travel-records/" + memberId + "/" + fileName;
    }

    private long createRecord(String... objectKeys) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(objectKeys)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void updateRecord(long recordId, String... objectKeys) throws Exception {
        mockMvc.perform(put("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(objectKeys)))
                .andExpect(status().isOk());
    }

    private void deleteRecord(long recordId) throws Exception {
        mockMvc.perform(delete("/api/v1/travel-records/" + recordId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    private String recordBody(String... objectKeys) {
        String keys = String.join(", ", Arrays.stream(objectKeys).map("\"%s\""::formatted).toList());
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

    private long queued() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orphaned_media", Long.class);
    }
}
