package com.mapmory.shared.data.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginalPrefetchPolicyTest {
    @Test
    fun `저데이터_모드에서는_원본을_미리_받지_않는다`() {
        // 사용자가 저장하지 않고 나가면 받은 데이터가 낭비된다(#682 제품 결정).
        assertFalse(shouldPrefetchOriginals(lowDataMode = true))
    }

    @Test
    fun `저데이터_모드가_아니면_셀룰러에서도_원본을_미리_받는다`() {
        assertTrue(shouldPrefetchOriginals(lowDataMode = false))
    }
}
