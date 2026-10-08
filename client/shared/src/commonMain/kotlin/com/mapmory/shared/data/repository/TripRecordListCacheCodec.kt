package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object TripRecordListCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(page: TripRecordPage): String = json.encodeToString(
        CachedPage(
            records = page.records.map { record ->
                CachedRecord(record.id, record.title, record.regionName, record.startDate, record.endDate,
                    record.locationId, record.content, record.tags.map { CachedTag(it.id, it.name) })
            },
            page = page.page,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        ),
    )

    fun decode(payload: String): TripRecordPage? = runCatching {
        val cached = json.decodeFromString<CachedPage>(payload)
        TripRecordPage(
            records = cached.records.map { record ->
                TripRecordSummary(
                    id = record.id, title = record.title, regionName = record.regionName,
                    startDate = record.startDate, endDate = record.endDate,
                    locationId = record.locationId, content = record.content,
                    tags = record.tags.map { Tag(it.id, it.name) },
                )
            },
            page = cached.page, size = cached.size,
            totalElements = cached.totalElements, totalPages = cached.totalPages,
        )
    }.getOrNull()
}

@Serializable
private data class CachedPage(
    val records: List<CachedRecord>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int,
)

@Serializable
private data class CachedRecord(
    val id: Long, val title: String, val regionName: String?, val startDate: String,
    val endDate: String?, val locationId: Long?, val content: String, val tags: List<CachedTag>,
)

@Serializable
private data class CachedTag(val id: Long, val name: String)
