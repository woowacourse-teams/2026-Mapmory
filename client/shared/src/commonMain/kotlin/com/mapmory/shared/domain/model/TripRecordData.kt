package com.mapmory.shared.domain.model

// 상세 응답과 편집 화면에서 사용하는 여행 기록 데이터다.
// 서버의 지역 코드는 RegionCatalog의 로컬 ID로 변환하고, object key 순서를 미디어로 보존한다.
data class TripRecordData(
    val id: Long,
    val locationId: Long,
    val title: String,
    val content: String,
    val startDate: String,
    val endDate: String?,
    val media: List<TripRecordMedia>,
    val createdAt: String,
    val updatedAt: String,
    val thumbnailUrl: String? = null,
)

/** 목록 API가 반환하는 얇은 모델이다. 본문과 전체 미디어는 상세 조회에서만 가져온다. */
data class TripRecordSummary(
    val id: Long,
    val title: String,
    val regionName: String? = null,
    val startDate: String,
    val endDate: String?,
    val thumbnailUrl: String? = null,
    val thumbnailUrlExpiresIn: Long? = null,
    val thumbnailPreviewBytes: ByteArray? = null,
    // 로컬 저장소에서는 기존 사진 표시를 유지하기 위해 아래 값을 함께 보존한다.
    val locationId: Long? = null,
    val content: String = "",
    val media: List<TripRecordMedia> = emptyList(),
)

data class TripRecordDraft(
    val locationId: Long,
    val title: String,
    val content: String,
    val startDate: String?,
    val endDate: String?,
    val mediaObjectKeys: List<String>,
    // API 요청에는 object key만 사용하고, 로컬 저장소에서는 선택한 사진 표시 데이터를 보존한다.
    val localMedia: List<TripRecordMediaDraft> = emptyList(),
)

fun TripRecordDraft.dateValidationError(): String? = when {
    startDate == null -> "시작일을 입력해 주세요."
    !startDate.isValidIsoDate() -> "올바른 시작일을 입력해 주세요."
    endDate != null && !endDate.isValidIsoDate() -> "올바른 종료일을 입력해 주세요."
    endDate != null && endDate < startDate -> "종료일은 시작일보다 빠를 수 없습니다."
    else -> null
}

private fun String.isValidIsoDate(): Boolean {
    if (!matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false

    val year = substring(0, 4).toInt()
    val month = substring(5, 7).toInt()
    val day = substring(8, 10).toInt()
    val daysInMonth = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> return false
    }
    return day in 1..daysInMonth
}

data class TripRecordQuery(
    val locationId: Long? = null,
    val page: Int = 0,
    val size: Int = 20,
)

data class TripRecordPage(
    val records: List<TripRecordSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
