package com.mapmory.backend.tag;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.tag.dto.TagRequest;
import com.mapmory.backend.tag.dto.TagResponse;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private static final int MAX_TAGS_PER_MEMBER = 10;
    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional
    public TagResponse create(Member member, TagRequest request) {
        validateTagLimit(member.getId());

        Tag tag = Tag.of(member, request.name());
        validateUniqueNameForCreate(member.getId(), tag.getNameKey());

        return TagResponse.from(saveTag(tag));
    }

    @Transactional(readOnly = true)
    public List<TagResponse> findAll(Member member) {
        return tagRepository.findAllByMemberIdOrderByCreatedAtAscIdAsc(member.getId()).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse update(Member member, Long tagId, TagRequest request) {
        Tag tag = findOwnedTag(member, tagId);

        String nameKey = Tag.nameKeyOf(request.name());
        validateUniqueNameForUpdate(member.getId(), nameKey, tagId);
        tag.rename(request.name());

        flushTagChanges();
        return TagResponse.from(tag);
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
            throw new BusinessException(TagErrorCode.TAG_NAME_CONFLICT);
        }
    }

    private void flushTagChanges() {
        try {
            tagRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(TagErrorCode.TAG_NAME_CONFLICT);
        }
    }
}
