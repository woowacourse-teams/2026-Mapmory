package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private Member member;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, memberRepository, eventPublisher);
    }

    @Test
    void 이름을_정규화해_태그를_생성한다() {
        when(member.getId()).thenReturn(10L);
        memberIsLocked();
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
        memberIsLocked();
        when(tagRepository.countByMemberId(10L)).thenReturn(10L);

        assertError(() -> tagService.create(member, "연인"), "TAG_LIMIT_EXCEEDED");

        verify(tagRepository, never()).saveAndFlush(any(Tag.class));
    }

    @Test
    void 샵이_포함된_태그_이름을_거부한다() {
        assertError(() -> tagService.create(member, "#연인"), "VALIDATION_ERROR");

        verifyNoInteractions(tagRepository, memberRepository);
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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 이름_유니크_제약조건_위반을_비즈니스_예외로_변환한다() {
        when(member.getId()).thenReturn(10L);
        memberIsLocked();
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
        memberIsLocked();
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "친구")).thenReturn(false);
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException("FK 위반");
        when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(integrityViolation);

        assertThatThrownBy(() -> tagService.create(member, "친구"))
                .isSameAs(integrityViolation);
    }

    @Test
    void 개수를_세기_전에_회원을_잠근다() {
        when(member.getId()).thenReturn(10L);
        memberIsLocked();
        when(tagRepository.countByMemberId(10L)).thenReturn(10L);

        assertError(() -> tagService.create(member, "연인"), "TAG_LIMIT_EXCEEDED");

        InOrder order = inOrder(memberRepository, tagRepository);
        order.verify(memberRepository).findByIdForUpdate(10L);
        order.verify(tagRepository).countByMemberId(10L);
    }

    @Test
    void 회원_행이_없으면_만들지_않는다() {
        when(member.getId()).thenReturn(10L);
        when(memberRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertError(() -> tagService.create(member, "연인"), "INACTIVE_MEMBER");

        verifyNoInteractions(tagRepository);
    }

    @Test
    void 같은_이름이_이미_있으면_만들지_않고_이름_중복으로_거절한다() {
        when(member.getId()).thenReturn(10L);
        memberIsLocked();
        when(tagRepository.countByMemberId(10L)).thenReturn(0L);
        when(tagRepository.existsByMemberIdAndNameKey(10L, "친구")).thenReturn(true);

        assertError(() -> tagService.create(member, "친구"), "TAG_NAME_CONFLICT");

        verify(tagRepository, never()).saveAndFlush(any(Tag.class));
    }

    @Test
    void 수정이_이름_유니크_제약조건에_걸리면_이름_중복으로_바꾸고_캐시_무효화를_알리지_않는다() {
        // 미리 확인한 뒤 다른 요청이 같은 이름을 먼저 커밋하면, flush에서 DB 유니크 제약이 막는다.
        when(member.getId()).thenReturn(10L);
        Tag tag = Tag.of(member, "친구");
        ReflectionTestUtils.setField(tag, "id", 1L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByMemberIdAndNameKeyAndIdNot(10L, "연인", 1L)).thenReturn(false);
        ConstraintViolationException constraintViolation = mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName()).thenReturn("uk_tag_member_name_key");
        doThrow(new DataIntegrityViolationException("이름 중복", constraintViolation))
                .when(tagRepository).flush();

        assertError(() -> tagService.update(member, 1L, "연인"), "TAG_NAME_CONFLICT");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 이름을_바꾼_태그를_돌려준다() {
        when(member.getId()).thenReturn(10L);
        Tag tag = Tag.of(member, "친구");
        ReflectionTestUtils.setField(tag, "id", 1L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByMemberIdAndNameKeyAndIdNot(10L, "연인", 1L)).thenReturn(false);

        Tag updated = tagService.update(member, 1L, "연인");

        assertThat(updated).isSameAs(tag);
        assertThat(updated.getName()).isEqualTo("연인");
        verify(tagRepository).flush();
    }

    @Test
    void 회원의_태그를_저장소가_준_순서대로_돌려준다() {
        when(member.getId()).thenReturn(10L);
        Tag first = Tag.of(member, "친구");
        Tag second = Tag.of(member, "연인");
        when(tagRepository.findAllByMemberIdOrderByCreatedAtAscIdAsc(10L)).thenReturn(List.of(first, second));

        assertThat(tagService.findAll(member)).containsExactly(first, second);
    }

    @Test
    void 내_태그를_찾아_돌려준다() {
        when(member.getId()).thenReturn(10L);
        Tag tag = Tag.of(member, "친구");
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));

        assertThat(tagService.getOwnedTag(member, 1L)).isSameAs(tag);
    }

    @Test
    void 없거나_남의_태그면_태그_없음으로_거절한다() {
        // 남의 태그도 회원 id로 함께 찾으므로 없음과 같게 보인다. 존재를 드러내지 않는다.
        when(member.getId()).thenReturn(10L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.empty());

        assertError(() -> tagService.getOwnedTag(member, 1L), "TAG_NOT_FOUND");
    }

    private void memberIsLocked() {
        when(memberRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(member));
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }

    @Test
    void 태그를_삭제하면_그_회원_캐시를_비우도록_알린다() {
        Tag tag = mock(Tag.class);
        when(member.getId()).thenReturn(10L);
        when(tagRepository.findByIdAndMemberId(7L, 10L)).thenReturn(Optional.of(tag));

        tagService.delete(member, 7L);

        verify(tagRepository).delete(tag);
        verify(eventPublisher).publishEvent(new TagChangedEvent(10L));
    }

    @Test
    void 태그_이름을_바꾸면_그_회원_캐시를_비우도록_알린다() {
        when(member.getId()).thenReturn(10L);
        Tag tag = Tag.of(member, "친구");
        ReflectionTestUtils.setField(tag, "id", 1L);
        when(tagRepository.findByIdAndMemberId(1L, 10L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByMemberIdAndNameKeyAndIdNot(10L, "가족", 1L)).thenReturn(false);

        tagService.update(member, 1L, "가족");

        assertThat(tag.getName()).isEqualTo("가족");
        verify(eventPublisher).publishEvent(new TagChangedEvent(10L));
    }
}
