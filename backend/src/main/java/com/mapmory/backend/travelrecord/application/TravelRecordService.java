package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.common.time.ServiceTime;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.region.application.RegionResolver;
import com.mapmory.backend.region.application.RegionSuggester;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagRepository;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecord.TravelRecordChangedEvent;
import com.mapmory.backend.travelrecord.api.TravelRecordDeleted;
import com.mapmory.backend.travelrecord.application.listcache.TravelRecordListCache;
import com.mapmory.backend.travelrecord.domain.ObjectKeys;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.PhotoCaptureDates;
import com.mapmory.backend.travelrecord.domain.RecordTagIds;
import com.mapmory.backend.travelrecord.domain.RecordedPlace;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import com.mapmory.backend.upload.service.UploadedObjectVerifier;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final OperationTimer operationTimer;
    private final TravelRecordAssembler travelRecordAssembler;
    private final Clock clock;
    private final UploadedObjectVerifier uploadedObjectVerifier;
    private final PlaceLookupPort placeLookupPort;
    private final RegionSuggester regionSuggester;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final TravelRecordListCache travelRecordListCache;
    private final ActiveMemberGuard activeMemberGuard;
    private final OrphanedMediaRepository orphanedMediaRepository;
    private final DerivedObjectKeys derivedObjectKeys;
    private final PhotoRecordNotificationOutboxWriter notificationOutboxWriter;

    public TravelRecordService(
            TravelRecordRepository travelRecordRepository,
            RegionResolver regionResolver,
            TagRepository tagRepository,
            TagService tagService,
            OperationTimer operationTimer,
            TravelRecordAssembler travelRecordAssembler,
            Clock clock,
            UploadedObjectVerifier uploadedObjectVerifier,
            PlaceLookupPort placeLookupPort,
            RegionSuggester regionSuggester,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher,
            TravelRecordListCache travelRecordListCache,
            ActiveMemberGuard activeMemberGuard,
            OrphanedMediaRepository orphanedMediaRepository,
            DerivedObjectKeys derivedObjectKeys,
            PhotoRecordNotificationOutboxWriter notificationOutboxWriter
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.regionResolver = regionResolver;
        this.tagRepository = tagRepository;
        this.tagService = tagService;
        this.operationTimer = operationTimer;
        this.travelRecordAssembler = travelRecordAssembler;
        this.clock = clock;
        this.uploadedObjectVerifier = uploadedObjectVerifier;
        this.placeLookupPort = placeLookupPort;
        this.regionSuggester = regionSuggester;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.travelRecordListCache = travelRecordListCache;
        this.activeMemberGuard = activeMemberGuard;
        this.orphanedMediaRepository = orphanedMediaRepository;
        this.derivedObjectKeys = derivedObjectKeys;
        this.notificationOutboxWriter = notificationOutboxWriter;
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * 외부 호출(Google 장소 조회, S3 업로드 확인)은 트랜잭션 밖에서 먼저 하고, DB 일만 트랜잭션으로 묶는다.
     * 트랜잭션은 시작할 때 커넥션을 빌려 끝날 때 돌려주므로, 안에서 외부를 기다리면 그동안 커넥션을 쥐고 있게 된다.
     *
     * <p>탈퇴 뒤 남은 access token으로는 만들지 못한다. 회원 확인은 커넥션을 더 빌리지 않도록 트랜잭션 안에서 한다.
     */
    public TravelRecord create(Member member, TravelRecordCommand command) {
        LocalDate today = ServiceTime.today(clock);
        TravelRecord.validatePeriod(command.startDate(), command.endDate(), today);
        ObjectKeys objectKeys = ObjectKeys.from(command.objectKeys());
        PhotoCaptureDates captureDates = PhotoCaptureDates.from(command.capturedDates(), today);
        RecordTagIds tagIds = RecordTagIds.from(command.tagIds());
        PlaceDetails place = resolvePlace(command);
        uploadedObjectVerifier.verifyAllUploaded(member.getId(), objectKeys.values());

        return transactionTemplate.execute(status -> {
            activeMemberGuard.require(member.getId());
            validateObjectKeysAreAvailable(objectKeys.values());
            Region region = resolveRegion(command, place);
            TravelRecord travelRecord = TravelRecord.of(
                    member,
                    region,
                    command.title(),
                    command.content(),
                    command.startDate(),
                    command.endDate(),
                    today
            );
            setPlace(travelRecord, place);
            travelRecord.synchronizeMedia(objectKeys, captureDates);
            travelRecord.retag(tagIds, findTags(member, tagIds));

            TravelRecord savedTravelRecord = travelRecordRepository.save(travelRecord);
            notificationOutboxWriter.save(savedTravelRecord.getId(), member.getId(), command.pushTokenId());
            eventPublisher.publishEvent(new TravelRecordChangedEvent(member.getId()));

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
     * 처음 붙일 때 확인을 거쳤으므로 다시 확인하지 않는다. 떨어져 나간 키의 객체는 정리 배치가 하루 한 번 지우므로(#162),
     * 읽기와 쓰기 사이의 몇 초에 배치가 지운 드문 경우에만 객체가 없는 키가 붙을 수 있다.
     */
    public TravelRecordDetail update(
            Member member,
            Long travelRecordId,
            TravelRecordCommand command
    ) {
        LocalDate today = ServiceTime.today(clock);
        TravelRecord.validatePeriod(command.startDate(), command.endDate(), today);
        ObjectKeys objectKeys = ObjectKeys.from(command.objectKeys());
        PhotoCaptureDates captureDates = PhotoCaptureDates.from(command.capturedDates(), today);
        RecordTagIds tagIds = RecordTagIds.from(command.tagIds());
        PlaceDetails place = resolvePlace(command);

        List<String> newObjectKeys = readOnlyTransactionTemplate.execute(status -> {
            TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);
            List<String> keys = travelRecord.newObjectKeys(objectKeys);
            validateObjectKeysAreAvailable(keys);
            return keys;
        });
        uploadedObjectVerifier.verifyAllUploaded(member.getId(), newObjectKeys);

        return transactionTemplate.execute(status -> {
            TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);
            validateObjectKeysAreAvailable(travelRecord.newObjectKeys(objectKeys));
            Region region = resolveRegion(command, place);

            travelRecord.update(
                    region,
                    command.title(),
                    command.content(),
                    command.startDate(),
                    command.endDate(),
                    today
            );
            setPlace(travelRecord, place);
            List<String> removedKeys = travelRecord.mediaKeysRemovedBy(objectKeys);
            operationTimer.record(
                    MonitoredOperation.MEDIA_SYNC,
                    () -> {
                        travelRecord.synchronizeMedia(objectKeys, captureDates);
                        travelRecordRepository.flush();
                        return travelRecord.getMedia();
                    }
            );
            enqueueOrphaned(removedKeys);
            // 태그 변경은 커밋 때 함께 나간다. 타이머 안에서 바꾸면 MEDIA_SYNC에 태그 쓰기가 섞인다.
            List<Tag> tags = findTags(member, tagIds);
            travelRecord.retag(tagIds, tags);
            eventPublisher.publishEvent(new TravelRecordChangedEvent(member.getId()));

            return travelRecordAssembler.assembleDetail(travelRecord, tags);
        });
    }

    @Transactional
    public void delete(Member member, Long travelRecordId) {
        TravelRecord travelRecord = findOwnedTravelRecord(member, travelRecordId);

        enqueueOrphaned(travelRecord.allMediaKeys());
        travelRecordRepository.delete(travelRecord);
        eventPublisher.publishEvent(new TravelRecordChangedEvent(member.getId()));
        eventPublisher.publishEvent(new TravelRecordDeleted(travelRecordId, member.getId()));
    }

    /**
     * 캐시에 있으면 DB에 가지 않고 트랜잭션도 열지 않는다. 썸네일 URL만 매번 새로 서명한다.
     * 캐시에 있다는 건 같은 회원이 같은 조건으로 검증을 통과했다는 뜻이다. 지역은 바뀌지 않고,
     * 태그가 바뀌거나 삭제되면 그 회원 캐시를 비우므로 검증을 다시 하지 않는다.
     */
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
        TravelRecordListCache.Key key = new TravelRecordListCache.Key(
                member.getId(), countryCode, provinceCode, districtCode, tagId, page, size);
        TravelRecordListPage listPage = travelRecordListCache.get(key, () -> readOnlyTransactionTemplate.execute(
                status -> loadListPage(member, countryCode, provinceCode, districtCode, tagId, page, size)));
        return travelRecordAssembler.signThumbnails(listPage);
    }

    private TravelRecordListPage loadListPage(
            Member member,
            String countryCode,
            String provinceCode,
            String districtCode,
            Long tagId,
            int page,
            int size
    ) {
        Long memberId = member.getId();
        Pageable pageable = createPageable(page, size);
        if (tagId != null) {
            tagService.getOwnedTag(member, tagId);
        }

        Page<TravelRecord> travelRecords = regionResolver.resolveIfPresent(countryCode, provinceCode, districtCode)
                .map(region -> travelRecordRepository.findByMemberIdInRegion(memberId, region, tagId, pageable))
                .orElseGet(() -> travelRecordRepository.findByMemberIdAndOptionalTagId(memberId, tagId, pageable));

        return travelRecordAssembler.assembleListPage(travelRecords);
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
            return regionSuggester.suggest(place.address())
                    .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.REGION_REQUIRED,
                            "장소의 지역을 자동으로 찾지 못했습니다. 지역을 직접 선택해 주세요."));
        }
        return regionResolver.resolve(command.countryCode(), command.provinceCode(), command.districtCode());
    }

    private PlaceDetails resolvePlace(TravelRecordCommand command) {
        if (command.placeId() == null) {
            return null;
        }
        // 지역이 없으면 장소로 지역을 정해야 해서 제공자에 다시 조회한다. 있으면 다시 조회하지 않고 ID와 출처만 붙인다.
        // 이때 장소 국가를 모르므로 요청 지역과 맞는지 확인하지 않는다. 앱을 고쳐 다른 나라 장소를 붙여도 그 회원의 기록에만 영향이 간다.
        PlaceId placeId = PlaceId.from(command.placeId());
        return !command.hasRegion()
                ? placeLookupPort.findDetails(placeId, null)
                : PlaceDetails.withoutLookup(placeId, placeLookupPort.provider());
    }

    private void setPlace(TravelRecord travelRecord, PlaceDetails place) {
        travelRecord.setPlace(place == null ? null : new RecordedPlace(
                place.provider().code(), place.id().value(), place.name(),
                place.provider().attribution(), place.provider().attributionUrl()));
    }

    /**
     * 회원의 태그만 조회한다. 애그리거트도 소유를 확인하지만, 남의 태그가 있는지 드러내지 않도록 조회부터 회원으로 거른다.
     */
    private List<Tag> findTags(Member member, RecordTagIds tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllByMemberIdAndIdInOrderByCreatedAtAscIdAsc(member.getId(), tagIds.values());
    }

    private void validateObjectKeysAreAvailable(List<String> newObjectKeys) {
        if (!newObjectKeys.isEmpty()
                && travelRecordRepository.existsMediaByObjectKeyIn(newObjectKeys)) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }

    // 썸네일 워커(렌더러)가 이 트랜잭션과 겹쳐 thumb_key를 채울 수 있다. 여기서 읽은 키에는 그 썸네일이 없으므로,
    // 원본에서 만들어질 썸네일 키도 함께 넣는다. 없는 객체를 지우는 것은 S3에서 성공한다(ADR 0029).
    private void enqueueOrphaned(Collection<String> keys) {
        keys.stream()
                .flatMap(key -> Stream.concat(Stream.of(key), derivedObjectKeys.thumbnailOf(key).stream()))
                .distinct()
                .forEach(orphanedMediaRepository::enqueue);
    }

}
