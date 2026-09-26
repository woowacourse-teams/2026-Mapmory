package com.mapmory.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.random.Random

data class TripRecord(
    val id: Long = Random.nextLong(from = 1L, until = Long.MAX_VALUE),
    val imageUrl: String,
    val startTripDate: LocalDate,
    val location: String,
    val tripRecordTitle: String = "",
    val tripRecordDescription: String? = null,
    val endTripDate: LocalDate? = null,
) {
    init {
        require(imageUrl.isNotBlank()) { "여행 사진은 필수입니다" }
        require(location.isNotBlank()) { "여행 장소는 필수입니다" }
        require(endTripDate == null || startTripDate <= endTripDate) {
            "여행 시작일은 종료일보다 늦을 수 없습니다"
        }
    }
}
