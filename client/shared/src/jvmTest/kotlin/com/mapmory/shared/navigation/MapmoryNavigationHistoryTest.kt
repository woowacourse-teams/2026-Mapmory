package com.mapmory.shared.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapmoryNavigationHistoryTest {
    @Test
    fun `rapid map taps keep only the first editor and allow a new location after back`() = onUiThread {
        val fixture = Fixture()
        val origin = fixture.controller.currentBackStackEntry!!.id
        fixture.navigator.navigateToEditor(selectedLocationId = 100)
        fixture.navigator.navigateToEditor(selectedLocationId = 200)
        fixture.navigator.navigateToEditor(selectedLocationId = 100)
        assertEquals(100L, fixture.controller.currentBackStackEntry!!.toRoute<EditorRoute>().selectedLocationId)
        assertTrue(fixture.navigator.navigateBack())
        assertEquals(origin, fixture.controller.currentBackStackEntry!!.id)
        fixture.navigator.navigateToEditor(selectedLocationId = 200)
        assertEquals(200L, fixture.controller.currentBackStackEntry!!.toRoute<EditorRoute>().selectedLocationId)
        fixture.close()
    }

    @Test
    fun `rapid clicks on different records open only the first and allow another after back`() = onUiThread {
        val fixture = Fixture()
        val origin = fixture.controller.currentBackStackEntry!!.id
        fixture.navigator.navigateToDetail(42)
        fixture.navigator.navigateToDetail(43)
        fixture.navigator.navigateToDetail(42)
        assertEquals(42L, fixture.controller.currentBackStackEntry!!.toRoute<DetailRoute>().recordId)
        assertTrue(fixture.navigator.navigateBack())
        assertEquals(origin, fixture.controller.currentBackStackEntry!!.id)
        fixture.navigator.navigateToDetail(43)
        assertEquals(43L, fixture.controller.currentBackStackEntry!!.toRoute<DetailRoute>().recordId)
        fixture.close()
    }

    @Test
    fun `forward restores the same entry and draft ViewModel after back`() = onUiThread {
        val fixture = Fixture()
        fixture.navigator.navigateToEditor()
        val editor = fixture.controller.currentBackStackEntry!!
        fixture.compose.onTransitionComplete(editor)
        val draft = Draft().apply { text = "작성 중인 여행 기록" }
        editor.viewModelStore.put("draft", draft)

        assertTrue(fixture.navigator.navigateBackKeepingState())
        fixture.compose.onTransitionComplete(editor)
        assertFalse(draft.cleared)
        assertTrue(fixture.navigator.navigateForward())
        val restored = fixture.controller.currentBackStackEntry!!
        assertEquals(editor.id, restored.id)
        assertSame(draft, restored.viewModelStore["draft"])
        assertEquals("작성 중인 여행 기록", draft.text)
        fixture.close()
    }

    @Test
    fun `multiple saved screens restore in forward order`() = onUiThread {
        val fixture = Fixture()
        fixture.navigator.navigateToDetail(42)
        val detailId = fixture.controller.currentBackStackEntry!!.id
        fixture.navigator.navigateToEditor(recordId = 42)
        val editorId = fixture.controller.currentBackStackEntry!!.id
        assertTrue(fixture.navigator.navigateBackKeepingState())
        assertTrue(fixture.navigator.navigateBackKeepingState())
        assertTrue(fixture.navigator.navigateForward())
        assertEquals(detailId, fixture.controller.currentBackStackEntry!!.id)
        assertTrue(fixture.navigator.navigateForward())
        assertEquals(editorId, fixture.controller.currentBackStackEntry!!.id)
        assertFalse(fixture.navigator.navigateForward())
        fixture.close()
    }

    @Test
    fun `new navigation replaces forward history instead of reopening an old draft`() = onUiThread {
        val fixture = Fixture()
        fixture.navigator.navigateToEditor()
        assertTrue(fixture.navigator.navigateBackKeepingState())
        fixture.navigator.navigateToDetail(42)
        assertFalse(fixture.navigator.navigateForward())
        fixture.close()
    }

    @Test
    fun `cancelled back repeatedly restores entry saved UI state and draft`() = onUiThread {
        val fixture = Fixture()
        fixture.navigator.navigateToEditor()
        val editor = fixture.controller.currentBackStackEntry!!
        fixture.compose.onTransitionComplete(editor)
        val draft = Draft().apply { text = "취소해도 유지할 초안" }
        editor.viewModelStore.put("draft", draft)
        editor.savedStateHandle["scrollOffset"] = 240

        repeat(3) {
            val departing = fixture.controller.currentBackStackEntry!!
            assertTrue(fixture.navigator.navigateBackKeepingState())
            fixture.compose.onTransitionComplete(departing)
            // Same inverse operation used by onBackCancelled.
            assertTrue(fixture.navigator.navigateForward())
            val restored = fixture.controller.currentBackStackEntry!!
            fixture.compose.onTransitionComplete(restored)
            assertEquals(editor.id, restored.id)
            assertSame(draft, restored.viewModelStore["draft"])
            assertEquals(240, restored.savedStateHandle.get<Int>("scrollOffset"))
            assertEquals("취소해도 유지할 초안", draft.text)
            assertFalse(draft.cleared)
            assertFalse(fixture.navigator.navigateForward())
        }
        fixture.close()
    }

    @Test
    fun `cancelled forward preserves remaining forward history and draft`() = onUiThread {
        val fixture = Fixture()
        fixture.navigator.navigateToDetail(42)
        val detail = fixture.controller.currentBackStackEntry!!
        fixture.navigator.navigateToEditor(recordId = 42)
        val editor = fixture.controller.currentBackStackEntry!!
        val draft = Draft().apply { text = "이어서 작성" }
        editor.viewModelStore.put("draft", draft)
        assertTrue(fixture.navigator.navigateBackKeepingState())
        fixture.compose.onTransitionComplete(editor)
        assertTrue(fixture.navigator.navigateBackKeepingState())
        fixture.compose.onTransitionComplete(detail)
        val originId = fixture.controller.currentBackStackEntry!!.id

        repeat(3) {
            assertTrue(fixture.navigator.navigateForward())
            val departing = fixture.controller.currentBackStackEntry!!
            fixture.compose.onTransitionComplete(departing)
            assertEquals(detail.id, departing.id)
            // Same inverse operation used by onForwardCancelled.
            assertTrue(fixture.navigator.navigateBackKeepingState())
            fixture.compose.onTransitionComplete(departing)
            assertEquals(originId, fixture.controller.currentBackStackEntry!!.id)
        }
        assertTrue(fixture.navigator.navigateForward())
        assertEquals(detail.id, fixture.controller.currentBackStackEntry!!.id)
        assertTrue(fixture.navigator.navigateForward())
        val restored = fixture.controller.currentBackStackEntry!!
        assertEquals(editor.id, restored.id)
        assertSame(draft, restored.viewModelStore["draft"])
        assertFalse(draft.cleared)
        assertFalse(fixture.navigator.navigateForward())
        fixture.close()
    }

    private class Draft : ViewModel() {
        var text = ""
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private class Fixture {
        val store = ViewModelStore()
        val compose = ComposeNavigator()
        val controller = NavHostController().apply {
            navigatorProvider.addNavigator(compose)
            setViewModelStore(store)
            graph = createGraph(startDestination = MapRoute) {
                composable<MapRoute> {}
                composable<EditorRoute> {}
                composable<DetailRoute> {}
            }
        }
        val navigator = MapmoryNavigator(controller)
        fun close() = store.clear()
    }

    private fun onUiThread(block: () -> Unit) {
        SwingUtilities.invokeAndWait(block)
    }
}
