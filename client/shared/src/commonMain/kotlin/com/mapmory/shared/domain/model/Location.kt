package com.mapmory.shared.domain.model

// 위치를 저장하기 위한 도메인
data class Location(
    val id: Long, // 지역 자체의 내부 ID
    val countryId: Long, // 이 지역이 속한 국가의 ID
    val parentId: Long?, // DISTRICT가 속한 PROVINCE
    // 국가: ISO 3166-1 alpha-2, 대한민국 시·도: ISO 3166-2, 시·군·구: 행정표준코드
    val regionCode: String,
    val name: String, // 지역명(ex) 서울특별시, 강남구)
    val type: LocationType, // 지역 단계
)

enum class LocationType {
    PROVINCE, // 시, 도 단위(서울특별시, 경기도)
    DISTRICT, // 시, 군, 구 단위(강남구, 수원시)
}

/** 구·군을 고르지 않고 도시 전체를 여행지로 사용할 수 있는 특별시·광역시·세종·제주. */
fun Location.isSelectableKoreanProvince(): Boolean =
    countryId == 1L && type == LocationType.PROVINCE && regionCode in SelectableKoreanProvinceCodes

private val SelectableKoreanProvinceCodes = setOf(
    "KR-11", "KR-26", "KR-27", "KR-28", "KR-29", "KR-30", "KR-31", "KR-50", "KR-49",
)
