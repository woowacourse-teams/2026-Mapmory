package com.mapmory.backend.tag;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private static final int MAX_TAGS_PER_MEMBER = 10;
    private static final String TAG_NAME_UNIQUE_CONSTRAINT = "uk_tag_member_name_key";
    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional
    public Tag create(Member member, String name) {
        Tag tag = Tag.of(member, name);

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
        return tag;
    }

    @Transactional
    public void delete(Member member, Long tagId) {
        tagRepository.delete(findOwnedTag(member, tagId));
    }

    @Transactional(readOnly = true)
    public Tag getOwnedTag(Member member, Long tagId) {
        return findOwnedTag(member, tagId);
    }

    private Tag findOwnedTag(Member member, Long tagId) {
        return tagRepository.findByIdAndMemberId(tagId, member.getId())
                .orElseThrow(() -> new BusinessException(TagErrorCode.TAG_NOT_FOUND));
    }

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
