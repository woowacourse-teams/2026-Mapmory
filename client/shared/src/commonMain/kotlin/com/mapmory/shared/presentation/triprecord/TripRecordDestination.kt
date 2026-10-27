package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.isSelectableKoreanProvince

/**
 * 새 기록에서 선택할 수 있는 지역 단계다. 제주도 전체 대신 제주시 또는 서귀포시를 고른다.
 */
internal fun Location.isSelectableTripRecordDestination(): Boolean = when (type) {
    LocationType.DISTRICT -> countryId == KoreaCountryId
    LocationType.PROVINCE ->
        (isSelectableKoreanProvince() && !isJejuProvince()) || (countryId != KoreaCountryId &&
            regionCode != KoreaCountryCode &&
            regionCode.length == CountryCodeLength)
}

internal fun Location.isJejuProvince(): Boolean =
    countryId == KoreaCountryId && type == LocationType.PROVINCE && regionCode == JejuProvinceCode

// 기록 지역의 국가 코드. 국내 시·군·구는 KR, 해외는 국가 코드 자체다.
internal fun Location.tripRecordCountryCode(): String =
    if (countryId == KoreaCountryId) KoreaCountryCode else regionCode

internal fun List<Location>.selectableTripRecordDestinations(): List<Location> =
    filter(Location::isSelectableTripRecordDestination)
        .distinctBy(Location::regionCode)

private const val KoreaCountryId = 1L
private const val KoreaCountryCode = "KR"
private const val CountryCodeLength = 2
private const val JejuProvinceCode = "KR-49"
