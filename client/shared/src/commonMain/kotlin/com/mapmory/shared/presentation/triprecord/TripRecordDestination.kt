package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType

/**
 * 여행 기록 API가 저장할 수 있는 지역 단계와 화면의 선택지를 일치시킨다.
 * 국내는 시·군·구, 해외는 국가까지만 기록할 수 있다.
 */
internal fun Location.isSelectableTripRecordDestination(): Boolean = when (type) {
    LocationType.DISTRICT -> countryId == KoreaCountryId
    LocationType.PROVINCE ->
        countryId != KoreaCountryId &&
            regionCode != KoreaCountryCode &&
            regionCode.length == CountryCodeLength
}

internal fun List<Location>.selectableTripRecordDestinations(): List<Location> =
    filter(Location::isSelectableTripRecordDestination)
        .distinctBy(Location::regionCode)

private const val KoreaCountryId = 1L
private const val KoreaCountryCode = "KR"
private const val CountryCodeLength = 2
