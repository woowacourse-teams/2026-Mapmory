package com.mapmory.backend.travelrecordtag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagRepository;
import com.mapmory.backend.travelrecord.TravelRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRecordTagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TravelRecordTagRepository travelRecordTagRepository;

    @Mock
    private Member member;

    private TravelRecordTagService service;

    // 태그 개수·중복 규칙이 TravelRecord 애그리거트에 있으므로 실제 엔티티를 쓴다.
    private TravelRecord travelRecord;

    @BeforeEach
    void setUp() {
        service = new TravelRecordTagService(tagRepository, travelRecordTagRepository);
        lenient().when(member.getId()).thenReturn(10L);
        travelRecord = TravelRecord.of(
                member,
                Region.of(null, null, "JP", "일본", RegionType.COUNTRY),
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null
        );
        ReflectionTestUtils.setField(travelRecord, "id", 100L);
    }

    @Test
    void 기록당_태그가_5개를_넘으면_거부한다() {
        assertError(
                () -> service.replace(member, travelRecord, List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                "TOO_MANY_TAGS"
        );

        verify(tagRepository, never()).findAllByMemberIdAndIdInOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    @Test
    void 중복된_태그_ID를_거부한다() {
        assertError(() -> service.replace(member, travelRecord, List.of(1L, 1L)), "VALIDATION_ERROR");
    }

    @Test
    void 회원이_소유하지_않은_태그를_거부한다() {
        when(tagRepository.findAllByMemberIdAndIdInOrderByCreatedAtAscIdAsc(10L, java.util.Set.of(1L, 2L)))
                .thenReturn(List.of(tag(1L, "연인", LocalDateTime.now())));

        assertError(() -> service.replace(member, travelRecord, List.of(1L, 2L)), "TAG_NOT_FOUND");

        verify(travelRecordTagRepository, never())
                .deleteAllByTravelRecordIdInBulk(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 소유한_태그로_기록의_연결을_교체한다() {
        LocalDateTime now = LocalDateTime.now();
        Tag later = tag(2L, "라멘맛집", now.plusSeconds(1));
        Tag earlier = tag(1L, "연인", now);
        when(tagRepository.findAllByMemberIdAndIdInOrderByCreatedAtAscIdAsc(10L, java.util.Set.of(1L, 2L)))
                .thenReturn(List.of(earlier, later));

        List<Tag> tags = service.replace(member, travelRecord, List.of(2L, 1L));

        assertThat(tags).extracting(Tag::getId).containsExactly(1L, 2L);
        verify(travelRecordTagRepository).deleteAllByTravelRecordIdInBulk(100L);
        verify(travelRecordTagRepository).saveAll(anyList());
    }

    private Tag tag(Long id, String name, LocalDateTime createdAt) {
        Tag tag = Tag.of(member, name);
        ReflectionTestUtils.setField(tag, "id", id);
        ReflectionTestUtils.setField(tag, "createdAt", createdAt);
        return tag;
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }
}
