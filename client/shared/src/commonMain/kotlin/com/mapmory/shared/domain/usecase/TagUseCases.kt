package com.mapmory.shared.domain.usecase

import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TagRules
import com.mapmory.shared.domain.repository.TagRepository

class GetTagsUseCase(
    private val repository: TagRepository,
) {
    suspend operator fun invoke(): Result<List<Tag>> = repository.getTags()
}

class CreateTagUseCase(
    private val repository: TagRepository,
) {
    suspend operator fun invoke(
        name: String,
        existingTags: Collection<Tag>,
    ): Result<Tag> = runCatching {
        TagRules.normalizeAndValidateNewName(name, existingTags)
    }.fold(
        onSuccess = { normalizedName -> repository.createTag(normalizedName) },
        onFailure = { error -> Result.failure(error) },
    )
}
