package com.mapmory.backend.travelrecordtag;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagErrorCode;
import com.mapmory.backend.tag.TagRepository;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.TravelRecord;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelRecordTagService {
    private static final int MAX_TAGS_PER_RECORD = 5;
    private static final Comparator<Tag> TAG_ORDER = Comparator
            .comparing(Tag::getCreatedAt)
            .thenComparingLong(Tag::getId);

    private final TagRepository tagRepository;
    private final TravelRecordTagRepository travelRecordTagRepository;

    public TravelRecordTagService(
            TagRepository tagRepository,
            TravelRecordTagRepository travelRecordTagRepository
    ) {
        this.tagRepository = tagRepository;
        this.travelRecordTagRepository = travelRecordTagRepository;
    }

    @Transactional
    public List<TagSummaryResponse> replace(Member member, TravelRecord travelRecord, List<Long> requestedTagIds) {
        Set<Long> tagIds = validateAndCollectTagIds(requestedTagIds);
        List<Tag> tags = findOwnedTags(member.getId(), tagIds);

        replaceAssociations(travelRecord, tags);

        return toTagSummaryResponses(tags);
    }

    @Transactional(readOnly = true)
    public List<TagSummaryResponse> findByTravelRecordId(Long travelRecordId) {
        return travelRecordTagRepository.findTagsByTravelRecordId(travelRecordId).stream()
                .map(TagSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<TagSummaryResponse>> findByTravelRecordIds(Collection<Long> travelRecordIds) {
        if (travelRecordIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<TagSummaryResponse>> result = initializeEmptyResults(travelRecordIds);
        List<TravelRecordTag> associations =
                travelRecordTagRepository.findAllWithTagByTravelRecordIdIn(travelRecordIds);
        result.putAll(groupTagResponsesByTravelRecordId(associations));

        return result;
    }

    private Set<Long> validateAndCollectTagIds(List<Long> requestedTagIds) {
        List<Long> tagIds = requestedTagIds == null ? List.of() : requestedTagIds;
        validateTagCount(tagIds);

        Set<Long> uniqueTagIds = new HashSet<>(tagIds);
        validateNoDuplicateTagIds(tagIds, uniqueTagIds);

        return uniqueTagIds;
    }

    private void validateTagCount(List<Long> tagIds) {
        if (tagIds.size() > MAX_TAGS_PER_RECORD) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAGS);
        }
    }

    private void validateNoDuplicateTagIds(List<Long> tagIds, Set<Long> uniqueTagIds) {
        if (uniqueTagIds.size() != tagIds.size()) {
            throw new BusinessException(TagErrorCode.INVALID_TAG_IDS);
        }
    }

    private List<Tag> findOwnedTags(Long memberId, Set<Long> tagIds) {
        List<Tag> tags = tagRepository.findAllByMemberIdAndIdIn(memberId, tagIds);
        if (tags.size() != tagIds.size()) {
            throw new BusinessException(TagErrorCode.TAG_NOT_FOUND);
        }
        return tags;
    }

    private void replaceAssociations(TravelRecord travelRecord, List<Tag> tags) {
        travelRecordTagRepository.deleteByTravelRecordId(travelRecord.getId());
        travelRecordTagRepository.flush();
        travelRecordTagRepository.saveAll(createAssociations(travelRecord, tags));
    }

    private List<TravelRecordTag> createAssociations(TravelRecord travelRecord, List<Tag> tags) {
        return tags.stream()
                .map(tag -> TravelRecordTag.of(travelRecord, tag))
                .toList();
    }

    private List<TagSummaryResponse> toTagSummaryResponses(List<Tag> tags) {
        return tags.stream()
                .sorted(TAG_ORDER)
                .map(TagSummaryResponse::from)
                .toList();
    }

    private Map<Long, List<TagSummaryResponse>> initializeEmptyResults(Collection<Long> travelRecordIds) {
        Map<Long, List<TagSummaryResponse>> result = new HashMap<>();
        travelRecordIds.forEach(id -> result.put(id, List.of()));
        return result;
    }

    private Map<Long, List<TagSummaryResponse>> groupTagResponsesByTravelRecordId(
            List<TravelRecordTag> associations
    ) {
        return associations.stream()
                .collect(Collectors.groupingBy(
                        TravelRecordTag::getTravelRecordId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                association -> TagSummaryResponse.from(association.getTag()),
                                Collectors.toList()
                        )
                ));
    }
}
