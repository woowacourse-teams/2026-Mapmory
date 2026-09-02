package com.mapmory.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TagRulesTest {
    @Test
    fun `태그 이름의 앞뒤와 연속 공백을 정규화한다`() {
        assertEquals("서울 맛집", TagRules.normalizeAndValidateName("  서울   맛집  "))
    }

    @Test
    fun `빈 이름과 해시태그 문자와 30자 초과 이름을 거부한다`() {
        assertFailsWith<IllegalArgumentException> { TagRules.normalizeAndValidateName("   ") }
        assertFailsWith<IllegalArgumentException> { TagRules.normalizeAndValidateName("#맛집") }
        assertFailsWith<IllegalArgumentException> { TagRules.normalizeAndValidateName("가".repeat(31)) }
    }

    @Test
    fun `새 태그는 사용자당 10개 제한과 대소문자 무시 중복을 검증한다`() {
        val existing = listOf(Tag(1, "Family"))
        assertFailsWith<IllegalArgumentException> {
            TagRules.normalizeAndValidateNewName("family", existing)
        }

        val full = (1L..10L).map { Tag(it, "태그$it") }
        assertFailsWith<IllegalArgumentException> {
            TagRules.normalizeAndValidateNewName("새 태그", full)
        }
    }

    @Test
    fun `기록 태그 ID는 최대 5개이며 중복 없이 양수여야 한다`() {
        TagRules.validateRecordTagIds(listOf(1, 2, 3, 4, 5))
        assertFailsWith<IllegalArgumentException> {
            TagRules.validateRecordTagIds(listOf(1, 2, 3, 4, 5, 6))
        }
        assertFailsWith<IllegalArgumentException> { TagRules.validateRecordTagIds(listOf(1, 1)) }
        assertFailsWith<IllegalArgumentException> { TagRules.validateRecordTagIds(listOf(0)) }
    }
}
