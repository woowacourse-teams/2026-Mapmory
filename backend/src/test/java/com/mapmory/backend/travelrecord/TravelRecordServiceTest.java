package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.recordmedia.RecordMedia;
import com.mapmory.backend.recordmedia.RecordMediaRepository;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecord.dto.TravelRecordDetailResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordListResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRecordServiceTest {

    @Mock
    private TravelRecordRepository travelRecordRepository;

    @Mock
    private RegionResolver regionResolver;

    @Mock
    private RecordMediaRepository recordMediaRepository;

    @Mock
    private TravelRecordTagService travelRecordTagService;

    @Mock
    private TagService tagService;

    @InjectMocks
    private TravelRecordService travelRecordService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(10L);
    }

    @Test
    void 국가_단위_여행_일지를_생성한다() {
        Region japan = mock(Region.class);
        TravelRecordRequest request = new TravelRecordRequest(
                "JP", null, null, "일본 여행", "", LocalDate.of(2026, 8, 11), null, List.of(), List.of(1L)
        );

        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(travelRecordRepository.save(any(TravelRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecord result = travelRecordService.create(member, request);

        assertThat(result).isNotNull();
        verify(regionResolver).resolve("JP", null, null);
        verify(travelRecordRepository).save(any(TravelRecord.class));
        verify(travelRecordTagService).replace(member, result, List.of(1L));
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
        List<RecordMedia> recordMedia = List.of(
                RecordMedia.of(travelRecord, "mapmory/travel-records/a.jpg", null, 0),
                RecordMedia.of(travelRecord, "mapmory/travel-records/b.jpg", null, 1)
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(recordMediaRepository.findByTravelRecordIdOrderBySortOrderAsc(101L))
                .thenReturn(recordMedia);

        TravelRecordDetailResponse result = travelRecordService.findById(member, 101L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.content()).isEqualTo("제주시를 걸었다.");
        assertThat(result.region().country().code()).isEqualTo("KR");
        assertThat(result.region().province().code()).isEqualTo("49");
        assertThat(result.region().district().code()).isEqualTo("50110");
        assertThat(result.objectKeys()).containsExactly(
                "mapmory/travel-records/a.jpg",
                "mapmory/travel-records/b.jpg"
        );
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
        when(recordMediaRepository.findByTravelRecordIdOrderBySortOrderAsc(102L))
                .thenReturn(List.of());

        TravelRecordDetailResponse result = travelRecordService.findById(member, 102L);

        assertThat(result.region().country().code()).isEqualTo("JP");
        assertThat(result.region().province()).isNull();
        assertThat(result.region().district()).isNull();
        assertThat(result.objectKeys()).isEmpty();
    }

    @Test
    void 없거나_다른_회원의_일지_상세_조회를_거부한다() {
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.empty());

        assertError(() -> travelRecordService.findById(member, 101L), "TRAVEL_RECORD_NOT_FOUND");
        verify(recordMediaRepository, never()).findByTravelRecordIdOrderBySortOrderAsc(101L);
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
        RecordMedia mediaA = RecordMedia.of(travelRecord, "travel-records/10/a.jpg", null, 0);
        RecordMedia mediaB = RecordMedia.of(travelRecord, "travel-records/10/b.jpg", null, 1);
        TravelRecordRequest request = new TravelRecordRequest(
                "KR",
                "49",
                "50110",
                "수정된 제목",
                "수정된 본문",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13),
                List.of("travel-records/10/b.jpg", "travel-records/10/c.jpg")
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("KR", "49", "50110")).thenReturn(district);
        when(recordMediaRepository.findByTravelRecordIdOrderBySortOrderAsc(101L))
                .thenReturn(List.of(mediaA, mediaB));
        when(recordMediaRepository.findByObjectKeyIn(List.of("travel-records/10/c.jpg")))
                .thenReturn(List.of());
        when(recordMediaRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecordDetailResponse result = travelRecordService.update(member, 101L, request);

        assertThat(result.title()).isEqualTo("수정된 제목");
        assertThat(result.content()).isEqualTo("수정된 본문");
        assertThat(result.region().district().code()).isEqualTo("50110");
        assertThat(result.objectKeys()).containsExactly(
                "travel-records/10/b.jpg",
                "travel-records/10/c.jpg"
        );
        assertThat(mediaB.getSortOrder()).isZero();
        verify(recordMediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(records -> {
            java.util.Iterator<? extends RecordMedia> iterator = records.iterator();
            return iterator.hasNext()
                    && iterator.next() == mediaA
                    && !iterator.hasNext();
        }));
    }

    @Test
    void 수정_요청의_중복_Object_Key를_거부한다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                List.of("travel-records/10/a.jpg", "travel-records/10/a.jpg")
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));

        assertError(() -> travelRecordService.update(member, 101L, request), "INVALID_OBJECT_KEY");
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
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "수정된 일본 여행",
                "수정된 본문",
                LocalDate.of(2026, 8, 12),
                null,
                List.of("travel-records/20/used.jpg")
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(recordMediaRepository.findByTravelRecordIdOrderBySortOrderAsc(101L))
                .thenReturn(List.of());
        when(recordMediaRepository.findByObjectKeyIn(List.of("travel-records/20/used.jpg")))
                .thenReturn(List.of(mock(RecordMedia.class)));

        assertError(() -> travelRecordService.update(member, 101L, request), "INVALID_OBJECT_KEY");
        assertThat(travelRecord.getTitle()).isEqualTo("일본 여행");
        verify(recordMediaRepository, never()).saveAll(anyList());
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
        RecordMedia existingMedia = RecordMedia.of(
                travelRecord,
                "travel-records/10/a.jpg",
                null,
                0
        );
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "수정된 제목",
                "수정된 본문",
                LocalDate.of(2026, 8, 12),
                null,
                null
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.of(travelRecord));
        when(regionResolver.resolve("JP", null, null)).thenReturn(japan);
        when(recordMediaRepository.findByTravelRecordIdOrderBySortOrderAsc(101L))
                .thenReturn(List.of(existingMedia));
        when(recordMediaRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TravelRecordDetailResponse result = travelRecordService.update(member, 101L, request);

        assertThat(result.objectKeys()).isEmpty();
        verify(recordMediaRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.iterator().hasNext()
        ));
        verify(recordMediaRepository, never()).findByObjectKeyIn(anyList());
    }

    @Test
    void 없거나_다른_회원의_일지_수정을_거부한다() {
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                List.of()
        );
        when(travelRecordRepository.findByIdAndMemberId(101L, 10L))
                .thenReturn(Optional.empty());

        assertError(() -> travelRecordService.update(member, 101L, request), "TRAVEL_RECORD_NOT_FOUND");
        verify(regionResolver, never()).resolve("JP", null, null);
        verify(recordMediaRepository, never()).findByTravelRecordIdOrderBySortOrderAsc(101L);
    }

    @Test
    void 지역_필터_없이_일지_목록을_조회한다() {
        TravelRecord travelRecord = mock(TravelRecord.class);
        Region region = mock(Region.class);
        when(travelRecord.getId()).thenReturn(101L);
        when(travelRecord.getRegion()).thenReturn(region);
        Page<TravelRecord> expected = new PageImpl<>(List.of(travelRecord), PageRequest.of(0, 20), 1);
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(null), any(Pageable.class)))
                .thenReturn(expected);

        TravelRecordListResponse result = travelRecordService.findAll(member, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(travelRecordRepository).findByMemberIdAndOptionalTagId(eq(10L), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void 국가로_일지_목록을_조회한다() {
        Region korea = region(1L);
        Page<TravelRecord> expected = Page.empty();
        when(regionResolver.resolve("KR", null, null)).thenReturn(korea);
        when(travelRecordRepository.findByMemberIdAndCountryIdAndOptionalTagId(
                eq(10L), eq(1L), eq(null), any(Pageable.class)))
                .thenReturn(expected);

        assertThat(travelRecordService.findAll(member, "KR", null, null, null, 0, 20).items()).isEmpty();
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

        assertThat(travelRecordService.findAll(member, "KR", "49", null, null, 0, 20).items()).isEmpty();
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

        assertThat(travelRecordService.findAll(member, "KR", "49", "50110", null, 0, 20).items()).isEmpty();
    }

    @Test
    void 소유한_태그로_일지_목록을_필터링한다() {
        Page<TravelRecord> expected = Page.empty();
        when(travelRecordRepository.findByMemberIdAndOptionalTagId(eq(10L), eq(7L), any(Pageable.class)))
                .thenReturn(expected);

        TravelRecordListResponse result = travelRecordService.findAll(
                member, null, null, null, 7L, 0, 20
        );

        assertThat(result.items()).isEmpty();
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
