package com.mapmory.shared.data.media

/**
 * 저장 전에 사진 원본을 미리 받아 두는 플랫폼 기능. 원본을 따로 받아야 하는 플랫폼(iOS iCloud)만 구현한다.
 * 두 함수 모두 바로 돌아오고, 실제 작업은 앱 단위 범위에서 진행한다.
 */
interface OriginalPhotoPrefetcher {
    fun prefetch(localIds: List<String>)

    /** 미리 받은 원본 캐시를 지운다. 받는 중이면 끝난 뒤에 지운다. */
    fun releasePrefetched(localIds: Set<String>)

    /**
     * 앱 시작 때 한 번, [keep](복원된 백그라운드 저장의 사진) 외의 원본 캐시를 지운다.
     * 앱이 종료돼 기록 작성 정리가 불리지 않은 원본이 쌓이지 않게 한다.
     */
    fun pruneCache(keep: Set<String>) {}
}

/**
 * 기록 작성 한 번 동안 미리 받은 원본을 추적한다.
 * [protectedIds]는 백그라운드 저장을 기다리는 사진처럼 곧 원본을 읽을 사진이라 지우지 않는다.
 */
internal class OriginalPrefetchSession(
    private val prefetcher: OriginalPhotoPrefetcher,
    private val protectedIds: () -> Set<String>,
) {
    private var prefetchedIds: Set<String> = emptySet()
    private var closed = false

    /** [localIds]는 업로드 순서(대표 사진 먼저)로 넘긴다. */
    fun select(localIds: List<String>) {
        if (closed) return
        releaseUnprotected(prefetchedIds - localIds.toSet())
        prefetchedIds = localIds.toSet()
        prefetcher.prefetch(localIds)
    }

    /** 기록 작성을 마치거나 그만둘 때 부른다. 저장된 사진은 업로드 후 저장 쪽이 이미 지운다. */
    fun close() {
        if (closed) return
        closed = true
        releaseUnprotected(prefetchedIds)
        prefetchedIds = emptySet()
    }

    private fun releaseUnprotected(localIds: Set<String>) {
        val released = localIds - protectedIds()
        if (released.isNotEmpty()) prefetcher.releasePrefetched(released)
    }
}
