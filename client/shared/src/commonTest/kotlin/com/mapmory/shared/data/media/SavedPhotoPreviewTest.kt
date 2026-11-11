package com.mapmory.shared.data.media

import com.mapmory.shared.domain.model.TripRecordMediaDraft
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SavedPhotoPreviewTest {
    private val small = byteArrayOf(1)
    private val media = listOf(
        TripRecordMediaDraft(objectKey = "a", sortOrder = 0, previewBytes = small),
        TripRecordMediaDraft(objectKey = "b", sortOrder = 1, previewBytes = small),
    )

    @Test
    fun `큰_미리보기로_교체한_사진은_초안의_작은_미리보기를_넘기지_않는다`() {
        // 넘기면 저장 뒤 캐시 단계가 256px로 큰 미리보기를 다시 덮어쓴다(#686 편집 경로).
        val result = media.withoutReplacedPreviews(replacedLocalIds = setOf("b"))

        assertContentEquals(small, result[0].previewBytes)
        assertNull(result[1].previewBytes)
    }

    @Test
    fun `교체한_사진이_없으면_목록을_그대로_둔다`() {
        assertSame(media, media.withoutReplacedPreviews(replacedLocalIds = emptySet()))
    }
}
