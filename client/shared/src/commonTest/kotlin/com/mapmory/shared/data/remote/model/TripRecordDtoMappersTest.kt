package com.mapmory.shared.data.remote.model

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiDtoMappersTest {
    private val catalog = StaticRegionCatalog()

    @Test
    fun `목록_썸네일_URL과_만료_시간을_도메인으로_매핑한다`() {
        val result = TripRecordListItemDto(
            id = 101,
            title = "제주 여행",
            regionName = "제주시",
            startDate = "2026-08-27",
            endDate = null,
            thumbnailUrl = "https://bucket.example.com/photo.jpg?signature=fresh",
            thumbnailUrlExpiresIn = 300L,
        ).toDomain()

        assertEquals("https://bucket.example.com/photo.jpg?signature=fresh", result.thumbnailUrl)
        assertEquals(300L, result.thumbnailUrlExpiresIn)
    }

    @Test
    fun `상세_지역_코드와_Object_Key를_로컬_도메인으로_매핑한다`() {
        val result = TripRecordDetailDto(
            id = 101,
            title = "비 오는 날의 제주시",
            content = "골목을 걸었다.",
            region = TripRecordRegionDto(
                country = RegionCodeDto("KR", "대한민국"),
                province = RegionCodeDto("49", "제주특별자치도"),
                district = RegionCodeDto("50110", "제주시"),
            ),
            startDate = "2026-08-11",
            endDate = null,
            objectKeys = listOf("travel-records/guest/a.jpg"),
            createdAt = "2026-08-14T10:30:00",
            updatedAt = "2026-08-15T09:00:00",
        ).toDomain(catalog)

        assertEquals(catalog.requireByCode("50110").id, result.locationId)
        assertEquals("travel-records/guest/a.jpg", result.media.single().objectKey)
        assertEquals(0, result.media.single().sortOrder)
    }

    @Test
    fun `조회용_URL이_포함된_미디어_응답을_Object_Key와_함께_매핑한다`() {
        val result = TripRecordDetailDto(
            id = 101,
            title = "제주 여행",
            content = "",
            region = TripRecordRegionDto(
                country = RegionCodeDto("KR", "대한민국"),
                province = RegionCodeDto("49", "제주특별자치도"),
                district = RegionCodeDto("50110", "제주시"),
            ),
            startDate = "2026-08-11",
            endDate = null,
            objectKeys = listOf("이 값보다 media를 우선한다"),
            media = listOf(
                TripRecordMediaDto(
                    id = 55,
                    objectKey = "travel-records/10/photo.jpg",
                    viewUrl = "https://bucket.example.com/photo.jpg?signature=first",
                    viewUrlExpiresIn = 300,
                    sortOrder = 2,
                ),
            ),
            createdAt = "2026-08-14T10:30:00",
            updatedAt = "2026-08-15T09:00:00",
        ).toDomain(catalog)

        assertEquals(55, result.media.single().id)
        assertEquals("travel-records/10/photo.jpg", result.media.single().objectKey)
        assertEquals(2, result.media.single().sortOrder)
        assertEquals(
            "https://bucket.example.com/photo.jpg?signature=first",
            result.media.single().url,
        )
    }

    @Test
    fun `국내_시군구_초안을_서버_코드_경로로_매핑한다`() {
        val jejuCity = catalog.requireByCode("50110")

        val request = TripRecordDraft(
            locationId = jejuCity.id,
            title = "제주 여행",
            content = "본문",
            startDate = "2026-08-11",
            endDate = null,
            mediaObjectKeys = listOf("travel-records/guest/a.jpg"),
        ).toRequestDto(catalog)

        assertEquals("KR", request.countryCode)
        assertEquals("49", request.provinceCode)
        assertEquals("50110", request.districtCode)
        assertEquals(listOf("travel-records/guest/a.jpg"), request.objectKeys)
    }

    @Test
    fun `해외_초안은_국가_코드만_매핑한다`() {
        val japan = catalog.requireByCode("JP")

        val request = TripRecordDraft(
            locationId = japan.id,
            title = "일본 여행",
            content = "",
            startDate = "2026-08-11",
            endDate = null,
            mediaObjectKeys = emptyList(),
        ).toRequestDto(catalog)

        assertEquals("JP", request.countryCode)
        assertEquals(null, request.provinceCode)
        assertEquals(null, request.districtCode)
    }

    @Test
    fun `시작일이_없는_생성_요청은_네트워크_호출_전에_거부한다`() {
        val japan = catalog.requireByCode("JP")

        assertFailsWith<IllegalArgumentException> {
            TripRecordDraft(
                locationId = japan.id,
                title = "일본 여행",
                content = "",
                startDate = null,
                endDate = null,
                mediaObjectKeys = emptyList(),
            ).toRequestDto(catalog)
        }
    }

    @Test
    fun `시도_목록_필터를_국가와_시도_코드로_매핑한다`() {
        val jejuProvince = catalog.requireByCode("KR-49")

        val path = TripRecordQuery(locationId = jejuProvince.id).toRegionQuery(catalog)

        assertEquals("KR", path?.countryCode)
        assertEquals("49", path?.provinceCode)
        assertEquals(null, path?.districtCode)
    }

    @Test
    fun `국내_기록은_시군구가_필수이고_제목은_200자로_제한한다`() {
        val seoul = catalog.requireByCode("KR-11")
        val japan = catalog.requireByCode("JP")

        assertFailsWith<IllegalArgumentException> {
            draft(locationId = seoul.id, title = "서울 여행").toRequestDto(catalog)
        }
        assertFailsWith<IllegalArgumentException> {
            draft(locationId = japan.id, title = "가".repeat(201)).toRequestDto(catalog)
        }
    }

    private fun draft(locationId: Long, title: String) = TripRecordDraft(
        locationId = locationId,
        title = title,
        content = "",
        startDate = "2026-08-11",
        endDate = null,
        mediaObjectKeys = emptyList(),
    )
}
