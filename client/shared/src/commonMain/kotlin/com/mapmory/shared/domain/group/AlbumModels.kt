package com.mapmory.shared.domain.group

import kotlinx.serialization.Serializable

@Serializable data class AlbumRegion(val id: Long, val code: String, val type: String, val name: String)
@Serializable data class AlbumMember(val memberId: Long, val nickname: String, val me: Boolean)
@Serializable data class AlbumPhotoUrl(val viewUrl: String, val viewUrlExpiresIn: Long)
@Serializable data class AlbumPhoto(val id: Long, val viewUrl: String, val viewUrlExpiresIn: Long, val sortOrder: Int)
@Serializable data class AlbumEntry(val travelRecordId: Long, val owner: AlbumMember, val title: String?, val content: String?, val startDate: String, val endDate: String?, val placeName: String?, val photos: List<AlbumPhoto>, val addedAt: String)
@Serializable data class AlbumSummary(val id: Long, val name: String, val region: AlbumRegion, val startDate: String?, val endDate: String?, val coverPhoto: AlbumPhotoUrl?, val contributors: List<AlbumMember>, val recordCount: Int, val photoCount: Int, val lastAddedAt: String)
@Serializable data class AlbumPage(val items: List<AlbumSummary>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int, val hasNext: Boolean)
@Serializable data class AlbumDetail(val id: Long, val groupId: Long, val name: String, val region: AlbumRegion, val startDate: String?, val endDate: String?, val opener: AlbumMember, val myTravelRecordIds: List<Long>, val entries: List<AlbumEntry>)
