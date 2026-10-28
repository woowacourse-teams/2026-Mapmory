package com.mapmory.backend.sharevideo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.sharevideo.timeline.TravelTimelineSnapshotFactory;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
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
                activeMemberGuard
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

    private void assertError(Runnable action, ShareVideoErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );
    }
}
