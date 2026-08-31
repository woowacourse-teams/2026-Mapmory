package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.RecordMedia;
import com.mapmory.backend.recordmedia.RecordMediaRepository;
import com.mapmory.backend.recordmedia.RecordMediaUrlService;
import com.mapmory.backend.recordmedia.RecordMediaView;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.travelrecordtag.TravelRecordTagService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 여행 일지에 딸린 미디어·태그·조회 URL을 모아 도메인 조합 결과를 만든다.
 * 조회에 필요한 리포지토리를 직접 들고 있어, TravelRecordService는 유스케이스 흐름에만 집중한다.
 */
@Component
public class TravelRecordAssembler {

    private final RecordMediaRepository recordMediaRepository;
    private final TravelRecordTagService travelRecordTagService;
    private final RecordMediaUrlService recordMediaUrlService;

    public TravelRecordAssembler(
            RecordMediaRepository recordMediaRepository,
            TravelRecordTagService travelRecordTagService,
            RecordMediaUrlService recordMediaUrlService
    ) {
        this.recordMediaRepository = recordMediaRepository;
        this.travelRecordTagService = travelRecordTagService;
        this.recordMediaUrlService = recordMediaUrlService;
    }

    public TravelRecordDetail assembleDetail(TravelRecord travelRecord) {
        Long travelRecordId = travelRecord.getId();
        List<RecordMedia> recordMedia = recordMediaRepository
                .findByTravelRecordIdOrderBySortOrderAsc(travelRecordId);

        return assembleDetail(
                travelRecord,
                recordMedia,
                travelRecordTagService.findByTravelRecordId(travelRecordId)
        );
    }

    public TravelRecordDetail assembleDetail(
            TravelRecord travelRecord,
            List<RecordMedia> recordMedia,
            List<Tag> tags
    ) {
        return new TravelRecordDetail(travelRecord, toMediaViews(recordMedia), tags);
    }

    public TravelRecordSummaries assembleSummaries(Page<TravelRecord> travelRecords) {
        List<Long> travelRecordIds = travelRecords.getContent().stream()
                .map(TravelRecord::getId)
                .toList();

        return new TravelRecordSummaries(
                travelRecords,
                travelRecordTagService.findByTravelRecordIds(travelRecordIds),
                recordMediaUrlService.createThumbnailUrls(travelRecordIds)
        );
    }

    private List<RecordMediaView> toMediaViews(List<RecordMedia> recordMedia) {
        return recordMedia.stream()
                .map(media -> new RecordMediaView(
                        media,
                        recordMediaUrlService.createViewUrl(media.getObjectKey())
                ))
                .toList();
    }
}
