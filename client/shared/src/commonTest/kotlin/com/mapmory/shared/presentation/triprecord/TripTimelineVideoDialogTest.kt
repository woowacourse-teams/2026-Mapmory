package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.triprecord.screen.canCreateTripTimelineVideo
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.presentation.triprecord.screen.TripTimelineDownloadedPreview
import com.mapmory.shared.presentation.triprecord.screen.downloadTripTimelineVideoPreview
import com.mapmory.shared.presentation.triprecord.screen.shouldExpandTripTimelineVideoDialog
import com.mapmory.shared.presentation.triprecord.screen.shouldShowTripTimelineVideoRetryButton
import com.mapmory.shared.presentation.triprecord.screen.tripTimelineVideoDialogHeightFraction
import com.mapmory.shared.presentation.triprecord.share.ShareVideoFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTimelineVideoDialogTest {
    @Test
    fun videoCreationRequiresTwoRecordedCountries() {
        assertFalse(canCreateTripTimelineVideo(null))
        assertFalse(canCreateTripTimelineVideo(0))
        assertFalse(canCreateTripTimelineVideo(1))
        assertTrue(canCreateTripTimelineVideo(2))
        assertTrue(canCreateTripTimelineVideo(3))
    }

    @Test
    fun `영상이 없고 생성이 끝나지 않았으면 컴팩트 높이를 사용한다`() {
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = null))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.PENDING))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.RENDERING))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.FAILED))
    }

    @Test
    fun `영상 미리보기 또는 완료 상태면 전체 높이를 사용한다`() {
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = true, status = ShareVideoStatus.PENDING))
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = true, status = ShareVideoStatus.RENDERING))
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = true, status = ShareVideoStatus.FAILED))
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.COMPLETED))
    }

    @Test
    fun `생성 전은 작은 팝업이고 완료 후는 큰 미리보기 화면을 사용한다`() {
        assertEquals(0.44f, tripTimelineVideoDialogHeightFraction(expanded = false))
        assertEquals(0.82f, tripTimelineVideoDialogHeightFraction(expanded = true))
    }

    @Test
    fun `기존 영상이 유지된 새 작업의 다운로드 실패 후에만 재시도 버튼을 보인다`() {
        fun shouldShowRetry(
            videoJobId: Long?,
            attemptedJobId: Long? = 2L,
            busy: Boolean = false,
            hasFailureMessage: Boolean = true,
        ) = shouldShowTripTimelineVideoRetryButton(
            jobId = 2L,
            status = ShareVideoStatus.COMPLETED,
            videoJobId = videoJobId,
            previewAttemptedJobId = attemptedJobId,
            busy = busy,
            hasFailureMessage = hasFailureMessage,
        )

        assertTrue(shouldShowRetry(videoJobId = 1L))
        assertFalse(shouldShowRetry(videoJobId = 1L, busy = true))
        assertFalse(shouldShowRetry(videoJobId = 1L, hasFailureMessage = false))
        assertFalse(shouldShowRetry(videoJobId = 2L))
        assertFalse(shouldShowRetry(videoJobId = 1L, attemptedJobId = null))
    }

    @Test
    fun `새 영상 다운로드 실패는 기존 영상을 유지하고 재시도 성공은 새 영상으로 교체한다`() = runBlocking {
        var preview = TripTimelineDownloadedPreview(ShareVideoFile("video-a.mp4"), jobId = 1L)
        var attempts = 0
        val downloadB: suspend (Long?) -> ShareVideoFile = { protectedJobId ->
            assertEquals(1L, protectedJobId)
            attempts++
            if (attempts == 1) error("download failed")
            ShareVideoFile("video-b.mp4")
        }

        val firstAttempt = runCatching {
            preview = downloadTripTimelineVideoPreview(preview, jobId = 2L, downloadB)
        }
        assertTrue(firstAttempt.isFailure)
        assertEquals(TripTimelineDownloadedPreview(ShareVideoFile("video-a.mp4"), 1L), preview)

        preview = downloadTripTimelineVideoPreview(preview, jobId = 2L, downloadB)
        assertEquals(TripTimelineDownloadedPreview(ShareVideoFile("video-b.mp4"), 2L), preview)
    }
}
