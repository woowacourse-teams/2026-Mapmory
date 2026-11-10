package com.mapmory.shared.presentation.group

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupInvitationLinksTest {
    private val url = "https://map-mory.com/invite/long_token-123"

    @Test fun extractsOnlyThePublicInvitationToken() {
        assertEquals("long_token-123", groupInvitationToken(url))
        assertEquals("ABC123", groupInvitationToken("https://map-mory.com/invite/ABC123"))
        listOf(
            "http://map-mory.com/invite/long_token-123",
            "https://map-mory.com.attacker.example/invite/long_token-123",
            "https://attacker@map-mory.com/invite/long_token-123",
            "https://map-mory.com:8443/invite/long_token-123",
            "$url?redirect=other", "$url#fragment", "$url/other",
            "https://map-mory.com/invite/ABC12",
            "https://map-mory.com/invite/a%2Fb%3Fx%23y",
            "https://map-mory.com/invite/",
            "not a link",
        ).forEach { assertNull(groupInvitationToken(it), it) }
    }

    @Test fun restoresAcrossLoginAndDoesNotLetAnOldScreenDismissANewerInvite() {
        var saved: String? = null
        val inbox = GroupInvitationInbox(save = { saved = it })
        assertTrue(inbox.receive(url))
        val restored = GroupInvitationInbox(initialUrl = saved, save = { saved = it })
        assertEquals(url, restored.pendingUrl)
        val newer = "https://map-mory.com/invite/new_token-456"
        assertTrue(restored.receive(newer))
        restored.dismiss(url)
        assertEquals(newer, restored.pendingUrl)
        assertEquals(newer, saved)
        restored.dismiss(newer)
        assertNull(saved)
        assertNull(restored.pendingUrl)
        assertEquals(1, restored.revision)
    }

    @Test fun invalidLinksDoNotOverwritePendingInvitesOrPersist() {
        val inbox = GroupInvitationInbox(initialUrl = url, save = { error("Must not save an invalid link") })
        assertFalse(inbox.receive("https://other.example/invite/token"))
        assertEquals(url, inbox.pendingUrl)
    }
}
