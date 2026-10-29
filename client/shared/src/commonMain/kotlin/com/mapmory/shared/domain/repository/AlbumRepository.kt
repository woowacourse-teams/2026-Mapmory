package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.group.AlbumDetail
import com.mapmory.shared.domain.group.AlbumPage

interface AlbumRepository {
    suspend fun list(groupId: Long, page: Int = 0): Result<AlbumPage>
    suspend fun detail(groupId: Long, albumId: Long): Result<AlbumDetail>
    suspend fun create(groupId: Long, recordId: Long, name: String? = null, regionId: Long? = null): Result<AlbumDetail>
    suspend fun addRecord(groupId: Long, albumId: Long, recordId: Long): Result<AlbumDetail>
    suspend fun update(groupId: Long, albumId: Long, name: String? = null, regionId: Long? = null): Result<AlbumDetail>
}
