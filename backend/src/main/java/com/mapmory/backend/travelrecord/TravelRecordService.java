package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.place.application.PlaceSelectionService;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TravelRecordService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TravelRecordRepository travelRecordRepository;
    private final RegionResolver regionResolver;
    private final TravelRecordTagService travelRecordTagService;
    private final TagService tagService;
    private final OperationTimer operationTimer;
    private final TravelRecordAssembler travelRecordAssembler;
    private final Clock clock;
    private final UploadedObjectVerifier uploadedObjectVerifier;
    private final PlaceLookupPort placeLookupPort;
    private final PlaceSelectionService placeSelectionService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public TravelRecordService(
            TravelRecordRepository travelRecordRepository,
            RegionResolver regionResolver,
            TravelRecordTagService travelRecordTagService,
            TagService tagService,
            OperationTimer operationTimer,
            TravelRecordAssembler travelRecordAssembler,
            Clock clock,
            UploadedObjectVerifier uploadedObjectVerifier,
            PlaceLookupPort placeLookupPort,
            PlaceSelectionService placeSelectionService,
            PlatformTransactionManager transactionManager
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.regionResolver = regionResolver;
        this.travelRecordTagService = travelRecordTagService;
        this.tagService = tagService;
        this.operationTimer = operationTimer;
        this.travelRecordAssembler = travelRecordAssembler;
        this.clock = clock;
        this.uploadedObjectVerifier = uploadedObjectVerifier;
        this.placeLookupPort = placeLookupPort;
        this.placeSelectionService = placeSelectionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * 외부 호출(Google 장소 조회, S3 업로드 확인)은 트랜잭션 밖에서 먼저 하고, DB 일만 트랜잭션으로 묶는다.
     * 트랜잭션은 시작할 때 커넥션을 빌려 끝날 때 돌려주므로, 안에서 외부를 기다리면 그동안 커넥션을 쥐고 있게 된다.
     */
    public TravelRecord create(Member member, TravelRecordCommand command) {
        validateTravelDates(command.startDate(), command.endDate());
        List<String> objectKeys = command.objectKeys();
        TravelRecord.validateObjectKeys(objectKeys);
        PlaceDetails place = resolvePlace(command);
        uploadedObjectVerifier.verifyAllUploaded(objectKeys);

        return transactionTemplate.execute(status -> {
            Region region = resolveRegion(command, place);
            TravelRecord travelRecord = TravelRecord.of(
                    member,
                    region,
                    command.title(),
                    command.content(),
                    command.startDate(),
                    command.endDate()
            );
            setPlace(travelRecord, place);
            travelRecord.synchronizeMedia(objectKeys);

            TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);
            travelRecordTagService.replace(member, savedTravelRecord, command.tagIds());

            return savedTravelRecord;
        });
    }

    @Transactional(readOnly = true)
    public TravelRecordDetail findById(Member member, Long travelRecordId) {
        TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);
        return travelRecordAssembler.assembleDetail(travelRecord);
    }

    /**
     * 외부 호출을 트랜잭션 밖에 두려고 읽기와 쓰기를 두 트랜잭션으로 나눈다.
     * 읽기에서 새로 추가된 사진을 구해 S3로 확인하고, 쓰기에서 기록을 다시 읽어 그사이 삭제됐으면 TRAVEL_RECORD_NOT_FOUND로 끝낸다.
     * 그사이 사진 목록이 바뀌어 확인하지 않은 키가 새 사진으로 보이더라도, 그 키는 읽을 때 기록에 붙어 있던 사진이라
     * 처음 붙일 때 확인을 거쳤고 S3에서 지우지 않으므로 다시 확인하지 않는다.
     */
    public TravelRecordDetail update(
            Member member,
            Long travelRecordId,
            TravelRecordCommand command
    ) {
        validateTravelDates(command.startDate(), command.endDate());
        List<String> objectKeys = command.objectKeys();
        TravelRecord.validateObjectKeys(objectKeys);
        PlaceDetails place = resolvePlace(command);

        List<String> newObjectKeys = readOnlyTransactionTemplate.execute(status -> {
            TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);
            List<String> keys = travelRecord.newObjectKeys(objectKeys);
            validateObjectKeysAreAvailable(keys);
            return keys;
        });
        uploadedObjectVerifier.verifyAllUploaded(newObjectKeys);

        return transactionTemplate.execute(status -> {
            TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);
            validateObjectKeysAreAvailable(travelRecord.newObjectKeys(objectKeys));
            Region region = resolveRegion(command, place);

            travelRecord.update(
                    region,
                    command.title(),
                    command.content(),
                    command.startDate(),
                    command.endDate()
            );
            setPlace(travelRecord, place);
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
        });
    }

    @Transactional
    public void delete(Member member, Long travelRecordId) {
        TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);

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
        validatePagination(page, size);
        Long memberId = member.getId();
        Pageable pageable = createPageable(page, size);
        if (tagId != null) {
            tagService.getOwnedTag(member, tagId);
        }

        Page<TravelRecord> travelRecords = regionResolver.resolveIfPresent(countryCode, provinceCode, districtCode)
                .map(region -> findInRegion(memberId, region, tagId, pageable))
                .orElseGet(() -> travelRecordRepository.findByMemberIdAndOptionalTagId(memberId, tagId, pageable));

        return travelRecordAssembler.assembleSummaries(travelRecords);
    }

    private Page<TravelRecord> findInRegion(Long memberId, Region region, Long tagId, Pageable pageable) {
        return switch (region.getRegionType()) {
            case COUNTRY -> travelRecordRepository.findByMemberIdAndCountryIdAndOptionalTagId(
                    memberId, region.getId(), tagId, pageable);
            case PROVINCE -> travelRecordRepository.findByMemberIdAndProvinceIdAndOptionalTagId(
                    memberId, region.getId(), tagId, pageable);
            case DISTRICT -> travelRecordRepository.findByMemberIdAndRegionIdAndOptionalTagId(
                    memberId, region.getId(), tagId, pageable);
        };
    }

    private TravelRecord findOwnedTravelRecord(Member member, Long travelRecordId) {
        return travelRecordRepository.findByIdAndMemberId(travelRecordId, member.getId())
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
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

    private Region resolveRegion(TravelRecordCommand command, PlaceDetails place) {
        if (!command.hasRegion()) {
            if (place == null) {
                throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED);
            }
            Region suggestion = placeSelectionService.suggestedRegion(place);
            if (suggestion == null) {
                throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED,
                        "장소의 지역을 자동으로 찾지 못했습니다. 지역을 직접 선택해 주세요.");
            }
            return suggestion;
        }
        return regionResolver.resolve(command.countryCode(), command.provinceCode(), command.districtCode());
    }

    private PlaceDetails resolvePlace(TravelRecordCommand command) {
        if (command.placeId() == null) {
            return null;
        }
        // 지역이 없으면 장소로 지역을 정해야 해서 제공자에 다시 조회한다. 있으면 기록에 붙일 값만 받는다.
        // 지역이 있으면 장소 국가를 모르므로 요청 지역과 맞는지 확인하지 않는다.
        return !command.hasRegion()
                ? placeLookupPort.findById(command.placeId())
                : placeLookupPort.findForRecord(command.placeId());
    }

    private void setPlace(TravelRecord travelRecord, PlaceDetails place) {
        travelRecord.setPlace(place == null ? null : new RecordedPlace(
                placeLookupPort.providerCode(), place.placeId(), place.name(),
                place.attribution(), place.attributionUrl()));
    }

        private void validateObjectKeysAreAvailable(List<String> newObjectKeys) {
        if (!newObjectKeys.isEmpty()
                && travelRecordRepository.existsMediaByObjectKeyIn(newObjectKeys)) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }



}
