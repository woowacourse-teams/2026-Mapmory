package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.travelrecord.application.model.MediaView;
import com.mapmory.backend.travelrecord.application.model.RecordMedia;
import com.mapmory.backend.travelrecord.application.model.RecordedPlace;
import com.mapmory.backend.travelrecord.application.model.TravelRecord;
import com.mapmory.backend.travelrecord.application.model.TravelRecordDetail;
import com.mapmory.backend.travelrecord.TravelRecordListPage;
import com.mapmory.backend.travelrecord.TravelRecordRepository;
import com.mapmory.backend.travelrecord.TravelRecordSummaries;
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

    /**
     * 일지 한 페이지를 캐시에 넣을 수 있는 값으로 옮긴다. 서명 URL은 만들지 않는다({@link #signThumbnails}).
     */
    public TravelRecordListPage assembleListPage(Page<TravelRecord> travelRecords) {
        List<Long> travelRecordIds = travelRecords.getContent().stream()
                .map(TravelRecord::getId)
                .toList();

        List<RecordMedia> media = findMedia(travelRecordIds);
        Map<Long, List<Tag>> tags = travelRecordTagService.findByTravelRecordIds(travelRecordIds);
        Map<Long, String> thumbnailObjectKeys = findThumbnailObjectKeys(media);
        Map<Long, Integer> photoCounts = countPhotos(media);

        return new TravelRecordListPage(
                travelRecords.getContent().stream()
                        .map(travelRecord -> toEntry(
                                travelRecord,
                                tags.getOrDefault(travelRecord.getId(), List.of()),
                                thumbnailObjectKeys.get(travelRecord.getId()),
                                photoCounts.getOrDefault(travelRecord.getId(), 0)
                        ))
                        .toList(),
                travelRecords.getNumber(),
                travelRecords.getSize(),
                travelRecords.getTotalElements(),
                travelRecords.getTotalPages(),
                travelRecords.hasNext()
        );
    }

    /**
     * 썸네일 URL은 5분 뒤 만료되므로 캐시에 두지 않고 응답할 때마다 새로 서명한다. 서명은 서버 안의 계산이라 DB나 S3에 가지 않는다.
     */
    public TravelRecordSummaries signThumbnails(TravelRecordListPage listPage) {
        Map<Long, ExpiringUrl> thumbnailUrls = new HashMap<>();
        for (TravelRecordListPage.Entry entry : listPage.entries()) {
            if (entry.thumbnailObjectKey() != null) {
                thumbnailUrls.put(entry.id(), recordMediaUrlService.createViewUrl(entry.thumbnailObjectKey()));
            }
        }
        return new TravelRecordSummaries(listPage, Map.copyOf(thumbnailUrls));
    }

    private TravelRecordListPage.Entry toEntry(
            TravelRecord travelRecord,
            List<Tag> tags,
            String thumbnailObjectKey,
            int photoCount
    ) {
        RecordedPlace place = travelRecord.getPlace();
        return new TravelRecordListPage.Entry(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getName(),
                place == null ? null : place.name(),
                place == null ? null : place.attribution(),
                place == null ? null : place.attributionUrl(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                thumbnailObjectKey,
                photoCount,
                tags.stream()
                        .map(tag -> new TravelRecordListPage.TagLabel(tag.getId(), tag.getName()))
                        .toList()
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

    private Map<Long, String> findThumbnailObjectKeys(List<RecordMedia> media) {
        Map<Long, String> thumbnailObjectKeys = new HashMap<>();
        for (RecordMedia recordMedia : media) {
            thumbnailObjectKeys.putIfAbsent(recordMedia.travelRecordId(), recordMedia.getThumbnailObjectKey());
        }
        return thumbnailObjectKeys;
    }

    private Map<Long, Integer> countPhotos(List<RecordMedia> media) {
        Map<Long, Integer> photoCounts = new HashMap<>();
        for (RecordMedia recordMedia : media) {
            photoCounts.merge(recordMedia.travelRecordId(), 1, Integer::sum);
        }
        return Map.copyOf(photoCounts);
    }
}
