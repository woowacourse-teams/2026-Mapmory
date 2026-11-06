package com.mapmory.backend.sharevideo;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.application.PushTokenService;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshot;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshotFactory;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ShareVideoService {

    private final TravelRecordRepository travelRecordRepository;
    private final TravelTimelineSnapshotFactory snapshotFactory;
    private final ShareVideoJobRepository shareVideoJobRepository;
    private final ShareVideoDownloadUrlService downloadUrlService;
    private final JsonMapper jsonMapper;
    private final ActiveMemberGuard activeMemberGuard;
    private final PushTokenService pushTokenService;

    public ShareVideoService(
            TravelRecordRepository travelRecordRepository,
            TravelTimelineSnapshotFactory snapshotFactory,
            ShareVideoJobRepository shareVideoJobRepository,
            ShareVideoDownloadUrlService downloadUrlService,
            JsonMapper jsonMapper,
            ActiveMemberGuard activeMemberGuard,
            PushTokenService pushTokenService
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.snapshotFactory = snapshotFactory;
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.downloadUrlService = downloadUrlService;
        this.jsonMapper = jsonMapper;
        this.activeMemberGuard = activeMemberGuard;
        this.pushTokenService = pushTokenService;
    }

    @Transactional
    public ShareVideoJob createTravelTimeline(final Member member) {
        return createTravelTimeline(member, ShareVideoStyle.defaultStyle(), null);
    }

    @Transactional
    public ShareVideoJob createTravelTimeline(
            final Member member,
            final ShareVideoStyle styleId
    ) {
        return createTravelTimeline(member, styleId, null);
    }

    @Transactional
    public ShareVideoJob createTravelTimeline(
            final Member member,
            final ShareVideoStyle styleId,
            final Long pushTokenId
    ) {
        // 탈퇴 뒤 남은 access token으로 렌더링을 맡기지 못하게 한다.
        activeMemberGuard.require(member.getId());

        // 알림을 요청한 경우에만 수신 등록의 소유권을 확인한다.
        if (pushTokenId != null
                && !pushTokenService.isOwnedBy(pushTokenId, member.getId())) {
            throw new BusinessException(
                    ShareVideoErrorCode.SHARE_VIDEO_PUSH_TARGET_NOT_FOUND
            );
        }

        final ShareVideoStyle resolvedStyle;
        if (styleId == null) {
            resolvedStyle = ShareVideoStyle.defaultStyle();
        } else {
            resolvedStyle = styleId;
        }
        final List<TravelRecord> records = travelRecordRepository.findAllForTravelTimeline(member.getId());
        final TravelTimelineSnapshot snapshot = snapshotFactory.create(records);

        if (snapshot.stops().size() < 2) {
            throw new BusinessException(ShareVideoErrorCode.INSUFFICIENT_TIMELINE_STOPS);
        }

        final String snapshotJson = jsonMapper.writeValueAsString(snapshot);

        final ShareVideoJob job = ShareVideoJob.pending(
                member,
                "TRAVEL_TIMELINE",
                resolvedStyle,
                snapshotJson,
                pushTokenId
        );

        return shareVideoJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public ShareVideoJob getJob(final Member member, final Long jobId) {
        return findOwnedJob(member, jobId);
    }

    @Transactional(readOnly = true)
    public ShareVideoDownloadUrl createDownloadUrl(final Member member, final Long jobId) {
        final ShareVideoJob job = findOwnedJob(member, jobId);

        return switch (job.getStatus()) {
            case COMPLETED -> downloadUrlService.create(job.getResultObjectKey());
            case PENDING, RENDERING -> throw new BusinessException(ShareVideoErrorCode.SHARE_VIDEO_NOT_READY);
            case FAILED -> throw new BusinessException(ShareVideoErrorCode.SHARE_VIDEO_GENERATION_FAILED);
        };
    }

    private ShareVideoJob findOwnedJob(final Member member, final Long jobId) {
        return shareVideoJobRepository.findByIdAndMemberId(jobId, member.getId())
                .orElseThrow(() -> new BusinessException(ShareVideoErrorCode.SHARE_VIDEO_JOB_NOT_FOUND));
    }
}
