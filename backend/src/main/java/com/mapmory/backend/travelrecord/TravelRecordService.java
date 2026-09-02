package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import com.mapmory.backend.upload.service.UploadedObjectVerifier;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelRecordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String KOREA_COUNTRY_CODE = "KR";

    private final TravelRecordRepository travelRecordRepository;
    private final RegionResolver regionResolver;
    private final TravelRecordTagService travelRecordTagService;
    private final TagService tagService;
    private final OperationTimer operationTimer;
    private final TravelRecordAssembler travelRecordAssembler;
    private final Clock clock;
    private final UploadedObjectVerifier uploadedObjectVerifier;

    public TravelRecordService(
            TravelRecordRepository travelRecordRepository,
            RegionResolver regionResolver,
            TravelRecordTagService travelRecordTagService,
            TagService tagService,
            OperationTimer operationTimer,
            TravelRecordAssembler travelRecordAssembler,
            Clock clock,
            UploadedObjectVerifier uploadedObjectVerifier
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.regionResolver = regionResolver;
        this.travelRecordTagService = travelRecordTagService;
        this.tagService = tagService;
        this.operationTimer = operationTimer;
        this.travelRecordAssembler = travelRecordAssembler;
        this.clock = clock;
        this.uploadedObjectVerifier = uploadedObjectVerifier;
    }

    @Transactional
    public TravelRecord create(Member member, TravelRecordCommand command) {
        validateTravelDates(command.startDate(), command.endDate());
        validateTravelRecordRegion(command);
        List<String> objectKeys = command.objectKeys();
        TravelRecord.validateObjectKeys(objectKeys);
        uploadedObjectVerifier.verifyAllUploaded(objectKeys);
        Region region = resolveRegion(command);

        TravelRecord travelRecord = TravelRecord.of(
                member,
                region,
                command.title(),
                command.content(),
                command.startDate(),
                command.endDate()
        );
        travelRecord.synchronizeMedia(objectKeys);

        TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);
        travelRecordTagService.replace(member, savedTravelRecord, command.tagIds());

        return savedTravelRecord;
    }

    @Transactional(readOnly = true)
    public TravelRecordDetail findById(Member member, Long travelRecordId) {
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        return travelRecordAssembler.assembleDetail(travelRecord);
    }

    @Transactional
    public TravelRecordDetail update(
            Member member,
            Long travelRecordId,
            TravelRecordCommand command
    ) {
        validateTravelDates(command.startDate(), command.endDate());
        validateTravelRecordRegion(command);
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
        List<String> objectKeys = command.objectKeys();
        TravelRecord.validateObjectKeys(objectKeys);

        Region region = resolveRegion(command);
        List<String> newObjectKeys = travelRecord.newObjectKeys(objectKeys);
        validateObjectKeysAreAvailable(newObjectKeys);
        uploadedObjectVerifier.verifyAllUploaded(newObjectKeys);

        travelRecord.update(
                region,
                command.title(),
                command.content(),
                command.startDate(),
                command.endDate()
        );
        List<Tag> tags = travelRecordTagService.replace(member, travelRecord, command.tagIds());
        operationTimer.record(
                MonitoredOperation.MEDIA_SYNC,
                () -> {
                    travelRecord.synchronizeMedia(objectKeys);
                    travelRecordRepository.flush();
                    return travelRecord.getMedia();
                }
        );

        return travelRecordAssembler.assembleDetail(travelRecord, tags);
    }

    @Transactional
    public void delete(Member member, Long travelRecordId) {
        TravelRecord travelRecord = travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));

        travelRecordRepository.delete(travelRecord);
    }

    @Transactional(readOnly = true)
    public TravelRecordSummaries findAll(
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

        return travelRecordAssembler.assembleSummaries(travelRecords);
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

    private void validateTravelDates(LocalDate startDate, LocalDate endDate) {
        TravelPeriod.of(startDate, endDate).validateNotAfter(LocalDate.now(clock));
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private Region resolveRegion(TravelRecordCommand command) {
        return regionResolver.resolve(
                command.countryCode(),
                command.provinceCode(),
                command.districtCode()
        );
    }

    private void validateTravelRecordRegion(TravelRecordCommand command) {
        String countryCode = command.countryCode();
        String provinceCode = command.provinceCode();
        String districtCode = command.districtCode();
        validateRegionCodeFormat(countryCode, provinceCode, districtCode);

        if (KOREA_COUNTRY_CODE.equals(countryCode)) {
            if (provinceCode == null || districtCode == null) {
                throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED);
            }
            return;
        }

        if (provinceCode != null || districtCode != null) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_REGION_TYPE);
        }
    }

        private void validateObjectKeysAreAvailable(List<String> newObjectKeys) {
        if (!newObjectKeys.isEmpty()
                && travelRecordRepository.existsMediaByObjectKeyIn(newObjectKeys)) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }



}
