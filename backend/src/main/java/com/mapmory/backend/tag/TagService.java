package com.mapmory.backend.tag;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private static final int MAX_TAGS_PER_MEMBER = 10;
    private static final String TAG_NAME_UNIQUE_CONSTRAINT = "uk_tag_member_name_key";
    private final TagRepository tagRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TagService(TagRepository tagRepository, MemberRepository memberRepository,
                      ApplicationEventPublisher eventPublisher) {
        this.tagRepository = tagRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 회원당 {@value #MAX_TAGS_PER_MEMBER}개는 도메인 규칙이라 동시에 만들어도 넘지 않게 한다.
     *
     * <p>개수를 센 뒤 넣으므로, 잠그지 않으면 두 요청이 모두 9개를 보고 넣어 11개가 된다(읽은 다음 쓰기).
     * 세기 전에 회원 행을 FOR UPDATE로 잠가 같은 회원의 생성을 차례로 처리한다. 늦은 요청은 앞 요청이 커밋된 뒤 세므로 10개를 본다.
     * 잠금이 트랜잭션의 첫 DB 조회여야 한다. REPEATABLE READ의 스냅샷은 첫 일반 조회 때 생기므로, 그 전에 다른 조회가 있으면
     * 잠근 뒤에도 앞 요청이 넣은 태그를 보지 못한다. 이름 검증({@link Tag#of})은 DB를 쓰지 않아 앞에 둔다.
     *
     * <p>태그 INSERT는 FK 확인으로 어차피 회원 행을 공유 잠금하므로, 순서는 회원 → 태그로 ADR 0028의 "회원 먼저"와 같다.
     */
    @Transactional
    public Tag create(Member member, String name) {
        Tag tag = Tag.of(member, name);

        lockMember(member.getId());
        validateTagLimit(member.getId());
        validateUniqueNameForCreate(member.getId(), tag.getNameKey());

        return saveTag(tag);
    }

    @Transactional(readOnly = true)
    public List<Tag> findAll(Member member) {
        return tagRepository.findAllByMemberIdOrderByCreatedAtAscIdAsc(member.getId()).stream()
                .toList();
    }

    @Transactional
    public Tag update(Member member, Long tagId, String name) {
        Tag tag = findOwnedTag(member, tagId);

        String nameKey = Tag.nameKeyOf(name);
        validateUniqueNameForUpdate(member.getId(), nameKey, tagId);
        tag.rename(name);

        flushTagChanges();
        eventPublisher.publishEvent(new TagChangedEvent(member.getId()));
        return tag;
    }

    @Transactional
    public void delete(Member member, Long tagId) {
        tagRepository.delete(findOwnedTag(member, tagId));
        eventPublisher.publishEvent(new TagChangedEvent(member.getId()));
    }

    @Transactional(readOnly = true)
    public Tag getOwnedTag(Member member, Long tagId) {
        return findOwnedTag(member, tagId);
    }

    private Tag findOwnedTag(Member member, Long tagId) {
        return tagRepository.findByIdAndMemberId(tagId, member.getId())
                .orElseThrow(() -> new BusinessException(TagErrorCode.TAG_NOT_FOUND));
    }

    // 회원 행이 없으면 ActiveMemberGuard와 같이 INACTIVE_MEMBER다. 활동 중인지는 여기서 보지 않는다(잠금만 담당).
    private void lockMember(Long memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }

    // 임시: 뮤테이션 테스트 연동 시험용 주석. 시험 뒤 되돌린다.
    private void validateTagLimit(Long memberId) {
        if (tagRepository.countByMemberId(memberId) >= MAX_TAGS_PER_MEMBER) {
            throw new BusinessException(TagErrorCode.TAG_LIMIT_EXCEEDED);
        }
    }

    private void validateUniqueNameForCreate(Long memberId, String nameKey) {
        boolean exists = tagRepository.existsByMemberIdAndNameKey(memberId, nameKey);
        throwIfNameExists(exists);
    }

    private void validateUniqueNameForUpdate(Long memberId, String nameKey, Long tagId) {
        boolean exists = tagRepository.existsByMemberIdAndNameKeyAndIdNot(memberId, nameKey, tagId);
        throwIfNameExists(exists);
    }

    private void throwIfNameExists(boolean exists) {
        if (exists) {
            throw new BusinessException(TagErrorCode.TAG_NAME_CONFLICT);
        }
    }

    private Tag saveTag(Tag tag) {
        try {
            return tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    private void flushTagChanges() {
        try {
            tagRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    private RuntimeException translateIntegrityViolation(DataIntegrityViolationException exception) {
        if (isTagNameUniqueConstraintViolation(exception)) {
            return new BusinessException(TagErrorCode.TAG_NAME_CONFLICT);
        }
        return exception;
    }

    private boolean isTagNameUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && TAG_NAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
