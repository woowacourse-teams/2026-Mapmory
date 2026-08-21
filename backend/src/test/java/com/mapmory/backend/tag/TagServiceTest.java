package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.tag.dto.TagRequest;
import com.mapmory.backend.tag.dto.TagResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private Member member;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository);
        when(member.getId()).thenReturn(10L);
    }

    @Test
    void 이름을_정규화해_태그를_생성한다() {
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "date course"))
                .thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            ReflectionTestUtils.setField(tag, "id", 1L);
            return tag;
        });

        TagResponse response = tagService.create(member, new TagRequest("  Date   Course  "));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Date Course");
        verify(tagRepository).existsByMemberIdAndNameKey(10L, "date course");
    }

    @Test
    void 회원당_태그가_10개면_생성을_거부한다() {
        when(tagRepository.countByMemberId(10L)).thenReturn(10L);

        assertError(() -> tagService.create(member, new TagRequest("연인")), "TAG_LIMIT_EXCEEDED");

        verify(tagRepository, never()).saveAndFlush(any(Tag.class));
    }

    @Test
    void 샵이_포함된_태그_이름을_거부한다() {
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);

        assertError(() -> tagService.create(member, new TagRequest("#연인")), "VALIDATION_ERROR");
    }

    @Test
    void 중복된_이름으로_수정할_때는_기존_이름을_유지한다() {
        Tag tag = Tag.of(member, "친구");
        ReflectionTestUtils.setField(tag, "id", 1L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByMemberIdAndNameKeyAndIdNot(10L, "date course", 1L))
                .thenReturn(true);

        assertError(
                () -> tagService.update(member, 1L, new TagRequest("  Date   Course  ")),
                "TAG_NAME_CONFLICT"
        );

        assertThat(tag.getName()).isEqualTo("친구");
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }
}
