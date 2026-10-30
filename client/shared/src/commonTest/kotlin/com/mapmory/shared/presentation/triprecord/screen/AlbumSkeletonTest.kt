package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.data.remote.model.TripRecordListItemDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.data.repository.TripRecordListCacheCodec
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.navigation.DetailRoute
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumSkeletonTest {
    @Test
    fun `목록 응답의 4장은 캐시와 상세 경로를 거쳐 스켈레톤 4칸이 된다`() {
        val dto = Json.decodeFromString<TripRecordListItemDto>(
            """{"id":1,"regionName":"일본","startDate":"2026-10-14","endDate":null,"photoCount":4}""",
        )
        val page = TripRecordPage(listOf(dto.toDomain()), 0, 20, 1, 1)
        val cached = requireNotNull(TripRecordListCacheCodec.decode(TripRecordListCacheCodec.encode(page)))
        val item = cached.records.single().copy(thumbnailPreviewBytes = byteArrayOf(1)).toTripRecordItemUiState()
        assertEquals(1, item.photos.size)
        assertEquals(4, item.photoCount)
        val route = DetailRoute(item.id, item.locationName, item.endDate ?: item.startDate, item.photoCount)
        val restored = Json.decodeFromString<DetailRoute>(Json.encodeToString(route))
        assertEquals(listOf(2, 2), albumSkeletonRowSizes(restored.photoCount, 2))
    }

    @Test
    fun `이전 응답과 캐시는 개수가 없으면 한 칸을 표시한다`() {
        val dto = Json.decodeFromString<TripRecordListItemDto>(
            """{"id":1,"regionName":"일본","startDate":"2026-10-14","endDate":null}""",
        )
        assertEquals(listOf(1), albumSkeletonRowSizes(dto.toDomain().toTripRecordItemUiState().photoCount, 2))
        val cached = requireNotNull(TripRecordListCacheCodec.decode(
            """{"records":[{"id":1,"title":"","regionName":"일본","startDate":"2026-10-14","endDate":null,"locationId":null,"content":"","tags":[]}],"page":0,"size":20,"totalElements":1,"totalPages":1}""",
        ))
        assertEquals(listOf(1), albumSkeletonRowSizes(cached.records.single().photoCount, 2))
    }

    @Test
    fun `사진 수와 열 수가 달라도 마지막 행에 남은 개수만 표시한다`() {
        assertEquals(listOf(2, 2, 1), albumSkeletonRowSizes(5, 2))
        assertEquals(listOf(3, 1), albumSkeletonRowSizes(4, 3))
        assertEquals(emptyList(), albumSkeletonRowSizes(0, 2))
        assertEquals(emptyList(), albumSkeletonRowSizes(-1, 2))
    }
}
