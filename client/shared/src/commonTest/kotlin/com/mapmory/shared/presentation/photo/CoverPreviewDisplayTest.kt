package com.mapmory.shared.presentation.photo

import com.mapmory.shared.presentation.photo.CoverPreviewEvent.GRACE_EXPIRED
import com.mapmory.shared.presentation.photo.CoverPreviewEvent.LARGE_READY
import com.mapmory.shared.presentation.photo.CoverPreviewEvent.LARGE_UNAVAILABLE
import com.mapmory.shared.presentation.photo.CoverPreviewStage.LARGE
import com.mapmory.shared.presentation.photo.CoverPreviewStage.SMALL
import com.mapmory.shared.presentation.photo.CoverPreviewStage.WAITING
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverPreviewDisplayTest {
    @Test
    fun `큰_미리보기를_지원하지_않는_플랫폼은_기다리지_않고_바로_작은_미리보기를_보여준다`() {
        // 안드로이드: 대기가 생기면 대표 사진 화면이 첫 프레임에 비어 보인다.
        assertEquals(CoverPreviewDisplay(SMALL), initialCoverPreviewDisplay(largePreviewSupported = false))
    }

    @Test
    fun `큰_미리보기를_지원하면_처음에는_아무것도_바꾸지_않고_기다린다`() {
        assertEquals(CoverPreviewDisplay(WAITING), initialCoverPreviewDisplay(largePreviewSupported = true))
    }

    @Test
    fun `대기_시간_안에_큰_미리보기가_준비되면_작은_미리보기_없이_바로_보여준다`() {
        val display = initialCoverPreviewDisplay(largePreviewSupported = true).after(LARGE_READY)

        assertEquals(CoverPreviewDisplay(LARGE, crossfade = false), display)
    }

    @Test
    fun `대기_시간이_지나면_작은_미리보기를_먼저_보여준다`() {
        val display = initialCoverPreviewDisplay(largePreviewSupported = true).after(GRACE_EXPIRED)

        assertEquals(CoverPreviewDisplay(SMALL), display)
    }

    @Test
    fun `작은_미리보기를_보여준_뒤_도착한_큰_미리보기는_크로스페이드로_바꾼다`() {
        val display = initialCoverPreviewDisplay(largePreviewSupported = true)
            .after(GRACE_EXPIRED)
            .after(LARGE_READY)

        assertEquals(CoverPreviewDisplay(LARGE, crossfade = true), display)
    }

    @Test
    fun `대기_중에_큰_미리보기를_받을_수_없게_되면_대기_시간을_채우지_않고_작은_미리보기를_보여준다`() {
        val display = initialCoverPreviewDisplay(largePreviewSupported = true).after(LARGE_UNAVAILABLE)

        assertEquals(CoverPreviewDisplay(SMALL), display)
    }

    @Test
    fun `큰_미리보기를_보여준_뒤에는_늦게_온_이벤트로_작은_미리보기로_돌아가지_않는다`() {
        val direct = initialCoverPreviewDisplay(largePreviewSupported = true).after(LARGE_READY)
        val crossfaded = direct.copy(crossfade = true)

        for (event in CoverPreviewEvent.entries) {
            assertEquals(direct, direct.after(event))
            assertEquals(crossfaded, crossfaded.after(event))
        }
    }

    @Test
    fun `작은_미리보기_상태에서_대기_만료나_실패가_다시_와도_그대로다`() {
        val small = initialCoverPreviewDisplay(largePreviewSupported = true).after(GRACE_EXPIRED)

        assertEquals(small, small.after(GRACE_EXPIRED))
        assertEquals(small, small.after(LARGE_UNAVAILABLE))
    }

    @Test
    fun `기다리는_동안에는_두_미리보기_모두_보이지_않는다`() {
        assertEquals(
            CoverPreviewLayers(smallVisible = false, largeAlpha = 0f),
            CoverPreviewDisplay(WAITING).layers(crossfadeProgress = 0f),
        )
    }

    @Test
    fun `작은_미리보기_상태에서는_작은_것만_보인다`() {
        assertEquals(
            CoverPreviewLayers(smallVisible = true, largeAlpha = 0f),
            CoverPreviewDisplay(SMALL).layers(crossfadeProgress = 0f),
        )
    }

    @Test
    fun `대기_안에_온_큰_미리보기는_애니메이션을_기다리지_않고_첫_프레임부터_완전히_보인다`() {
        // 진행값이 아직 0이어도(애니메이션 값은 다음 프레임에 바뀐다) 작은 미리보기가 한 프레임도 보이면 안 된다.
        assertEquals(
            CoverPreviewLayers(smallVisible = false, largeAlpha = 1f),
            CoverPreviewDisplay(LARGE, crossfade = false).layers(crossfadeProgress = 0f),
        )
    }

    @Test
    fun `크로스페이드는_작은_미리보기_위에서_진행값만큼_큰_미리보기를_보인다`() {
        val display = CoverPreviewDisplay(LARGE, crossfade = true)

        assertEquals(CoverPreviewLayers(smallVisible = true, largeAlpha = 0f), display.layers(crossfadeProgress = 0f))
        assertEquals(CoverPreviewLayers(smallVisible = true, largeAlpha = 0.4f), display.layers(crossfadeProgress = 0.4f))
        assertEquals(CoverPreviewLayers(smallVisible = true, largeAlpha = 1f), display.layers(crossfadeProgress = 1f))
    }

    @Test
    fun `큰_미리보기를_기다리는_시간은_150ms다`() {
        assertEquals(150L, CoverPreviewGraceMillis)
    }
}
