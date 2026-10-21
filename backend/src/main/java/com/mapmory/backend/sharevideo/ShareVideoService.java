package com.mapmory.backend.sharevideo;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshot;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshotFactory;
import com.mapmory.backend.travelrecord.TravelRecord;
import com.mapmory.backend.travelrecord.TravelRecordRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ShareVideoService {

    private final TravelRecordRepository travelRecordRepository;
    private final TravelTimelineSnapshotFactory snapshotFactory;
    private final ShareVideoJobRepository shareVideoJobRepository;
    private final JsonMapper jsonMapper;

    public ShareVideoService(
            TravelRecordRepository travelRecordRepository,
            TravelTimelineSnapshotFactory snapshotFactory,
            ShareVideoJobRepository shareVideoJobRepository,
            JsonMapper jsonMapper
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.snapshotFactory = snapshotFactory;
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public ShareVideoJob createTravelTimeline(final Member member) {
        final List<TravelRecord> records = travelRecordRepository.findAllForTravelTimeline(member.getId());
        final TravelTimelineSnapshot snapshot = snapshotFactory.create(records);

        if (snapshot.stops().size() < 2) {
            throw new BusinessException(ShareVideoErrorCode.INSUFFICIENT_TIMELINE_STOPS);
        }

        final String snapshotJson = jsonMapper.writeValueAsString(snapshot);

        final ShareVideoJob job = ShareVideoJob.pending(
                member,
                "TRAVEL_TIMELINE",
                snapshotJson
        );

        return shareVideoJobRepository.save(job);
    }
}
