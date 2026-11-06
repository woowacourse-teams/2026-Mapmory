package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.mapmory.backend.travelrecord.application.UnlinkedMediaCleanup;
import com.mapmory.backend.travelrecord.application.UnlinkedMediaCleanupProperties;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectLister;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 기록에 붙지 않은 S3 객체 정리. (#186)
 *
 * <p>S3 목록은 대역으로 주고, 기록·정리 큐는 실제 DB로 대조한다.
 */
class UnlinkedMediaCleanupIntegrationTest extends IntegrationTest {

    private static final String GOOGLE_PLACE_ID = "ChIJTokyoTower";
    private static final Duration GRACE_PERIOD = Duration.ofDays(3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private OrphanedMediaRepository orphanedMediaRepository;

    @Autowired
    private UnlinkedMediaCleanupProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final StoredObjectLister lister = mock(StoredObjectLister.class);
    private final List<String> deletedKeys = new ArrayList<>();
    private String accessToken;
    private Long memberId;
    private Instant old;
    private Instant recent;

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
        old = clock.instant().minus(GRACE_PERIOD).minus(Duration.ofHours(1));
        recent = clock.instant().minus(GRACE_PERIOD).plus(Duration.ofHours(1));
    }

    /**
     * 처음 배포할 때 후보가 정말 고아인지 보고 끈다. 설정이 빠져도 지우지 않아야 한다.
     */
    @Test
    void 기본_설정은_지우지_않고_사흘을_기다린다() {
        assertThat(properties.dryRun()).isTrue();
        assertThat(properties.gracePeriod()).isEqualTo(GRACE_PERIOD);
    }

    @Test
    void 기록에_붙지_않고_유예_기간이_지난_객체만_지운다() {
        String unlinked = photo("unlinked.jpg");
        String justUploaded = photo("just-uploaded.jpg");
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(
                object(unlinked, old),
                object(justUploaded, recent)));

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(deletedKeys).containsExactly(unlinked);
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
    }

    @Test
    void 기록에_붙은_원본과_썸네일은_지우지_않는다() throws Exception {
        String linked = photo("linked.jpg");
        String thumb = photo("linked-thumb.jpg");
        long recordId = createRecord(linked);
        jdbcTemplate.update("UPDATE record_media SET thumb_key = ? WHERE travel_record_id = ?", thumb, recordId);
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(
                object(linked, old),
                object(thumb, old)));

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(result.candidates()).isZero();
        verify(objectDeleter, never()).delete(anyCollection());
    }

    /**
     * 기록을 지우거나 사진을 빼서 큐에 들어간 키는 큐 정리(#615)가 지운다. 두 배치가 같은 객체를 다루지 않게 한다.
     */
    @Test
    void 정리_큐에_있는_키는_건너뛴다() {
        String queued = photo("queued.jpg");
        jdbcTemplate.update("INSERT INTO orphaned_media (object_key, created_at) VALUES (?, NOW())", queued);
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(object(queued, old)));

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(result.candidates()).isZero();
        verify(objectDeleter, never()).delete(anyCollection());
    }

    @Test
    void 여러_쪽을_끝까지_읽는다() {
        String first = photo("first.jpg");
        String second = photo("second.jpg");
        given(lister.listTravelRecordObjects(null)).willReturn(new StoredObjectPage(List.of(object(first, old)), "next"));
        given(lister.listTravelRecordObjects("next")).willReturn(lastPage(object(second, old)));

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(deletedKeys).containsExactly(first, second);
        assertThat(result.scanned()).isEqualTo(2);
    }

    @Test
    void dry_run이면_후보만_세고_지우지_않는다() {
        String unlinked = photo("unlinked.jpg");
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(object(unlinked, old)));

        UnlinkedMediaCleanup.Result result = cleanup(true).run();

        verify(objectDeleter, never()).delete(anyCollection());
        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        assertThat(result.sample()).containsExactly(unlinked);
    }

    @Test
    void 한_쪽의_실패가_다음_쪽을_막지_않는다() {
        String broken = photo("broken.jpg");
        String next = photo("next.jpg");
        given(lister.listTravelRecordObjects(null)).willReturn(new StoredObjectPage(List.of(object(broken, old)), "next"));
        given(lister.listTravelRecordObjects("next")).willReturn(lastPage(object(next, old)));
        willAnswer(invocation -> {
            Collection<String> keys = invocation.getArgument(0);
            if (keys.contains(broken)) {
                throw new IllegalStateException("unexpected");
            }
            deletedKeys.addAll(keys);
            return List.of();
        }).given(objectDeleter).delete(anyCollection());

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(deletedKeys).containsExactly(next);
        assertThat(result.failedPages()).isEqualTo(1);
    }

    @Test
    void S3에서_지우지_못한_객체를_센다() {
        String failing = photo("failing.jpg");
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(object(failing, old)));
        willReturn(List.of(failing)).given(objectDeleter).delete(anyCollection());

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(result.deleted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void 우리_경로_밖의_키는_넘기지_않는다() {
        String outside = "other-team/travel-records/1/a.jpg";
        given(lister.listTravelRecordObjects(null)).willReturn(lastPage(object(outside, old)));

        UnlinkedMediaCleanup.Result result = cleanup(false).run();

        assertThat(result.candidates()).isZero();
        verify(objectDeleter, never()).delete(anyCollection());
    }

    private UnlinkedMediaCleanup cleanup(boolean dryRun) {
        return new UnlinkedMediaCleanup(lister, travelRecordRepository, orphanedMediaRepository, objectDeleter, clock,
                new UnlinkedMediaCleanupProperties(dryRun, GRACE_PERIOD));
    }

    private StoredObjectPage lastPage(StoredObject... objects) {
        return new StoredObjectPage(List.of(objects), null);
    }

    private StoredObject object(String key, Instant lastModified) {
        return new StoredObject(key, lastModified);
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
}
