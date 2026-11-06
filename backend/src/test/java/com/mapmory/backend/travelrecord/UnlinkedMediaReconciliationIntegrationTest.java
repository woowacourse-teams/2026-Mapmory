package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
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
import com.mapmory.backend.travelrecord.application.OrphanedMediaCleanup;
import com.mapmory.backend.travelrecord.application.UnlinkedMediaProperties;
import com.mapmory.backend.travelrecord.application.UnlinkedMediaReconciliation;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 기록에 붙지 않은 S3 객체 대조. (#186)
 */
class UnlinkedMediaReconciliationIntegrationTest extends IntegrationTest {

    private static final String GOOGLE_PLACE_ID = "ChIJTokyoTower";
    private static final String METRIC_NAME = "mapmory.media.unlinked.reconcile";
    private static final Instant NOW = Instant.parse("2026-11-06T20:00:00Z");
    private static final Duration GRACE_PERIOD = Duration.ofDays(3);
    private static final Instant OLD = NOW.minus(GRACE_PERIOD).minusSeconds(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TravelRecordRepository travelRecordRepository;

    @Autowired
    private OrphanedMediaRepository orphanedMediaRepository;

    @Autowired
    private OrphanedMediaCleanup cleanup;

    @Autowired
    private UnlinkedMediaReconciliation configuredReconciliation;

    @Autowired
    private ObjectKeyGenerator objectKeyGenerator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

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
    void 유예_기간이_지난_미연결_객체만_큐에_넣는다() {
        String old = photo("old.jpg");
        String exactlyGrace = photo("exactly-grace.jpg");
        String recent = photo("recent.jpg");
        stubLastPage(null, new StoredObject(old, OLD), new StoredObject(exactlyGrace, NOW.minus(GRACE_PERIOD)),
                new StoredObject(recent, NOW.minus(Duration.ofDays(1))));

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(queuedKeys()).containsExactly(old);
        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.queued()).isEqualTo(1);
    }

    @Test
    void 기록의_원본_키는_후보가_아니다() throws Exception {
        String linked = photo("linked.jpg");
        createRecord(linked);
        stubLastPage(null, new StoredObject(linked, OLD));

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(result.candidates()).isZero();
        assertThat(queuedKeys()).isEmpty();
    }

    @Test
    void 기록의_썸네일_키는_후보가_아니다() throws Exception {
        String thumb = photo("thumb.jpg");
        long recordId = createRecord(photo("linked.jpg"));
        jdbcTemplate.update("UPDATE record_media SET thumb_key = ? WHERE travel_record_id = ?", thumb, recordId);
        stubLastPage(null, new StoredObject(thumb, OLD));

        assertThat(reconciliation(false).run().candidates()).isZero();

        assertThat(queuedKeys()).isEmpty();
    }

    // 삭제에 실패해 큐에 남은 키를 날마다 새 후보로 세면 숫자를 믿을 수 없다.
    @Test
    void 이미_큐에_있는_키는_후보로_세지_않는다() {
        String queued = photo("queued.jpg");
        stubLastPage(null, new StoredObject(queued, OLD));
        reconciliation(false).run();

        assertThat(reconciliation(false).run().candidates()).isZero();

        assertThat(queuedKeys()).containsExactly(queued);
    }

    @Test
    void 드라이런이면_큐에_넣지_않고_후보만_센다() {
        String old = photo("old.jpg");
        stubLastPage(null, new StoredObject(old, OLD));

        UnlinkedMediaReconciliation.Result result = reconciliation(true).run();

        assertThat(queuedKeys()).isEmpty();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.queued()).isZero();
        assertThat(result.sample()).containsExactly(old);
    }

    // 운영이 쓰는 값은 application.yaml이다. 환경 변수가 빠진 서버가 지우기 시작하면 안 된다.
    @Test
    void 설정이_없는_서버의_배치는_큐에_넣지_않는다() {
        stubLastPage(null, new StoredObject(photo("old.jpg"), Instant.parse("2020-01-01T00:00:00Z")));

        UnlinkedMediaReconciliation.Result result = configuredReconciliation.run();

        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(queuedKeys()).isEmpty();
    }

    @Test
    void 후보_예시는_다섯_개까지만_남긴다() {
        StoredObject[] objects = IntStream.range(0, 7)
                .mapToObj(index -> new StoredObject(photo(index + ".jpg"), OLD))
                .toArray(StoredObject[]::new);
        stubLastPage(null, objects);

        assertThat(reconciliation(true).run().sample()).hasSize(5);
    }

    // S3 목록은 사전순이다. 앞의 몇 개만 남기면 날마다 같은 회원의 키만 보게 된다.
    @Test
    void 후보_예시는_뒤쪽_키도_담는다() {
        List<StoredObject> objects = IntStream.range(0, 50)
                .mapToObj(index -> new StoredObject(photo(index + ".jpg"), OLD))
                .toList();
        stubLastPage(null, objects.toArray(StoredObject[]::new));
        List<String> firstFive = objects.subList(0, 5).stream().map(StoredObject::key).toList();

        Set<String> seen = new HashSet<>();
        IntStream.range(0, 20).forEach(run -> seen.addAll(reconciliation(true).run().sample()));

        assertThat(seen).isNotEqualTo(Set.copyOf(firstFive));
    }

    @Test
    void 여러_쪽을_끝까지_읽는다() {
        String first = photo("first.jpg");
        String second = photo("second.jpg");
        willReturn(new StoredObjectPage(List.of(new StoredObject(first, OLD)), "next"))
                .given(storedObjectLister).listTravelRecordObjects(null);
        stubLastPage("next", new StoredObject(second, OLD));

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(queuedKeys()).containsExactlyInAnyOrder(first, second);
        assertThat(result.scanned()).isEqualTo(2);
    }

    // 지우면 되돌릴 수 없으므로 서버가 발급한 형식이 아닌 객체는 건드리지 않고 수만 센다.
    @Test
    void 서버가_만든_형식이_아닌_키는_후보가_아니다() {
        String uuid = UUID.randomUUID().toString();
        stubLastPage(null,
                new StoredObject("other-team/travel-records/1/" + uuid + ".jpg", OLD),
                new StoredObject("mapmory/travel-records/" + memberId + "/", OLD),
                new StoredObject("mapmory/travel-records/backup/" + uuid + ".jpg", OLD),
                new StoredObject("mapmory/travel-records/" + memberId + "/sub/" + uuid + ".jpg", OLD),
                new StoredObject("mapmory/travel-records/" + memberId + "/manual.jpg", OLD));
        double before = count("UNRECOGNIZED", false);

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(result.candidates()).isZero();
        assertThat(result.unrecognized()).isEqualTo(5);
        assertThat(queuedKeys()).isEmpty();
        assertThat(count("UNRECOGNIZED", false) - before).isEqualTo(5);
    }

    @Test
    void 유예_기간이_지나지_않은_낯선_키는_세지_않는다() {
        stubLastPage(null, new StoredObject("mapmory/travel-records/backup/a.jpg", NOW));

        assertThat(reconciliation(false).run().unrecognized()).isZero();
    }

    @Test
    void 한_쪽을_처리하지_못해도_다음_쪽을_읽는다() {
        String failing = photo("failing.jpg");
        String next = photo("next.jpg");
        willReturn(new StoredObjectPage(List.of(new StoredObject(failing, OLD)), "next"))
                .given(storedObjectLister).listTravelRecordObjects(null);
        stubLastPage("next", new StoredObject(next, OLD));
        TravelRecordRepository failingOnce = mock(TravelRecordRepository.class);
        given(failingOnce.findObjectKeysIn(anyCollection()))
                .willThrow(new IllegalStateException("db down"))
                .willReturn(List.of());
        double failedBefore = count("FAILED_PAGE", false);

        UnlinkedMediaReconciliation.Result result = reconciliation(false, failingOnce).run();

        assertThat(result.failedPages()).isEqualTo(1);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(queuedKeys()).containsExactly(next);
        assertThat(count("FAILED_PAGE", false) - failedBefore).isEqualTo(1);
    }

    @Test
    void 목록_조회가_실패하면_멈추고_그때까지의_결과를_돌려준다() {
        String first = photo("first.jpg");
        willReturn(new StoredObjectPage(List.of(new StoredObject(first, OLD)), "next"))
                .given(storedObjectLister).listTravelRecordObjects(null);
        willThrow(new IllegalStateException("s3 down")).given(storedObjectLister).listTravelRecordObjects("next");
        double before = count("FAILED_LIST", false);

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(result.listFailed()).isTrue();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.sample()).containsExactly(first);
        assertThat(queuedKeys()).containsExactly(first);
        assertThat(count("FAILED_LIST", false) - before).isEqualTo(1);
    }

    @Test
    void 첫_쪽_조회가_실패해도_결과를_돌려준다() {
        willThrow(new IllegalStateException("s3 down")).given(storedObjectLister).listTravelRecordObjects(null);

        UnlinkedMediaReconciliation.Result result = reconciliation(false).run();

        assertThat(result.listFailed()).isTrue();
        assertThat(result.scanned()).isZero();
    }

    @Test
    void 후보와_큐에_넣은_수를_메트릭에_남긴다() {
        stubLastPage(null, new StoredObject(photo("old.jpg"), OLD));
        double candidatesBefore = count("CANDIDATE", false);
        double queuedBefore = count("QUEUED", false);

        reconciliation(false).run();

        assertThat(count("CANDIDATE", false) - candidatesBefore).isEqualTo(1);
        assertThat(count("QUEUED", false) - queuedBefore).isEqualTo(1);
    }

    @Test
    void 드라이런의_후보는_실제_후보와_나눠_센다() {
        stubLastPage(null, new StoredObject(photo("old.jpg"), OLD));
        double before = count("CANDIDATE", true);

        reconciliation(true).run();

        assertThat(count("CANDIDATE", true) - before).isEqualTo(1);
    }

    @Test
    void 대조_배치는_직접_지우지_않는다() {
        stubLastPage(null, new StoredObject(photo("old.jpg"), OLD));

        reconciliation(false).run();

        verify(objectDeleter, never()).delete(anyCollection());
    }

    @Test
    void 큐에_넣은_키를_다음_정리_배치가_지운다() {
        String old = photo("old.jpg");
        stubLastPage(null, new StoredObject(old, OLD));
        reconciliation(false).run();

        assertThat(cleanup.run().deleted()).isEqualTo(1);

        assertThat(deletedKeys).containsExactly(old);
        assertThat(queuedKeys()).isEmpty();
    }

    // 앱은 며칠 전에 올린 키로 저장을 다시 시도할 수 있다. 후보가 된 뒤 붙어도 사진이 사라지면 안 된다.
    @Test
    void 큐에_넣은_뒤_기록에_붙은_키는_지우지_않고_행만_없앤다() throws Exception {
        String old = photo("old.jpg");
        stubLastPage(null, new StoredObject(old, OLD));
        reconciliation(false).run();
        createRecord(old);

        assertThat(cleanup.run().deleted()).isZero();

        assertThat(deletedKeys).isEmpty();
        assertThat(queuedKeys()).isEmpty();
    }

    private UnlinkedMediaReconciliation reconciliation(boolean dryRun) {
        return reconciliation(dryRun, travelRecordRepository);
    }

    private UnlinkedMediaReconciliation reconciliation(boolean dryRun, TravelRecordRepository records) {
        return new UnlinkedMediaReconciliation(
                storedObjectLister,
                records,
                orphanedMediaRepository,
                objectKeyGenerator,
                transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC),
                meterRegistry,
                new UnlinkedMediaProperties(dryRun, GRACE_PERIOD)
        );
    }

    private void stubLastPage(String token, StoredObject... objects) {
        willReturn(new StoredObjectPage(List.of(objects), null))
                .given(storedObjectLister).listTravelRecordObjects(token);
    }

    private double count(String outcome, boolean dryRun) {
        return meterRegistry.counter(METRIC_NAME, "outcome", outcome, "dryRun", String.valueOf(dryRun)).count();
    }

    // 서버가 만드는 형식의 키. 같은 이름이면 같은 키다.
    private String photo(String name) {
        return "mapmory/travel-records/" + memberId + "/" + UUID.nameUUIDFromBytes(name.getBytes()) + ".jpg";
    }

    private List<String> queuedKeys() {
        return jdbcTemplate.queryForList("SELECT object_key FROM orphaned_media", String.class);
    }

    private long createRecord(String objectKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "도쿄 여행",
                                  "content": "기록 본문",
                                  "startDate": "2026-08-01",
                                  "objectKeys": ["%s"],
                                  "placeId": "%s"
                                }
                                """.formatted(objectKey, GOOGLE_PLACE_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }
}
