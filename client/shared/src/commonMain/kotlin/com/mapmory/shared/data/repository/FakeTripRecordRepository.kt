package com.mapmory.shared.data.repository

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.MapRegionLevel
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.dateValidationError
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.TripRecordRepository

/** 서버 API를 연결하기 전 기록 흐름을 확인하는 메모리 기반 구현이다. */
class FakeTripRecordRepository(
    private val regionCatalog: RegionCatalog = StaticRegionCatalog(),
    private val now: () -> String,
) : TripRecordRepository, MapSummaryRepository {
    private val records = mutableListOf<TripRecordData>()
    private var nextRecordId = 1L
    private var nextMediaId = 1L

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        if (query.page < 0 || query.size !in 1..MaxPageSize) {
            return Result.failure(IllegalArgumentException("페이지 번호와 크기를 확인해 주세요."))
        }

        val filteredRecords = records.filter { record ->
            query.locationId == null || record.locationId == query.locationId
        }
        val totalPages = (filteredRecords.size + query.size - 1) / query.size
        val pageRecords = filteredRecords.drop(query.page * query.size).take(query.size)

        return Result.success(
            TripRecordPage(
                records = pageRecords.map(TripRecordData::toSummary),
                page = query.page,
                size = query.size,
                totalElements = filteredRecords.size.toLong(),
                totalPages = totalPages,
            ),
        )
    }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        records.find { it.id == id }?.let(Result.Companion::success)
            ?: Result.failure(NoSuchElementException("여행 기록을 찾을 수 없습니다."))

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
        draft.dateValidationError()?.let { return Result.failure(IllegalArgumentException(it)) }
        val timestamp = now()
        val record = TripRecordData(
            id = nextRecordId++,
            locationId = draft.locationId,
            title = draft.title,
            content = draft.content,
            startDate = requireNotNull(draft.startDate),
            endDate = draft.endDate,
            media = createMedia(draft),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        records += record
        return Result.success(record)
    }

    override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft): Result<TripRecordData> {
        val index = records.indexOfFirst { it.id == id }
        if (index == -1) return Result.failure(NoSuchElementException("여행 기록을 찾을 수 없습니다."))
        draft.dateValidationError()?.let { return Result.failure(IllegalArgumentException(it)) }

        val updatedRecord = records[index].copy(
            locationId = draft.locationId,
            title = draft.title,
            content = draft.content,
            startDate = requireNotNull(draft.startDate),
            endDate = draft.endDate,
            media = createMedia(draft),
            updatedAt = now(),
        )
        records[index] = updatedRecord
        return Result.success(updatedRecord)
    }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> {
        if (!records.removeAll { it.id == id }) {
            return Result.failure(NoSuchElementException("여행 기록을 찾을 수 없습니다."))
        }
        return Result.success(Unit)
    }

    override suspend fun getRootRegions(): Result<List<MapRegionSummary>> = runCatching {
        records.groupBy { record ->
            rootLocation(regionCatalog.findById(record.locationId))
        }.mapNotNull { (location, records) ->
            location?.toSummary(records.size.toLong(), MapRegionType.COUNTRY)
        }.sortedBy(MapRegionSummary::code)
    }

    override suspend fun getChildRegions(regionId: Long): Result<List<MapRegionSummary>> = runCatching {
        val parent = requireNotNull(regionCatalog.findById(regionId)) {
            "지역을 찾을 수 없습니다: $regionId"
        }
        when {
            parent.regionCode == KoreaCountryCode -> koreanProvinceSummaries()
            parent.regionCode.startsWith(KoreanProvincePrefix) -> koreanDistrictSummaries(parent)
            else -> emptyList()
        }
    }

    private fun createMedia(draft: TripRecordDraft): List<TripRecordMedia> {
        val localMediaByObjectKey = draft.localMedia.associateBy { it.objectKey }
        return draft.mediaObjectKeys.mapIndexed { index, objectKey ->
            val localMedia = localMediaByObjectKey[objectKey]
            TripRecordMedia(
                id = nextMediaId++,
                objectKey = objectKey,
                sortOrder = localMedia?.sortOrder ?: index,
                url = null,
                previewBytes = localMedia?.previewBytes?.copyOf(),
                originalBytes = localMedia?.originalBytes?.copyOf(),
                latitude = localMedia?.latitude,
                longitude = localMedia?.longitude,
                capturedAt = localMedia?.capturedAt,
            )
        }
    }

    private fun rootLocation(location: Location?): Location? = when {
        location == null -> null
        location.countryId == KoreaCountryId -> regionCatalog.findByCode(KoreaCountryCode)
        location.regionCode.length == CountryCodeLength -> location
        else -> regionCatalog.findById(location.countryId)
    }

    private fun koreanProvinceSummaries(): List<MapRegionSummary> = records.groupBy { record ->
        val location = regionCatalog.findById(record.locationId)
        when {
            location?.countryId != KoreaCountryId -> null
            location.type == LocationType.PROVINCE -> location
            else -> location.parentId?.let(regionCatalog::findById)
        }
    }.mapNotNull { (location, records) ->
        location?.toSummary(records.size.toLong(), MapRegionType.PROVINCE)
            ?.copy(code = location.regionCode.removePrefix(KoreanProvincePrefix))
    }.sortedBy(MapRegionSummary::code)

    private fun koreanDistrictSummaries(province: Location): List<MapRegionSummary> = records.groupBy { record ->
        regionCatalog.findById(record.locationId)?.takeIf { location ->
            location.type == LocationType.DISTRICT && location.parentId == province.id
        }
    }.mapNotNull { (location, records) ->
        location?.toSummary(records.size.toLong(), MapRegionType.DISTRICT)
    }.sortedBy(MapRegionSummary::code)

    private fun Location.toSummary(count: Long, type: MapRegionType): MapRegionSummary = MapRegionSummary(
        regionId = id,
        code = regionCode,
        type = type,
        name = name,
        count = count,
        level = count.toLevel(),
    )
}

private fun TripRecordData.toSummary(): TripRecordSummary = TripRecordSummary(
    id = id,
    title = title,
    startDate = startDate,
    endDate = endDate,
    thumbnailUrl = thumbnailUrl,
    locationId = locationId,
    content = content,
    media = media,
)

private fun Long.toLevel(): MapRegionLevel = when (this) {
    0L -> MapRegionLevel.NONE
    in 1L..2L -> MapRegionLevel.LOW
    in 3L..5L -> MapRegionLevel.MEDIUM
    else -> MapRegionLevel.HIGH
}

private const val KoreaCountryId = 1L
private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"
private const val CountryCodeLength = 2
private const val MaxPageSize = 100
