package com.mapmory.shared.domain.region

import com.mapmory.shared.domain.model.Location

interface RegionCatalog {
    val locations: List<Location>

    fun findById(id: Long): Location? = locations.firstOrNull { it.id == id }

    fun findByCode(code: String): Location? = locations.firstOrNull { it.regionCode == code }

    fun findDistrict(
        provinceCode: String,
        districtCode: String,
    ): Location?

    fun requireByCode(code: String): Location =
        requireNotNull(findByCode(code)) { "선택한 지역 정보를 확인하지 못했습니다." }
}
