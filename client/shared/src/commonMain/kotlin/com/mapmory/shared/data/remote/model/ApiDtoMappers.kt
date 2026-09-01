package com.mapmory.shared.data.remote.model

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

fun TripRecordListItemDto.toDomain(): TripRecordSummary = TripRecordSummary(
    id = id,
    title = title,
    regionName = regionName,
    startDate = startDate,
    endDate = endDate,
    thumbnailUrl = thumbnailUrl,
    thumbnailUrlExpiresIn = thumbnailUrlExpiresIn,
)

fun TripRecordDetailDto.toDomain(regionCatalog: RegionCatalog): TripRecordData {
    val location = when {
        region.district != null && region.province != null -> regionCatalog.findDistrict(
            provinceCode = "KR-${region.province.code}",
            districtCode = region.district.code,
        )

        region.province != null -> regionCatalog.findByCode("KR-${region.province.code}")
        else -> regionCatalog.findByCode(region.country.code)
    }
    requireNotNull(location) {
        "서버 지역 코드를 로컬 지역 데이터에서 찾을 수 없습니다: ${region.country.code}/" +
            "${region.province?.code.orEmpty()}/${region.district?.code.orEmpty()}"
    }

    return TripRecordData(
        id = id,
        locationId = location.id,
        title = title,
        content = content,
        startDate = startDate,
        endDate = endDate,
        media = media
            .map { item ->
                TripRecordMedia(
                    id = item.id,
                    objectKey = item.objectKey,
                    sortOrder = item.sortOrder,
                    url = item.viewUrl,
                )
            }
            .ifEmpty {
                objectKeys.mapIndexed { index, objectKey ->
                    TripRecordMedia(
                        id = index.toLong(),
                        objectKey = objectKey,
                        sortOrder = index,
                        url = null,
                    )
                }
            },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal data class RegionQuery(
    val countryCode: String,
    val provinceCode: String? = null,
    val districtCode: String? = null,
)

internal fun TripRecordQuery.toRegionQuery(regionCatalog: RegionCatalog): RegionQuery? =
    locationId?.let { id ->
        val location = requireNotNull(regionCatalog.findById(id)) {
            "선택한 지역을 찾을 수 없습니다: $id"
        }
        location.toRegionQuery(regionCatalog, allowKoreanProvince = true)
    }

internal fun TripRecordDraft.toRequestDto(regionCatalog: RegionCatalog): TripRecordRequestDto {
    require(title.length <= MaxTitleLength) { "제목은 200자 이하여야 합니다." }
    dateValidationError()?.let { error -> throw IllegalArgumentException(error) }
    require(mediaObjectKeys.distinct().size == mediaObjectKeys.size) {
        "사진 Object Key는 중복될 수 없습니다."
    }
    val location = requireNotNull(regionCatalog.findById(locationId)) {
        "선택한 지역을 찾을 수 없습니다: $locationId"
    }
    val region = location.toRegionQuery(regionCatalog, allowKoreanProvince = false)
    return TripRecordRequestDto(
        countryCode = region.countryCode,
        provinceCode = region.provinceCode,
        districtCode = region.districtCode,
        title = title,
        content = content,
        startDate = requireNotNull(startDate),
        endDate = endDate,
        objectKeys = mediaObjectKeys,
    )
}

private fun Location.toRegionQuery(
    regionCatalog: RegionCatalog,
    allowKoreanProvince: Boolean,
): RegionQuery = when {
    type == LocationType.DISTRICT -> {
        val province = requireNotNull(parentId?.let(regionCatalog::findById)) {
            "시·군·구의 상위 시·도를 찾을 수 없습니다: $regionCode"
        }
        require(province.regionCode.startsWith(KoreanProvincePrefix)) {
            "대한민국 시·군·구만 기록할 수 있습니다: $regionCode"
        }
        RegionQuery(
            countryCode = KoreaCountryCode,
            provinceCode = province.regionCode.removePrefix(KoreanProvincePrefix),
            districtCode = regionCode,
        )
    }

    regionCode.startsWith(KoreanProvincePrefix) -> {
        require(allowKoreanProvince) { "대한민국 기록은 시·군·구까지 선택해야 합니다." }
        RegionQuery(
            countryCode = KoreaCountryCode,
            provinceCode = regionCode.removePrefix(KoreanProvincePrefix),
        )
    }

    regionCode.length == CountryCodeLength -> RegionQuery(countryCode = regionCode)
    else -> error("지원하지 않는 지역 단계입니다: $regionCode")
}

fun PageDto<TripRecordListItemDto>.toDomain(): TripRecordPage = TripRecordPage(
    records = items.map(TripRecordListItemDto::toDomain),
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

fun MapRegionSummaryDto.toDomain(): MapRegionSummary = MapRegionSummary(
    regionId = regionId,
    code = code,
    type = MapRegionType.valueOf(regionType),
    name = name,
    count = count,
    level = MapRegionLevel.valueOf(level),
)

private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"
private const val CountryCodeLength = 2
private const val MaxTitleLength = 200
