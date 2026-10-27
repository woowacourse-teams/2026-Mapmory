package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.presentation.triprecord.screen.shouldExpandTripTimelineVideoDialog
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTimelineVideoDialogTest {
    @Test
    fun `영상이 없고 생성이 끝나지 않았으면 컴팩트 높이를 사용한다`() {
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = null))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.PENDING))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.RENDERING))
        assertFalse(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.FAILED))
    }

    @Test
    fun `영상 미리보기 또는 완료 상태면 전체 높이를 사용한다`() {
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = true, status = ShareVideoStatus.RENDERING))
        assertTrue(shouldExpandTripTimelineVideoDialog(hasPreview = false, status = ShareVideoStatus.COMPLETED))
    }
}
