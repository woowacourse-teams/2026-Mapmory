package com.mapmory.backend.sharevideo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.application.PushTokenService;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshot;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshotFactory;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineStop;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class ShareVideoServiceTest {

    private final TravelRecordRepository travelRecordRepository = mock(TravelRecordRepository.class);
    private final TravelTimelineSnapshotFactory snapshotFactory = mock(TravelTimelineSnapshotFactory.class);
    private final ShareVideoJobRepository shareVideoJobRepository = mock(ShareVideoJobRepository.class);
    private final ShareVideoDownloadUrlService downloadUrlService = mock(ShareVideoDownloadUrlService.class);
    private final JsonMapper jsonMapper = mock(JsonMapper.class);
    private final ActiveMemberGuard activeMemberGuard = mock(ActiveMemberGuard.class);
    private final PushTokenService pushTokenService = mock(PushTokenService.class);
    private final Member member = mock(Member.class);

    private ShareVideoService shareVideoService;

    @BeforeEach
    void setUp() {
        shareVideoService = new ShareVideoService(
                travelRecordRepository,
                snapshotFactory,
                shareVideoJobRepository,
                downloadUrlService,
                jsonMapper,
                activeMemberGuard,
                pushTokenService
        );
        when(member.getId()).thenReturn(10L);
    }

    @Test
    void 회원이_소유한_영상_작업을_조회한다() {
        ShareVideoJob job = mock(ShareVideoJob.class);
        when(shareVideoJobRepository.findByIdAndMemberId(15L, 10L))
                .thenReturn(Optional.of(job));

        ShareVideoJob result = shareVideoService.getJob(member, 15L);

        assertThat(result).isSameAs(job);
    }

    @Test
    void 영상_작업을_찾지_못하면_예외를_발생시킨다() {
        when(shareVideoJobRepository.findByIdAndMemberId(15L, 10L))
                .thenReturn(Optional.empty());

        assertError(
                () -> shareVideoService.getJob(member, 15L),
                ShareVideoErrorCode.SHARE_VIDEO_JOB_NOT_FOUND
        );
    }

    @Test
    void 완료된_작업의_다운로드_URL을_생성한다() {
        ShareVideoJob job = mock(ShareVideoJob.class);
        ShareVideoDownloadUrl expected = new ShareVideoDownloadUrl(
                "https://download.example/share-videos/15/result.mp4",
                300L
        );
        when(shareVideoJobRepository.findByIdAndMemberId(15L, 10L))
                .thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(ShareVideoStatus.COMPLETED);
        when(job.getResultObjectKey()).thenReturn("share-videos/15/result.mp4");
        when(downloadUrlService.create("share-videos/15/result.mp4")).thenReturn(expected);

        ShareVideoDownloadUrl result = shareVideoService.createDownloadUrl(member, 15L);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = ShareVideoStatus.class, names = {"PENDING", "RENDERING"})
    void 준비되지_않은_작업은_다운로드할_수_없다(ShareVideoStatus status) {
        ShareVideoJob job = mock(ShareVideoJob.class);
        when(shareVideoJobRepository.findByIdAndMemberId(15L, 10L))
                .thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(status);

        assertError(
                () -> shareVideoService.createDownloadUrl(member, 15L),
                ShareVideoErrorCode.SHARE_VIDEO_NOT_READY
        );
        verifyNoInteractions(downloadUrlService);
    }

    @Test
    void 실패한_작업은_다운로드할_수_없다() {
        ShareVideoJob job = mock(ShareVideoJob.class);
        when(shareVideoJobRepository.findByIdAndMemberId(15L, 10L))
                .thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(ShareVideoStatus.FAILED);

        assertError(
                () -> shareVideoService.createDownloadUrl(member, 15L),
                ShareVideoErrorCode.SHARE_VIDEO_GENERATION_FAILED
        );
        verifyNoInteractions(downloadUrlService);
    }

    @Test
    void 소유한_푸시_등록_ID를_영상_작업에_저장한다() {
        stubTimeline();
        when(pushTokenService.isOwnedBy(42L, 10L)).thenReturn(true);

        ShareVideoJob job = shareVideoService.createTravelTimeline(member, ShareVideoStyle.HORIZON, 42L);

        assertThat(job.getRecipientPushTokenId()).isEqualTo(42L);
        assertThat(job.getStyleId()).isEqualTo(ShareVideoStyle.HORIZON);
        verify(pushTokenService).isOwnedBy(42L, 10L);
        verify(shareVideoJobRepository).save(job);
    }

    @Test
    void 사용할_수_없는_푸시_등록이면_작업을_생성하지_않는다() {
        when(pushTokenService.isOwnedBy(42L, 10L)).thenReturn(false);

        assertError(
                () -> shareVideoService.createTravelTimeline(member, ShareVideoStyle.CLASSIC, 42L),
                ShareVideoErrorCode.SHARE_VIDEO_PUSH_TARGET_NOT_FOUND
        );

        verifyNoInteractions(travelRecordRepository, snapshotFactory, jsonMapper, shareVideoJobRepository);
    }

    @Test
    void 기존_생성_호출은_알림_없이_기본_스타일로_생성한다() {
        stubTimeline();

        ShareVideoJob job = shareVideoService.createTravelTimeline(member);

        assertThat(job.getRecipientPushTokenId()).isNull();
        assertThat(job.getStyleId()).isEqualTo(ShareVideoStyle.CLASSIC);
        verifyNoInteractions(pushTokenService);
    }

    private void stubTimeline() {
        List<TravelRecord> records = List.of();
        TravelTimelineSnapshot snapshot = TravelTimelineSnapshot.of(List.of(
                new TravelTimelineStop("KR", "대한민국", List.of()),
                new TravelTimelineStop("JP", "일본", List.of())
        ));
        when(travelRecordRepository.findAllForTravelTimeline(10L)).thenReturn(records);
        when(snapshotFactory.create(records)).thenReturn(snapshot);
        when(jsonMapper.writeValueAsString(snapshot)).thenReturn("{\"version\":1}");
        when(shareVideoJobRepository.save(any(ShareVideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertError(Runnable action, ShareVideoErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );
    }
}
