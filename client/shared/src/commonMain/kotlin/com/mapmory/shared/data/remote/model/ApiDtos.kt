package com.mapmory.shared.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetailDto(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val errors: List<ProblemFieldErrorDto> = emptyList(),
)

@Serializable
data class ProblemFieldErrorDto(
    val field: String,
    val detail: String,
)

@Serializable
data class ApiResponseDto<T>(
    val data: T,
)

@Serializable
data class GuestLoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val isNewMember: Boolean,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class PresignedUploadRequestDto(
    val files: List<UploadFileRequestDto>,
)

@Serializable
data class UploadFileRequestDto(
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
)

@Serializable
data class PresignedUploadsDto(
    val uploads: List<PresignedUploadDto>,
)

@Serializable
data class PresignedUploadDto(
    val objectKey: String,
    val presignedUrl: String,
    val method: String,
    val contentType: String,
    val expiresIn: Long,
)

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

@Serializable
data class TripRecordListItemDto(
    val id: Long,
    val title: String,
    val regionName: String,
    val startDate: String,
    val endDate: String?,
    val thumbnailUrl: String? = null,
    val thumbnailUrlExpiresIn: Long? = null,
)

@Serializable
data class RegionCodeDto(
    val code: String,
    val name: String,
)

@Serializable
data class TripRecordRegionDto(
    val country: RegionCodeDto,
    val province: RegionCodeDto? = null,
    val district: RegionCodeDto? = null,
)

@Serializable
data class TripRecordDetailDto(
    val id: Long,
    val title: String,
    val content: String,
    val region: TripRecordRegionDto,
    val startDate: String,
    val endDate: String?,
    val objectKeys: List<String> = emptyList(),
    val media: List<TripRecordMediaDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class TripRecordMediaDto(
    val id: Long,
    val objectKey: String,
    val viewUrl: String,
    val viewUrlExpiresIn: Long,
    val sortOrder: Int,
)

@Serializable
data class TripRecordRequestDto(
    val countryCode: String,
    val provinceCode: String?,
    val districtCode: String?,
    val title: String,
    val content: String,
    val startDate: String,
    val endDate: String?,
    val objectKeys: List<String> = emptyList(),
)

@Serializable
data class IdResponseDto(
    val id: Long,
)

@Serializable
data class MapRegionSummaryDto(
    val regionId: Long,
    val code: String,
    val regionType: String,
    val name: String,
    val count: Long,
    val level: String,
)
