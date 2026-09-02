package com.mapmory.shared.domain.model

data class Tag(
    val id: Long,
    val name: String,
) {
    init {
        require(id > 0) { TagRules.InvalidIdMessage }
        require(name == TagRules.normalizeAndValidateName(name)) { TagRules.UnnormalizedNameMessage }
    }
}

object TagRules {
    const val MaxNameLength = 30
    const val MaxTagsPerMember = 10
    const val MaxTagsPerRecord = 5

    const val InvalidIdMessage = "선택한 태그 정보를 확인하지 못했습니다."
    const val EmptyNameMessage = "태그 이름을 입력해 주세요."
    const val HashNotAllowedMessage = "#은 빼고 태그 이름만 입력해 주세요."
    const val NameTooLongMessage = "태그 이름은 30자 이하여야 합니다."
    const val UnnormalizedNameMessage = "태그 이름의 앞뒤 및 연속 공백을 정리해 주세요."
    const val MemberLimitMessage = "태그는 최대 10개까지 만들 수 있습니다."
    const val DuplicateNameMessage = "같은 이름의 태그가 있습니다."
    const val RecordLimitMessage = "태그는 기록당 최대 5개까지 선택할 수 있습니다."
    const val DuplicateIdMessage = "같은 태그를 중복해서 선택할 수 없습니다."

    fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    fun normalizeAndValidateName(value: String): String = normalizeName(value).also { normalized ->
        require(normalized.isNotEmpty()) { EmptyNameMessage }
        require('#' !in normalized) { HashNotAllowedMessage }
        require(normalized.length <= MaxNameLength) { NameTooLongMessage }
    }

    fun normalizeAndValidateNewName(
        value: String,
        existingTags: Collection<Tag>,
    ): String = normalizeAndValidateName(value).also { normalized ->
        require(existingTags.size < MaxTagsPerMember) { MemberLimitMessage }
        require(existingTags.none { it.name.equals(normalized, ignoreCase = true) }) { DuplicateNameMessage }
    }

    fun validateRecordTagIds(tagIds: Collection<Long>) {
        require(tagIds.size <= MaxTagsPerRecord) { RecordLimitMessage }
        require(tagIds.distinct().size == tagIds.size) { DuplicateIdMessage }
        require(tagIds.all { it > 0 }) { InvalidIdMessage }
    }

    fun requireCanAddToRecord(selectedTagIds: Collection<Long>) {
        validateRecordTagIds(selectedTagIds)
        require(selectedTagIds.size < MaxTagsPerRecord) { RecordLimitMessage }
    }
}
