package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.recordmedia.RecordMedia;
import com.mapmory.backend.recordmedia.RecordMediaRepository;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordDetailResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordListResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelRecordService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TravelRecordRepository travelRecordRepository;
    private final RegionResolver regionResolver;
    private final RecordMediaRepository recordMediaRepository;
    private final TravelRecordTagService travelRecordTagService;
    private final TagService tagService;

    public TravelRecordService(
            TravelRecordRepository travelRecordRepository,
            RegionResolver regionResolver,
            RecordMediaRepository recordMediaRepository,
            TravelRecordTagService travelRecordTagService,
            TagService tagService
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.regionResolver = regionResolver;
        this.recordMediaRepository = recordMediaRepository;
        this.travelRecordTagService = travelRecordTagService;
        this.tagService = tagService;
    }

    @Transactional
    public TravelRecord create(Member member, TravelRecordRequest request) {
        Region region = regionResolver.resolve(
                request.countryCode(),
                request.provinceCode(),
                request.districtCode()
        );

        TravelRecord travelRecord = TravelRecord.of(
                member,
                region,
                request.title(),
                request.content(),
                request.startDate(),
                request.endDate()
        );

        TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);
        travelRecordTagService.replace(member, savedTravelRecord, request.tagIds());

        List<String> objectKeys = request.objectKeys() == null ? List.of() : request.objectKeys();

        // TODO : save or saveAll 결정하고 적용하기
        for (int index = 0; index < objectKeys.size(); index++) {
            RecordMedia recordMedia = RecordMedia.of(
                    savedTravelRecord,
                    objectKeys.get(index),
                    null,
                    index
            );

            recordMediaRepository.save(recordMedia);
        }

        return savedTravelRecord;
    }

    @Transactional(readOnly = true)
    public TravelRecordDetailResponse findById(Member member, Long travelRecordId) {
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        List<RecordMedia> recordMedia = recordMediaRepository
                .findByTravelRecordIdOrderBySortOrderAsc(travelRecordId);

        return TravelRecordDetailResponse.from(
                travelRecord,
                recordMedia,
                travelRecordTagService.findByTravelRecordId(travelRecordId)
        );
    }

    @Transactional
    public TravelRecordDetailResponse update(
            Member member,
            Long travelRecordId,
            TravelRecordRequest request
    ) {
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        List<String> objectKeys = request.objectKeys() == null ? List.of() : request.objectKeys();
        validateUniqueObjectKeys(objectKeys);

        Region region = resolveRegion(request);
        List<RecordMedia> existingMedia = recordMediaRepository
                .findByTravelRecordIdOrderBySortOrderAsc(travelRecordId);
        validateObjectKeysAreAvailable(objectKeys, existingMedia);

        travelRecord.update(
                region,
                request.title(),
                request.content(),
                request.startDate(),
                request.endDate()
        );
        List<RecordMedia> updatedMedia = synchronizeMedia(travelRecord, existingMedia, objectKeys);
        List<TagSummaryResponse> tags = travelRecordTagService.replace(member, travelRecord, request.tagIds());

        travelRecordRepository.flush();

        return TravelRecordDetailResponse.from(travelRecord, updatedMedia, tags);
    }

    @Transactional
    public void delete(Member member, Long travelRecordId) {
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));

        travelRecordRepository.delete(travelRecord);
    }

    @Transactional(readOnly = true)
    public TravelRecordListResponse findAll(
            Member member,
            String countryCode,
            String provinceCode,
            String districtCode,
            Long tagId,
            int page,
            int size
    ) {
        validateRegionCodeFormat(countryCode, provinceCode, districtCode);
        validateRegionFilterHierarchy(countryCode, provinceCode, districtCode);
        validatePagination(page, size);
        Long memberId = member.getId();
        Pageable pageable = createPageable(page, size);
        if (tagId != null) {
            tagService.getOwnedTag(member, tagId);
        }

        Page<TravelRecord> travelRecords;

        if (countryCode == null) {
            travelRecords = travelRecordRepository.findByMemberIdAndOptionalTagId(memberId, tagId, pageable);
        } else {
            Region region = regionResolver.resolve(countryCode, provinceCode, districtCode);
            if (provinceCode == null) {
                travelRecords = travelRecordRepository.findByMemberIdAndCountryIdAndOptionalTagId(
                        memberId,
                        region.getId(),
                        tagId,
                        pageable
                );
            } else if (districtCode == null) {
                travelRecords = travelRecordRepository.findByMemberIdAndProvinceIdAndOptionalTagId(
                        memberId,
                        region.getId(),
                        tagId,
                        pageable
                );
            } else {
                travelRecords = travelRecordRepository.findByMemberIdAndRegionIdAndOptionalTagId(
                        memberId,
                        region.getId(),
                        tagId,
                        pageable
                );
            }
        }

        Map<Long, List<TagSummaryResponse>> tagsByTravelRecordId =
                travelRecordTagService.findByTravelRecordIds(
                        travelRecords.getContent().stream().map(TravelRecord::getId).toList()
                );
        return TravelRecordListResponse.from(
                travelRecords,
                tagsByTravelRecordId
        );
    }

    private void validateRegionFilterHierarchy(
            String countryCode,
            String provinceCode,
            String districtCode
    ) {
        if (countryCode == null && (provinceCode != null || districtCode != null)) {
            throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED);
        }

        if (provinceCode == null && districtCode != null) {
            throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED);
        }
    }

    private void validateRegionCodeFormat(
            String countryCode,
            String provinceCode,
            String districtCode
    ) {
        if ((countryCode != null && !countryCode.matches("[A-Z]{2}"))
                || isBlank(provinceCode)
                || isBlank(districtCode)) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_REGION_CODE);
        }
    }

    private boolean isBlank(String value) {
        return value != null && value.isBlank();
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(
                    TravelRecordErrorCode.INVALID_PAGINATION,
                    "page는 0 이상이고 size는 1 이상 %d 이하여야 합니다.".formatted(MAX_PAGE_SIZE)
            );
        }
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private Region resolveRegion(TravelRecordRequest request) {
        return regionResolver.resolve(
                request.countryCode(),
                request.provinceCode(),
                request.districtCode()
        );
    }

    private void validateUniqueObjectKeys(List<String> objectKeys) {
        if (new HashSet<>(objectKeys).size() != objectKeys.size()) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }

    private void validateObjectKeysAreAvailable(
            List<String> objectKeys,
            List<RecordMedia> existingMedia
    ) {
        Set<String> existingObjectKeys = existingMedia.stream()
                .map(RecordMedia::getObjectKey)
                .collect(Collectors.toSet());
        List<String> newObjectKeys = objectKeys.stream()
                .filter(objectKey -> !existingObjectKeys.contains(objectKey))
                .toList();

        if (!newObjectKeys.isEmpty()
                && !recordMediaRepository.findByObjectKeyIn(newObjectKeys).isEmpty()) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }

    private List<RecordMedia> synchronizeMedia(
            TravelRecord travelRecord,
            List<RecordMedia> existingMedia,
            List<String> objectKeys
    ) {
        Map<String, RecordMedia> existingMediaByObjectKey = new HashMap<>();
        for (RecordMedia recordMedia : existingMedia) {
            existingMediaByObjectKey.put(recordMedia.getObjectKey(), recordMedia);
        }

        List<RecordMedia> updatedMedia = new ArrayList<>();
        for (int index = 0; index < objectKeys.size(); index++) {
            String objectKey = objectKeys.get(index);
            RecordMedia recordMedia = existingMediaByObjectKey.remove(objectKey);
            if (recordMedia == null) {
                recordMedia = RecordMedia.of(travelRecord, objectKey, null, index);
            } else {
                recordMedia.updateSortOrder(index);
            }
            updatedMedia.add(recordMedia);
        }

        recordMediaRepository.deleteAll(existingMediaByObjectKey.values());
        return recordMediaRepository.saveAll(updatedMedia);
    }

}
