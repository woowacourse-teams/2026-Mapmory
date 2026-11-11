package com.mapmory.shared.presentation.photo

/** 대표 사진 화면이 큰 미리보기를 기다리는 최대 시간. 이 안에 오면 작은 미리보기를 건너뛴다. */
internal const val CoverPreviewGraceMillis = 150L

internal enum class CoverPreviewStage {
    /** 큰 미리보기를 기다리는 중이라 사진 대신 자리 표시만 보인다. */
    WAITING,

    /** 목록용 작은 미리보기(256px)를 보여 준다. */
    SMALL,

    /** 큰 미리보기(1080px)를 보여 준다. */
    LARGE,
}

internal enum class CoverPreviewEvent {
    /** 큰 미리보기가 디코딩까지 끝나 바로 그릴 수 있다. */
    LARGE_READY,

    /** 이 사진의 큰 미리보기 요청이 이미지 없이 끝났다. */
    LARGE_UNAVAILABLE,

    /** [CoverPreviewGraceMillis]가 지났다. */
    GRACE_EXPIRED,
}

/** [crossfade]는 작은 미리보기를 이미 보여 준 뒤 큰 미리보기로 바뀌었는지를 뜻한다. */
internal data class CoverPreviewDisplay(
    val stage: CoverPreviewStage,
    val crossfade: Boolean = false,
)

/** 큰 미리보기를 받지 않는 플랫폼은 기다리지 않아 기존처럼 작은 미리보기를 바로 보여 준다. */
internal fun initialCoverPreviewDisplay(largePreviewSupported: Boolean): CoverPreviewDisplay =
    CoverPreviewDisplay(if (largePreviewSupported) CoverPreviewStage.WAITING else CoverPreviewStage.SMALL)

internal fun CoverPreviewDisplay.after(event: CoverPreviewEvent): CoverPreviewDisplay = when (stage) {
    CoverPreviewStage.WAITING -> when (event) {
        CoverPreviewEvent.LARGE_READY -> CoverPreviewDisplay(CoverPreviewStage.LARGE, crossfade = false)
        CoverPreviewEvent.LARGE_UNAVAILABLE,
        CoverPreviewEvent.GRACE_EXPIRED -> CoverPreviewDisplay(CoverPreviewStage.SMALL)
    }
    CoverPreviewStage.SMALL -> when (event) {
        CoverPreviewEvent.LARGE_READY -> CoverPreviewDisplay(CoverPreviewStage.LARGE, crossfade = true)
        CoverPreviewEvent.LARGE_UNAVAILABLE,
        CoverPreviewEvent.GRACE_EXPIRED -> this
    }
    CoverPreviewStage.LARGE -> this
}

/** 두 레이어를 어떻게 그릴지. 작은 미리보기는 아래, 큰 미리보기는 위에 놓인다. */
internal data class CoverPreviewLayers(
    val smallVisible: Boolean,
    val largeAlpha: Float,
)

/**
 * [crossfadeProgress]는 크로스페이드 애니메이션 값(0~1)이다. 애니메이션 값은 상태가 바뀐 다음 프레임에야
 * 움직이므로, 크로스페이드가 아닌 큰 미리보기는 이 값을 쓰지 않고 첫 프레임부터 완전히 보인다.
 */
internal fun CoverPreviewDisplay.layers(crossfadeProgress: Float): CoverPreviewLayers = when (stage) {
    CoverPreviewStage.WAITING -> CoverPreviewLayers(smallVisible = false, largeAlpha = 0f)
    CoverPreviewStage.SMALL -> CoverPreviewLayers(smallVisible = true, largeAlpha = 0f)
    CoverPreviewStage.LARGE -> if (crossfade) {
        CoverPreviewLayers(smallVisible = true, largeAlpha = crossfadeProgress)
    } else {
        CoverPreviewLayers(smallVisible = false, largeAlpha = 1f)
    }
}
