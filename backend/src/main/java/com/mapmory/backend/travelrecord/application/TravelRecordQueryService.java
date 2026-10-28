package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.travelrecord.api.RecordBrief;
import com.mapmory.backend.travelrecord.api.RecordView;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import com.mapmory.backend.travelrecord.domain.RecordMedia;
import com.mapmory.backend.travelrecord.domain.RecordedPlace;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TravelRecordQuery}의 구현. 기록은 한 번, 사진은 {@code findMediaByTravelRecordIdIn} 한 번으로 묶어 읽는다.
 *
 * <p>서명은 서버 안의 계산이라 쿼리가 아니다. 썸네일 규칙은 기록 목록 화면과 같다(썸네일 키가 없으면 원본).
 */
@Service
@Transactional(readOnly = true)
public class TravelRecordQueryService implements TravelRecordQuery {

    private final TravelRecordRepository travelRecordRepository;
    private final RecordMediaUrlService recordMediaUrlService;

    public TravelRecordQueryService(TravelRecordRepository travelRecordRepository,
                                    RecordMediaUrlService recordMediaUrlService) {
        this.travelRecordRepository = travelRecordRepository;
        this.recordMediaUrlService = recordMediaUrlService;
    }

    @Override
    public Optional<RecordBrief> findOwned(Long travelRecordId, Long memberId) {
        return travelRecordRepository.findByIdAndMemberId(travelRecordId, memberId)
                .map(travelRecord -> toBrief(travelRecord,
                        travelRecordRepository.findMediaByTravelRecordIdIn(List.of(travelRecord.getId()))));
    }

    @Override
    public List<RecordBrief> briefsOf(Collection<Long> travelRecordIds) {
        if (travelRecordIds.isEmpty()) {
            return List.of();
        }
        List<TravelRecord> travelRecords = travelRecordRepository.findAllByIdIn(travelRecordIds);
        Map<Long, List<RecordMedia>> media = mediaByTravelRecordId(travelRecordIds);
        return travelRecords.stream()
                .map(travelRecord -> toBrief(travelRecord, media.getOrDefault(travelRecord.getId(), List.of())))
                .toList();
    }

    @Override
    public List<RecordView> viewsOf(Collection<Long> travelRecordIds) {
        if (travelRecordIds.isEmpty()) {
            return List.of();
        }
        List<TravelRecord> travelRecords = travelRecordRepository.findAllByIdIn(travelRecordIds);
        Map<Long, List<RecordMedia>> media = mediaByTravelRecordId(travelRecordIds);
        return travelRecords.stream()
                .map(travelRecord -> toView(travelRecord, media.getOrDefault(travelRecord.getId(), List.of())))
                .toList();
    }

    // 쿼리가 기록 id, sortOrder 순으로 정렬해 주므로 기록마다 묶은 목록도 sortOrder 순이다.
    private Map<Long, List<RecordMedia>> mediaByTravelRecordId(Collection<Long> travelRecordIds) {
        return travelRecordRepository.findMediaByTravelRecordIdIn(travelRecordIds).stream()
                .collect(Collectors.groupingBy(RecordMedia::travelRecordId));
    }

    private RecordBrief toBrief(TravelRecord travelRecord, List<RecordMedia> media) {
        ExpiringUrl cover = media.isEmpty()
                ? null
                : recordMediaUrlService.createViewUrl(media.getFirst().getThumbnailObjectKey());
        return new RecordBrief(
                travelRecord.getId(),
                travelRecord.memberId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getId(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate() == null ? travelRecord.getStartDate() : travelRecord.getEndDate(),
                media.size(),
                cover
        );
    }

    private RecordView toView(TravelRecord travelRecord, List<RecordMedia> media) {
        RecordedPlace place = travelRecord.getPlace();
        return new RecordView(
                travelRecord.getId(),
                travelRecord.memberId(),
                travelRecord.getTitle(),
                travelRecord.getContent(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                place == null ? null : place.name(),
                media.stream()
                        .map(recordMedia -> new RecordView.PhotoView(
                                recordMedia.getId(),
                                recordMediaUrlService.createViewUrl(recordMedia.getObjectKey()),
                                recordMedia.getSortOrder()))
                        .toList()
        );
    }
}
