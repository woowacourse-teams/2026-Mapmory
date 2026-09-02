package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import com.mapmory.backend.upload.service.UploadedObjectVerifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRecordServiceTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(SERVICE_ZONE).toInstant(),
            SERVICE_ZONE
    );

    @Mock
    private TravelRecordRepository travelRecordRepository;

    @Mock
    private RegionResolver regionResolver;

    @Mock
    private TravelRecordTagService travelRecordTagService;

    @Mock
    private TagService tagService;

    @Mock
    private RecordMediaUrlService recordMediaUrlService;

    @Mock
    private UploadedObjectVerifier uploadedObjectVerifier;
    @Spy
    private OperationTimer operationTimer = new OperationTimer(new SimpleMeterRegistry());

    private TravelRecordService travelRecordService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(10L);
        lenient().when(recordMediaUrlService.createViewUrl(anyString()))
                .thenAnswer(invocation -> new ExpiringUrl(
                        "https://download.example/" + invocation.getArgument(0),
                        300L
                ));
        travelRecordService = new TravelRecordService(
                travelRecordRepository,
                regionResolver,
                travelRecordTagService,
                tagService,
                operationTimer,
                new TravelRecordAssembler(
                        travelRecordRepository,
                        travelRecordTagService,
                        recordMediaUrlService
                ),
                FIXED_CLOCK,
                uploadedObjectVerifier
        );
    }

    @Test
    void 국가_단위_여행_일지를_생성한다() {
        Region japan = mock(Region.class);
        TravelRecordCommand command = new TravelRecordCommand(
                "JP", null, null, "일본 여행", "", LocalDate.of(2026, 8, 11), null, List.of(), List.of(1L)
        );

        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(travelRecordRepository.save(any(TravelRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecord result = travelRecordService.create(member, command);

        assertThat(result).isNotNull();
        verify(regionResolver).resolve("JP", null, null);
        verify(travelRecordRepository).save(any(TravelRecord.class));
        verify(travelRecordTagService).replace(member, result, List.of(1L));
    }

    @Test
    void 본문이_null이면_빈_문자열로_정규화해_여행_일지를_생성한다() {
        Region japan = mock(Region.class);
        TravelRecordCommand command = new TravelRecordCommand(
                "JP", null, null, "일본 여행", null, LocalDate.of(2026, 8, 11), null, List.of(), List.of()
        );

        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(travelRecordRepository.save(any(TravelRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecord result = travelRecordService.create(member, command);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void 종료일이_시작일보다_빠르면_여행_일지를_생성하지_않는다() {
        LocalDate startDate = TODAY.minusDays(1);
        TravelRecordCommand command = createCommand(startDate, startDate.minusDays(1));

        assertInvalidTravelDates(command);
    }

    @Test
    void 시작일이_미래이면_여행_일지를_생성하지_않는다() {
        TravelRecordCommand command = createCommand(
                TODAY.plusDays(1),
                null
        );

        assertInvalidTravelDates(command);
    }

    @Test
    void 종료일이_미래이면_여행_일지를_생성하지_않는다() {
        TravelRecordCommand command = createCommand(
                TODAY,
                TODAY.plusDays(1)
        );

        assertInvalidTravelDates(command);
    }

    @Test
    void 시작일과_종료일이_오늘이면_여행_일지를_생성한다() {
        Region japan = mock(Region.class);
        TravelRecordCommand command = createCommand(TODAY, TODAY);
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(travelRecordRepository.save(any(TravelRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecord result = travelRecordService.create(member, command);

        assertThat(result.getStartDate()).isEqualTo(TODAY);
        assertThat(result.getEndDate()).isEqualTo(TODAY);
    }

    @Test
    void 대한민국_여행에_시도가_없으면_여행_일지를_생성하지_않는다() {
        TravelRecordCommand command = createCommand("KR", null, null);

        assertInvalidRegion(command, "REGION_REQUIRED");
    }

    @Test
    void 대한민국_여행에_시군구가_없으면_여행_일지를_생성하지_않는다() {
        TravelRecordCommand command = createCommand("KR", "49", null);

        assertInvalidRegion(command, "REGION_REQUIRED");
    }

    @Test
    void 해외_여행에_하위_지역이_있으면_여행_일지를_생성하지_않는다() {
        TravelRecordCommand command = createCommand("JP", "13", null);

        assertInvalidRegion(command, "INVALID_REGION_TYPE");
    }

    @Test
    void 대한민국_시군구_단위_여행_일지를_생성한다() {
        Region jejuCity = mock(Region.class);
        TravelRecordCommand command = createCommand("KR", "49", "50110");
        when(regionResolver.resolve("KR", "49", "50110")).thenReturn(jejuCity);
        when(travelRecordRepository.save(any(TravelRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecord result = travelRecordService.create(member, command);

        assertThat(result.getRegion()).isEqualTo(jejuCity);
        verify(travelRecordRepository).save(result);
    }

    @Test
    void 지역_계층과_정렬된_Object_Key를_포함한_일지_상세를_조회한다() {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "49", "제주특별자치도", RegionType.PROVINCE);
        Region district = Region.of(province, country, "50110", "제주시", RegionType.DISTRICT);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                district,
                "제주 여행",
                "제주시를 걸었다.",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        );
        ReflectionTestUtils.setField(travelRecord, "id", 101L);
        travelRecord.synchronizeMedia(List.of(
                "mapmory/travel-records/a.jpg",
                "mapmory/travel-records/b.jpg"
        ));
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));

        TravelRecordDetail result = travelRecordService.findById(member, 101L);

        Region resolvedRegion = result.travelRecord().getRegion();
        assertThat(result.travelRecord().getId()).isEqualTo(101L);
        assertThat(result.travelRecord().getContent()).isEqualTo("제주시를 걸었다.");
        assertThat(resolvedRegion.getRoot().getRegionCode()).isEqualTo("KR");
        assertThat(resolvedRegion.getParent().getRegionCode()).isEqualTo("49");
        assertThat(resolvedRegion.getRegionCode()).isEqualTo("50110");
        assertThat(result.recordMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly(
                        "mapmory/travel-records/a.jpg",
                        "mapmory/travel-records/b.jpg"
                );
        assertThat(result.mediaViews())
                .extracting(mediaView -> mediaView.viewUrl().url())
                .containsExactly(
                        "https://download.example/mapmory/travel-records/a.jpg",
                        "https://download.example/mapmory/travel-records/b.jpg"
                );
        assertThat(result.mediaViews())
                .extracting(mediaView -> mediaView.viewUrl().expiresIn())
                .containsOnly(300L);
        assertThat(result.mediaViews())
                .extracting(mediaView -> mediaView.recordMedia().getSortOrder())
                .containsExactly(0, 1);
        verify(recordMediaUrlService).createViewUrl("mapmory/travel-records/a.jpg");
        verify(recordMediaUrlService).createViewUrl("mapmory/travel-records/b.jpg");
    }

    private TravelRecordCommand createCommand(LocalDate startDate, LocalDate endDate) {
        return new TravelRecordCommand(
                "JP",
                null,
                null,
                "일본 여행",
                "",
                startDate,
                endDate,
                List.of(),
                List.of()
        );
    }

    private TravelRecordCommand createCommand(
            String countryCode,
            String provinceCode,
            String districtCode
    ) {
        return new TravelRecordCommand(
                countryCode,
                provinceCode,
                districtCode,
                "여행",
                "",
                TODAY,
                null,
                List.of(),
                List.of()
        );
    }

    private void assertInvalidTravelDates(TravelRecordCommand command) {
        assertThatThrownBy(() -> travelRecordService.create(member, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode().code())
                                .isEqualTo("INVALID_TRAVEL_DATE_RANGE"));
        verify(travelRecordRepository, never()).save(any(TravelRecord.class));
    }

    private void assertInvalidRegion(TravelRecordCommand command, String errorCode) {
        assertThatThrownBy(() -> travelRecordService.create(member, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode().code()).isEqualTo(errorCode));
        verify(travelRecordRepository, never()).save(any(TravelRecord.class));
    }

    @Test
    void 미디어가_없는_일지는_빈_Object_Key_목록을_반환한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                japan,
                "일본 여행",
                "도쿄 여행",
                LocalDate.of(2026, 8, 11),
                null
        );
        ReflectionTestUtils.setField(travelRecord, "id", 102L);
        when(travelRecordRepository.findByIdAndMemberId(102L, 10L))
                .thenReturn(Optional.of(travelRecord));

        TravelRecordDetail result = travelRecordService.findById(member, 102L);

        assertThat(result.travelRecord().getRegion().getRegionCode()).isEqualTo("JP");
        assertThat(result.travelRecord().getRegion().getParent()).isNull();
        assertThat(result.recordMedia()).isEmpty();
        assertThat(result.mediaViews()).isEmpty();
        verify(recordMediaUrlService, never()).createViewUrl(anyString());
    }

    @Test
    void 없거나_다른_회원의_일지_상세_조회를_거부한다() {
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.empty());

        assertError(() -> travelRecordService.findById(member, 101L), "TRAVEL_RECORD_NOT_FOUND");
    }

    @Test
    void 여행_일지를_수정하고_미디어를_동기화한다() {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "49", "제주특별자치도", RegionType.PROVINCE);
        Region district = Region.of(province, country, "50110", "제주시", RegionType.DISTRICT);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                country,
                "기존 제목",
                "기존 본문",
                LocalDate.of(2026, 8, 1),
                null
        );
        ReflectionTestUtils.setField(travelRecord, "id", 101L);
        travelRecord.synchronizeMedia(List.of("travel-records/10/a.jpg", "travel-records/10/b.jpg"));
        RecordMedia mediaB = travelRecord.getMedia().getLast();
        TravelRecordCommand command = new TravelRecordCommand(
                "KR",
                "49",
                "50110",
                "수정된 제목",
                "수정된 본문",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13),
                List.of("travel-records/10/b.jpg", "travel-records/10/c.jpg"),
                List.of()
            );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("KR", "49", "50110")).thenReturn(district);
        when(travelRecordRepository.existsMediaByObjectKeyIn(List.of("travel-records/10/c.jpg")))
                .thenReturn(false);

        TravelRecordDetail result = travelRecordService.update(member, 101L, command);

        assertThat(result.travelRecord().getTitle()).isEqualTo("수정된 제목");
        assertThat(result.travelRecord().getContent()).isEqualTo("수정된 본문");
        assertThat(result.travelRecord().getRegion().getRegionCode()).isEqualTo("50110");
        assertThat(result.recordMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly(
                        "travel-records/10/b.jpg",
                        "travel-records/10/c.jpg"
                );
        assertThat(mediaB.getSortOrder()).isZero();
        assertThat(travelRecord.getMedia())
                .extracting(RecordMedia::getObjectKey)
                .doesNotContain("travel-records/10/a.jpg");
        verify(operationTimer).record(eq(MonitoredOperation.MEDIA_SYNC), any());
    }

    @Test
    void 수정_요청의_중복_Object_Key를_거부한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                japan,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null
        );
        TravelRecordCommand command = new TravelRecordCommand(
                "JP",
                null,
                null,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                List.of("travel-records/10/a.jpg", "travel-records/10/a.jpg"),
                List.of()
            );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));

        assertError(() -> travelRecordService.update(member, 101L, command), "INVALID_OBJECT_KEY");
        verify(regionResolver, never()).resolve("JP", null, null);
    }

    @Test
    void 다른_일지에서_사용_중인_Object_Key를_거부한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                japan,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null
        );
        TravelRecordCommand command = new TravelRecordCommand(
                "JP",
                null,
                null,
                "수정된 일본 여행",
                "수정된 본문",
                LocalDate.of(2026, 8, 12),
                null,
                List.of("travel-records/20/used.jpg"),
                List.of()
            );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(travelRecordRepository.existsMediaByObjectKeyIn(List.of("travel-records/20/used.jpg")))
                .thenReturn(true);

        assertError(() -> travelRecordService.update(member, 101L, command), "INVALID_OBJECT_KEY");
        assertThat(travelRecord.getTitle()).isEqualTo("일본 여행");
        assertThat(travelRecord.getMedia()).isEmpty();
    }

    @Test
    void Object_Key가_null이면_모든_미디어를_삭제한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class),
                japan,
                "기존 제목",
                "기존 본문",
                LocalDate.of(2026, 8, 11),
                null
        );
        travelRecord.synchronizeMedia(List.of("travel-records/10/a.jpg"));
        TravelRecordCommand command = new TravelRecordCommand(
                "JP",
                null,
                null,
                "수정된 제목",
                "수정된 본문",
                LocalDate.of(2026, 8, 12),
                null,
                null,
                List.of()
            );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);


        TravelRecordDetail result = travelRecordService.update(member, 101L, command);

        assertThat(result.recordMedia()).isEmpty();
        assertThat(travelRecord.getMedia()).isEmpty();
        verify(travelRecordRepository, never()).existsMediaByObjectKeyIn(anyList());
    }

    @Test
    void 없거나_다른_회원의_일지_수정을_거부한다() {
        TravelRecordCommand command = new TravelRecordCommand(
                "JP",
                null,
                null,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                List.of(),
                List.of()
            );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.empty());

        assertError(() -> travelRecordService.update(member, 101L, command), "TRAVEL_RECORD_NOT_FOUND");
        verify(regionResolver, never()).resolve("JP", null, null);
    }

    @Test
    void 지역_필터_없이_일지_목록을_조회한다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        when(travelRecord.getId()).thenReturn(101L);
        Page<TravelRecord> expected = new PageImpl<>(List.of(travelRecord), PageRequest.of(0, 20), 1);
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(null), any(Pageable.class)))
                .thenReturn(expected);
        RecordMedia thumbnailMedia = recordMedia(101L, "mapmory/travel-records/a.jpg");
        when(travelRecordRepository.findMediaByTravelRecordIdIn(List.of(101L)))
                .thenReturn(List.of(thumbnailMedia));

        TravelRecordSummaries result = travelRecordService.findAll(member, null, null, null, null, 0, 20);

        assertThat(result.travelRecords().getTotalElements()).isEqualTo(1);
        assertThat(result.thumbnailUrlOf(travelRecord).url())
                .isEqualTo("https://download.example/mapmory/travel-records/a.jpg");
        assertThat(result.thumbnailUrlOf(travelRecord).expiresIn()).isEqualTo(300L);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(travelRecordRepository).findByMemberIdAndOptionalTagId(eq(10L), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        verify(recordMediaUrlService).createViewUrl("mapmory/travel-records/a.jpg");
    }

    @Test
    void 일지마다_정렬이_가장_앞선_미디어만_썸네일로_쓴다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        when(travelRecord.getId()).thenReturn(101L);
        Page<TravelRecord> expected = new PageImpl<>(List.of(travelRecord), PageRequest.of(0, 20), 1);
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(null), any(Pageable.class)))
                .thenReturn(expected);
        RecordMedia firstMedia = recordMedia(101L, "mapmory/first.jpg");
        RecordMedia laterMedia = recordMedia(101L, "mapmory/later.jpg");
        when(travelRecordRepository.findMediaByTravelRecordIdIn(List.of(101L)))
                .thenReturn(List.of(firstMedia, laterMedia));

        TravelRecordSummaries result = travelRecordService.findAll(member, null, null, null, null, 0, 20);

        assertThat(result.thumbnailUrlOf(travelRecord).url())
                .isEqualTo("https://download.example/mapmory/first.jpg");
        verify(recordMediaUrlService).createViewUrl("mapmory/first.jpg");
        verify(recordMediaUrlService, never()).createViewUrl("mapmory/later.jpg");
    }

    @Test
    void 미디어가_없는_일지_목록은_썸네일_정보가_null이다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        when(travelRecord.getId()).thenReturn(101L);
        Page<TravelRecord> expected = new PageImpl<>(List.of(travelRecord), PageRequest.of(0, 20), 1);
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(null), any(Pageable.class)))
                .thenReturn(expected);
        when(travelRecordRepository.findMediaByTravelRecordIdIn(List.of(101L))).thenReturn(List.of());

        TravelRecordSummaries result = travelRecordService.findAll(member, null, null, null, null, 0, 20);

        assertThat(result.thumbnailUrlOf(travelRecord)).isNull();
        verify(recordMediaUrlService, never()).createViewUrl(anyString());
    }

    @Test
    void 국가로_일지_목록을_조회한다() {
        Region korea = region(1L);
        Page<TravelRecord> expected = Page.empty();
        when(regionResolver.resolve("KR", null, null)).thenReturn(korea);
        when(travelRecordRepository.findByMemberIdAndCountryIdAndOptionalTagId(
                eq(10L), eq(1L), eq(null), any(Pageable.class)))
                .thenReturn(expected);

        assertThat(travelRecordService.findAll(member, "KR", null, null, null, 0, 20).travelRecords()).isEmpty();
        verify(travelRecordRepository, never()).findMediaByTravelRecordIdIn(anyList());
    }

    @Test
    void 시도로_일지_목록을_조회한다() {
        Region korea = mock(Region.class);
        Region jeju = region(2L);
        Page<TravelRecord> expected = Page.empty();
        when(regionResolver.resolve("KR", "49", null)).thenReturn(jeju);
        when(travelRecordRepository.findByMemberIdAndProvinceIdAndOptionalTagId(
                eq(10L), eq(2L), eq(null), any(Pageable.class)))
                .thenReturn(expected);

        assertThat(travelRecordService.findAll(member, "KR", "49", null, null, 0, 20).travelRecords()).isEmpty();
    }

    @Test
    void 시군구로_일지_목록을_조회한다() {
        Region korea = mock(Region.class);
        Region jeju = mock(Region.class);
        Region jejuCity = region(3L);
        Page<TravelRecord> expected = Page.empty();
        when(regionResolver.resolve("KR", "49", "50110")).thenReturn(jejuCity);
        when(travelRecordRepository.findByMemberIdAndRegionIdAndOptionalTagId(
                eq(10L), eq(3L), eq(null), any(Pageable.class)))
                .thenReturn(expected);

        assertThat(travelRecordService.findAll(member, "KR", "49", "50110", null, 0, 20).travelRecords()).isEmpty();
    }

    @Test
    void 소유한_태그로_일지_목록을_필터링한다() {
        Page<TravelRecord> expected = Page.empty();
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(7L), any(Pageable.class)))
                .thenReturn(expected);

        TravelRecordSummaries result = travelRecordService.findAll(
                member, null, null, null, 7L, 0, 20
        );

        assertThat(result.travelRecords()).isEmpty();
        verify(tagService).getOwnedTag(member, 7L);
        verify(travelRecordRepository).findByMemberIdAndOptionalTagId(eq(10L), eq(7L), any(Pageable.class));
    }

    @Test
    void 잘못된_지역_필터_조합을_거부한다() {
        assertError(() -> travelRecordService.findAll(member, null, "49", null, null, 0, 20), "REGION_REQUIRED");
        assertError(() -> travelRecordService.findAll(member, "KR", null, "50110", null, 0, 20), "REGION_REQUIRED");
    }

    @Test
    void 잘못된_지역_코드_형식을_거부한다() {
        assertError(() -> travelRecordService.findAll(member, "kr", null, null, null, 0, 20), "VALIDATION_ERROR");
        assertError(() -> travelRecordService.findAll(member, "KR", " ", null, null, 0, 20), "VALIDATION_ERROR");
    }

    @Test
    void 잘못된_페이지네이션을_거부한다() {
        assertError(() -> travelRecordService.findAll(member, null, null, null, null, -1, 20), "VALIDATION_ERROR");
        assertError(() -> travelRecordService.findAll(member, null, null, null, null, 0, 0), "VALIDATION_ERROR");
        assertError(() -> travelRecordService.findAll(member, null, null, null, null, 0, 101), "VALIDATION_ERROR");
    }

    @Test
    void 소유한_여행_일지를_삭제한다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));

        travelRecordService.delete(member, 101L);

        verify(travelRecordRepository).delete(travelRecord);
    }

    @Test
    void 없거나_다른_회원의_여행_일지_삭제를_거부한다() {
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.empty());

        assertError(() -> travelRecordService.delete(member, 101L), "TRAVEL_RECORD_NOT_FOUND");
        verify(travelRecordRepository, never()).delete(any(TravelRecord.class));
    }

    private RecordMedia recordMedia(Long travelRecordId, String thumbnailObjectKey) {
        RecordMedia recordMedia = mock(RecordMedia.class);
        lenient().when(recordMedia.travelRecordId()).thenReturn(travelRecordId);
        lenient().when(recordMedia.getThumbnailObjectKey()).thenReturn(thumbnailObjectKey);
        return recordMedia;
    }

    private Region region(Long id) {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(id);
        return region;
    }

    private void assertError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(errorCode);
    }
}
