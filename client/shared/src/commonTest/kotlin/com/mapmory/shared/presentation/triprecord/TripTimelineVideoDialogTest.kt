package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.presentation.triprecord.screen.shouldExpandTripTimelineVideoDialog
import com.mapmory.shared.presentation.triprecord.screen.tripTimelineVideoDialogHeightFraction
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
