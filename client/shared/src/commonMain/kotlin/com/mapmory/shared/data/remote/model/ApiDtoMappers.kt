package com.mapmory.shared.data.remote.model

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.MapRegionLevel
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TagRules
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.model.TopRegionStatistics
import com.mapmory.shared.domain.model.isSelectableKoreanProvince
import com.mapmory.shared.domain.model.dateValidationError
import com.mapmory.shared.domain.region.RegionCatalog
import kotlinx.datetime.LocalDate

fun TripRecordListItemDto.toDomain(regionCatalog: RegionCatalog? = null): TripRecordSummary = TripRecordSummary(
    id = id,
    photoCount = photoCount,
    title = title.orEmpty(),
    regionName = regionName,
    startDate = startDate,
    endDate = endDate,
    thumbnailUrl = thumbnailUrl,
    thumbnailUrlExpiresIn = thumbnailUrlExpiresIn,
    tags = tags.map(TagDto::toDomain),
    locationId = region?.let { regionData ->
        regionCatalog?.let { regionData.findLocation(it)?.id }
    },
)

fun TagDto.toDomain(): Tag = Tag(id = id, name = TagRules.normalizeAndValidateName(name))

fun TripRecordDetailDto.toDomain(regionCatalog: RegionCatalog): TripRecordData {
    val location = region.findLocation(regionCatalog)
    requireNotNull(location) {
        "여행 장소 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
    }

    return TripRecordData(
        id = id,
        locationId = location.id,
        title = title.orEmpty(),
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
                    thumbnailUrl = item.thumbnailUrl,
                    capturedAt = item.capturedDate.toLocalCapturedDate(),
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
        tags = tags.map(TagDto::toDomain),
        place = placeId?.let { id ->
            PlaceReference(
                placeId = id,
                name = placeName,
                attribution = placeAttribution,
                attributionUrl = placeAttributionUrl,
            )
        },
    )
}

fun PlaceCandidateDto.toDomain(): PlaceCandidate = PlaceCandidate(
    placeId = placeId,
    name = name,
    address = address,
    attribution = attribution,
    attributionUrl = attributionUrl,
)

fun PlaceSelectionDto.toDomain(): PlaceSelection = PlaceSelection(
    place = PlaceReference(
        placeId = placeId,
        name = name,
        attribution = attribution,
        attributionUrl = attributionUrl,
        countryCode = countryCode,
    ),
    countryCode = countryCode,
    suggestedRegion = suggestedRegion?.let { region ->
        PlaceRegionSuggestion(
            countryCode = region.country.code,
            provinceCode = region.province?.code,
            districtCode = region.district?.code,
        )
    },
    manualRegionRequired = manualRegionRequired,
)

internal data class RegionQuery(
    val countryCode: String,
    val provinceCode: String? = null,
    val districtCode: String? = null,
)

internal fun TripRecordQuery.toRegionQuery(regionCatalog: RegionCatalog): RegionQuery? =
    locationId?.let { id ->
        val location = requireNotNull(regionCatalog.findById(id)) {
            "선택한 지역 정보를 확인하지 못했습니다. 여행 장소를 다시 선택해 주세요."
        }
        location.toRegionQuery(regionCatalog, allowKoreanProvince = true)
    }

internal fun TripRecordDraft.toRequestDto(regionCatalog: RegionCatalog): TripRecordRequestDto {
    dateValidationError()?.let { error -> throw IllegalArgumentException(error) }
    require(mediaObjectKeys.isNotEmpty()) { TripRecordPhotoRules.RequiredMessage }
    require(mediaObjectKeys.distinct().size == mediaObjectKeys.size) {
        "같은 사진을 중복해서 추가할 수 없습니다."
    }
    TagRules.validateRecordTagIds(tagIds)
    val location = requireNotNull(regionCatalog.findById(locationId)) {
        "선택한 지역 정보를 확인하지 못했습니다. 여행 장소를 다시 선택해 주세요."
    }
    val region = location.toRegionQuery(regionCatalog, allowKoreanProvince = false)
    return TripRecordRequestDto(
        countryCode = region.countryCode,
        provinceCode = region.provinceCode,
        districtCode = region.districtCode,
        title = title,
        content = content.orEmpty(),
        startDate = startDate,
        endDate = endDate,
        photos = photoRequests(),
        tagIds = tagIds,
        placeId = place?.placeId,
    )
}

private fun TripRecordDraft.photoRequests(): List<TripRecordPhotoRequestDto> {
    val capturedDateByObjectKey = localMedia.associate { media -> media.objectKey to media.capturedAt }
    return mediaObjectKeys.map { objectKey ->
        TripRecordPhotoRequestDto(
            objectKey = objectKey,
            capturedDate = capturedDateByObjectKey[objectKey].toIsoCapturedDate(),
        )
    }
}

/** 사진첩마다 다른 촬영일 표기(yyyy.MM.dd, yyyy-MM-ddTHH:mm 등)를 서버가 받는 yyyy-MM-dd로 맞춘다. */
internal fun String?.toIsoCapturedDate(): String? {
    val match = CapturedDatePattern.find(this?.trim().orEmpty()) ?: return null
    val (year, month, day) = match.destructured
    val iso = "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
    return iso.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
}

/** 서버 촬영일(yyyy-MM-dd)을 기기 사진첩과 같은 yyyy.MM.dd로 바꿔 한 기록 안에서 표기와 정렬이 섞이지 않게 한다. */
internal fun String?.toLocalCapturedDate(): String? = toIsoCapturedDate()?.replace('-', '.')

// 앞에 숫자가 더 붙은 값(12026.09.11)은 날짜로 보지 않는다. EXIF 원문(yyyy:MM:dd)도 받는다.
private val CapturedDatePattern = Regex("(?<!\\d)(\\d{4})[.\\-/:](\\d{1,2})[.\\-/:](\\d{1,2})")

private fun Location.toRegionQuery(
    regionCatalog: RegionCatalog,
    allowKoreanProvince: Boolean,
): RegionQuery = when {
    type == LocationType.DISTRICT -> {
        val province = requireNotNull(parentId?.let(regionCatalog::findById)) {
            "선택한 지역 정보를 확인하지 못했습니다. 여행 장소를 다시 선택해 주세요."
        }
        require(province.regionCode.startsWith(KoreanProvincePrefix)) {
            "국내 여행은 시·군·구 단위로 선택해 주세요."
        }
        RegionQuery(
            countryCode = KoreaCountryCode,
            provinceCode = province.regionCode.removePrefix(KoreanProvincePrefix),
            districtCode = regionCode,
        )
    }

    regionCode.startsWith(KoreanProvincePrefix) -> {
        require(allowKoreanProvince || isSelectableKoreanProvince()) {
            "특별시·광역시·세종·제주 외의 국내 지역은 시·군·구까지 선택해 주세요."
        }
        RegionQuery(
            countryCode = KoreaCountryCode,
            provinceCode = regionCode.removePrefix(KoreanProvincePrefix),
        )
    }

    regionCode.length == CountryCodeLength -> RegionQuery(countryCode = regionCode)
    else -> error("선택한 지역 정보를 확인하지 못했습니다. 여행 장소를 다시 선택해 주세요.")
}

fun PageDto<TripRecordListItemDto>.toDomain(regionCatalog: RegionCatalog? = null): TripRecordPage = TripRecordPage(
    records = items.map { it.toDomain(regionCatalog) },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

private fun TripRecordRegionDto.findLocation(regionCatalog: RegionCatalog) = when {
    district != null && province != null -> regionCatalog.findDistrict(
        provinceCode = "KR-${province.code}",
        districtCode = district.code,
    )

    province != null -> regionCatalog.findByCode("KR-${province.code}")
    else -> regionCatalog.findByCode(country.code)
}

fun MapRegionSummaryDto.toDomain(): MapRegionSummary = MapRegionSummary(
    regionId = regionId,
    code = code,
    type = MapRegionType.valueOf(regionType),
    name = name,
    count = count,
    level = MapRegionLevel.valueOf(level),
)

fun TripStatisticsDto.toDomain(): TripStatistics = TripStatistics(
    recordCount = recordCount,
    mediaCount = mediaCount,
    visitedCountryCount = visitedCountryCount,
    visitedKoreaDistrictCount = visitedKoreaDistrictCount,
    visitedCountryCodes = visitedCountryCodes,
    topRegions = topRegions.map { region ->
        TopRegionStatistics(
            regionId = region.regionId,
            code = region.code,
            type = MapRegionType.valueOf(region.regionType),
            name = region.name,
            recordCount = region.recordCount,
        )
    },
)

private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"
private const val CountryCodeLength = 2
