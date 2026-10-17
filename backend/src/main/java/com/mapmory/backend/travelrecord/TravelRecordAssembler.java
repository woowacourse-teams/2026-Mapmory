package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * TravelRecord 애그리거트의 읽기 모델을 조립한다.
 *
 * <p>ADR 0017 결정 4에 따라 애그리거트당 하나만 둔다. 조회와 URL 발급을 맡으므로
 * 애그리거트 내부가 아니라 응용 계층에 속한다.
 */
@Component
public class TravelRecordAssembler {

    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordTagService travelRecordTagService;
    private final RecordMediaUrlService recordMediaUrlService;

    public TravelRecordAssembler(
            TravelRecordRepository travelRecordRepository,
            TravelRecordTagService travelRecordTagService,
            RecordMediaUrlService recordMediaUrlService
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.travelRecordTagService = travelRecordTagService;
        this.recordMediaUrlService = recordMediaUrlService;
    }

    public TravelRecordDetail assembleDetail(TravelRecord travelRecord) {
        return assembleDetail(
                travelRecord,
                travelRecordTagService.findByTravelRecordId(travelRecord.getId())
        );
    }

    /**
     * 태그를 이미 알고 있는 수정 경로용. 같은 트랜잭션에서 다시 조회하지 않는다.
     */
    public TravelRecordDetail assembleDetail(TravelRecord travelRecord, List<Tag> tags) {
        return new TravelRecordDetail(travelRecord, tags, toMediaViews(travelRecord));
    }

    public TravelRecordSummaries assembleSummaries(Page<TravelRecord> travelRecords) {
        List<Long> travelRecordIds = travelRecords.getContent().stream()
                .map(TravelRecord::getId)
                .toList();

        List<RecordMedia> media = findMedia(travelRecordIds);

        return new TravelRecordSummaries(
                travelRecords,
                travelRecordTagService.findByTravelRecordIds(travelRecordIds),
                createThumbnailUrls(media),
                countPhotos(media)
        );
    }

    private List<MediaView> toMediaViews(TravelRecord travelRecord) {
        return travelRecord.getMedia().stream()
                .map(recordMedia -> new MediaView(
                        recordMedia,
                        recordMediaUrlService.createViewUrl(recordMedia.getObjectKey())
                ))
                .toList();
    }

    /**
     * 썸네일과 사진 개수를 같은 조회 결과에서 만든다. 목록 한 페이지에 미디어 조회는 한 번뿐이다.
     */
    private List<RecordMedia> findMedia(List<Long> travelRecordIds) {
        if (travelRecordIds.isEmpty()) {
            return List.of();
        }
        return travelRecordRepository.findMediaByTravelRecordIdIn(travelRecordIds);
    }

    private Map<Long, ExpiringUrl> createThumbnailUrls(List<RecordMedia> media) {
        Map<Long, ExpiringUrl> thumbnailUrls = new HashMap<>();
        for (RecordMedia recordMedia : media) {
            thumbnailUrls.computeIfAbsent(
                    recordMedia.travelRecordId(),
                    ignored -> recordMediaUrlService.createViewUrl(recordMedia.getThumbnailObjectKey())
            );
        }
        return Map.copyOf(thumbnailUrls);
    }

    private Map<Long, Integer> countPhotos(List<RecordMedia> media) {
        Map<Long, Integer> photoCounts = new HashMap<>();
        for (RecordMedia recordMedia : media) {
            photoCounts.merge(recordMedia.travelRecordId(), 1, Integer::sum);
        }
        return Map.copyOf(photoCounts);
    }
}
