package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
    }

    @Test
    void 이름을_정규화해_태그를_생성한다() {
        when(member.getId()).thenReturn(10L);
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "date course"))
                .thenReturn(false);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            ReflectionTestUtils.setField(tag, "id", 1L);
            return tag;
        });

        Tag created = tagService.create(member, "  Date   Course  ");

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("Date Course");
        verify(tagRepository).existsByMemberIdAndNameKey(10L, "date course");
    }

    @Test
    void 회원당_태그가_10개면_생성을_거부한다() {
        when(member.getId()).thenReturn(10L);
        when(tagRepository.countByMemberId(10L)).thenReturn(10L);

        assertError(() -> tagService.create(member, "연인"), "TAG_LIMIT_EXCEEDED");

        verify(tagRepository, never()).saveAndFlush(any(Tag.class));
    }

    @Test
    void 샵이_포함된_태그_이름을_거부한다() {
        assertError(() -> tagService.create(member, "#연인"), "VALIDATION_ERROR");

        verifyNoInteractions(tagRepository);
    }

    @Test
    void 중복된_이름으로_수정할_때는_기존_이름을_유지한다() {
        when(member.getId()).thenReturn(10L);
        Tag tag = Tag.of(member, "친구");
        ReflectionTestUtils.setField(tag, "id", 1L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByMemberIdAndNameKeyAndIdNot(10L, "date course", 1L))
                .thenReturn(true);

        assertError(
                () -> tagService.update(member, 1L, "  Date   Course  "),
                "TAG_NAME_CONFLICT"
        );

        assertThat(tag.getName()).isEqualTo("친구");
    }

    @Test
    void 이름_유니크_제약조건_위반을_비즈니스_예외로_변환한다() {
        when(member.getId()).thenReturn(10L);
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "친구")).thenReturn(false);
        ConstraintViolationException constraintViolation = mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName()).thenReturn("uk_tag_member_name_key");
        DataIntegrityViolationException integrityViolation =
                new DataIntegrityViolationException("이름 중복", constraintViolation);
        when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(integrityViolation);

        assertError(() -> tagService.create(member, "친구"), "TAG_NAME_CONFLICT");
    }

    @Test
    void 다른_무결성_예외를_이름_중복으로_변환하지_않는다() {
        when(member.getId()).thenReturn(10L);
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "친구")).thenReturn(false);
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException("FK 위반");
        when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(integrityViolation);

        assertThatThrownBy(() -> tagService.create(member, "친구"))
                .isSameAs(integrityViolation);
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }
}
