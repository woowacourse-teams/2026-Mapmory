package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.ScrollToTopRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollToTopRegistryTest {
    @Test
    fun `status bar tap targets only the active screen and supports repeated taps`() {
        val registry = ScrollToTopRegistry()
        var listActive = true
        var listScrolls = 0
        var detailScrolls = 0
        registry.register(ScrollToTopRegistry.Target({ listActive }, { listScrolls++ }))
        val detail = ScrollToTopRegistry.Target({ !listActive }, { detailScrolls++ })
        registry.register(detail)
        registry.scrollToTop()
        listActive = false
        repeat(2) { registry.scrollToTop() }
        assertEquals(1, listScrolls)
        assertEquals(2, detailScrolls)
        registry.unregister(detail)
        registry.scrollToTop()
        assertEquals(2, detailScrolls)
        listActive = true
        registry.scrollToTop()
        assertEquals(2, listScrolls)
    }
}
