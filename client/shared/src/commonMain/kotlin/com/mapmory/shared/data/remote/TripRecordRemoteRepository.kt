package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.IdResponseDto
import com.mapmory.shared.data.remote.model.PageDto
import com.mapmory.shared.data.remote.model.TripRecordDetailDto
import com.mapmory.shared.data.remote.model.TripRecordListItemDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.data.remote.model.toRegionQuery
import com.mapmory.shared.data.remote.model.toRequestDto
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.TripRecordRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TripRecordRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
    private val regionCatalog: RegionCatalog,
) : TripRecordRepository {
    private val recordsUrl = "${apiBaseUrl.trimEnd('/')}/travel-records"

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        apiCall {
            require(query.page >= 0) { "페이지 번호는 0 이상이어야 합니다." }
            require(query.size in 1..MaxPageSize) { "페이지 크기는 1 이상 100 이하여야 합니다." }
            val region = query.toRegionQuery(regionCatalog)
            val response = client.get(recordsUrl) {
                authorizeWith(accessTokenProvider)
                region?.countryCode?.let { parameter("countryCode", it) }
                region?.provinceCode?.let { parameter("provinceCode", it) }
                region?.districtCode?.let { parameter("districtCode", it) }
                parameter("page", query.page)
                parameter("size", query.size)
            }.requireSuccess()

            response.body<ApiResponseDto<PageDto<TripRecordListItemDto>>>().data.toDomain()
        }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        apiCall {
            requireValidRecordId(id)
            client.get("$recordsUrl/$id") {
                authorizeWith(accessTokenProvider)
            }.requireSuccess()
                .body<ApiResponseDto<TripRecordDetailDto>>()
                .data
                .toDomain(regionCatalog)
        }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        apiCall {
            val id = client.post(recordsUrl) {
                authorizeWith(accessTokenProvider)
                contentType(ContentType.Application.Json)
                setBody(draft.toRequestDto(regionCatalog))
            }.requireSuccess()
                .body<ApiResponseDto<IdResponseDto>>()
                .data
                .id

            getTripRecord(id).getOrThrow()
        }

    override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft): Result<TripRecordData> =
        apiCall {
            requireValidRecordId(id)
            client.put("$recordsUrl/$id") {
                authorizeWith(accessTokenProvider)
                contentType(ContentType.Application.Json)
                setBody(draft.toRequestDto(regionCatalog))
            }.requireSuccess()
                .body<ApiResponseDto<TripRecordDetailDto>>()
                .data
                .toDomain(regionCatalog)
        }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        apiCall {
            requireValidRecordId(id)
            client.delete("$recordsUrl/$id") {
                authorizeWith(accessTokenProvider)
            }.requireSuccess()
        }

    private fun requireValidRecordId(id: Long) {
        require(id > 0) { "여행 기록 ID는 양수여야 합니다." }
    }
}

private const val MaxPageSize = 100
