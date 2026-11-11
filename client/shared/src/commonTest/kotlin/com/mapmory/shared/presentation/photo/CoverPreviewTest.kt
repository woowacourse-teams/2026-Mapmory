package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class CoverPreviewTest {
    private val small = byteArrayOf(1)
    private val large = byteArrayOf(2, 2)
    private val photos = listOf(
        SelectedPhoto(id = "a", displayName = "a", previewBytes = small),
        SelectedPhoto(id = "b", displayName = "b", previewBytes = small),
    )

    @Test
    fun `대표_사진의_큰_미리보기가_있으면_대표_사진의_미리보기만_바꾼다`() {
        val result = photos.withCoverPreview("b", CoverPreview("b", large))

        assertContentEquals(small, result[0].previewBytes)
        assertContentEquals(large, result[1].previewBytes)
    }

    @Test
    fun `큰_미리보기가_없거나_다른_사진의_것이면_목록을_그대로_둔다`() {
        assertSame(photos, photos.withCoverPreview("b", null))
        assertSame(photos, photos.withCoverPreview("missing", CoverPreview("missing", large)))
    }

    @Test
    fun `이전에_고른_다른_사진의_큰_미리보기는_대표_사진에도_그_사진에도_적용하지_않는다`() {
        // A의 큰 미리보기가 도착한 뒤 대표를 B로 바꾸고, B의 결과가 오기 전에 저장한 경우.
        assertSame(photos, photos.withCoverPreview("b", CoverPreview("a", large)))
    }
}
