package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.Tag

interface TagRepository {
    suspend fun getTags(): Result<List<Tag>>

    suspend fun createTag(name: String): Result<Tag>
}
