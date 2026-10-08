package com.mapmory.shared.domain.model

data class PlaceCandidate(
    val placeId: String,
    val name: String,
    val address: String?,
    val attribution: String?,
    val attributionUrl: String?,
)

data class PlaceReference(
    val placeId: String,
    // Google 장소는 서버가 이름을 저장하지 않아 저장된 기록에서는 null이다.
    val name: String?,
    val address: String? = null,
    val attribution: String? = null,
    val attributionUrl: String? = null,
    val countryCode: String? = null,
)

data class PlaceRegionSuggestion(
    val countryCode: String,
    val provinceCode: String? = null,
    val districtCode: String? = null,
)

object PlaceRules {
    const val MinQueryLength = 1
    const val MaxQueryLength = 100
    const val MaxCandidates = 10
    const val MaxPlaceIdLength = 255

    // 한 글자 지명(괌)은 찾되, 한글을 조합하는 중(ㄱ, 경보ㄱ)에는 검색하지 않는다.
    fun isSearchableQuery(query: String): Boolean {
        val normalized = query.trim()
        return normalized.length in MinQueryLength..MaxQueryLength && !normalized.last().isHangulJamo()
    }

    private fun Char.isHangulJamo(): Boolean = this in '\u1100'..'\u11FF' || this in '\u3131'..'\u318E'
}

// 서버는 Google 장소에 attribution "Google Maps"와 Google 지도 주소를 보낸다. 둘 중 하나만 맞아도 Google로 본다.
object PlaceAttribution {
    const val GoogleMaps = "Google Maps"
    private const val GoogleMapsUrl = "https://www.google.com/maps"

    fun isGoogleMaps(attribution: String?, attributionUrl: String?): Boolean =
        attribution?.trim().equals(GoogleMaps, ignoreCase = true) ||
            attributionUrl?.trim()?.startsWith(GoogleMapsUrl) == true
}

data class PlaceSelection(
    val place: PlaceReference,
    val countryCode: String?,
    val suggestedRegion: PlaceRegionSuggestion?,
    val manualRegionRequired: Boolean,
)
