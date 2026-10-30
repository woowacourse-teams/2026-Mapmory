package com.mapmory.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapmoryBackHandlerRegistryTest {
    @Test
    fun `only the current back stack owner's handler receives system back`() {
        val registry = MapmoryBackHandlerRegistry()
        var hiddenMapWasCalled = false
        var editorWasCalled = false
        registry.register("map") {
            hiddenMapWasCalled = true
            true
        }
        registry.register("editor") {
            editorWasCalled = true
            true
        }

        assertTrue(registry.handleBack("editor"))
        assertTrue(editorWasCalled)
        assertFalse(hiddenMapWasCalled)
    }

    @Test
    fun `missing current owner falls through to navigator`() {
        val registry = MapmoryBackHandlerRegistry()
        registry.register("map") { true }

        assertFalse(registry.handleBack("records"))
    }

    @Test
    fun `gesture back can preserve a draft without invoking destructive button back`() {
        val registry = MapmoryBackHandlerRegistry()
        var buttonBackCalled = false
        registry.register(
            "editor",
            gestureBack = { false }, // Delegate to save-state navigation.
        ) {
            buttonBackCalled = true
            true
        }

        assertFalse(registry.handleGestureBack("editor"))
        assertFalse(buttonBackCalled)
        assertTrue(registry.handleBack("editor"))
        assertTrue(buttonBackCalled)
    }

    @Test
    fun `forward uses only the active screen and unregister removes its callbacks`() {
        val registry = MapmoryBackHandlerRegistry()
        var hiddenForwardCalled = false
        registry.register("hidden", gestureForward = { hiddenForwardCalled = true; true }) { true }
        val active = registry.register("editor", gestureForward = { true }) { true }

        assertTrue(registry.handleGestureForward("editor"))
        assertFalse(hiddenForwardCalled)
        registry.unregister(active)
        assertFalse(registry.handleGestureForward("editor"))
    }

    @Test
    fun `gesture does not invoke an ordinary non reversible back handler`() {
        val registry = MapmoryBackHandlerRegistry()
        var ordinaryBackCalled = false
        registry.register("detail") {
            ordinaryBackCalled = true
            true
        }

        assertFalse(registry.handleGestureBack("detail"))
        assertFalse(ordinaryBackCalled)
        assertFalse(registry.handleGestureForward("detail"))
        assertFalse(registry.handleGestureBack("missing"))
    }
}
