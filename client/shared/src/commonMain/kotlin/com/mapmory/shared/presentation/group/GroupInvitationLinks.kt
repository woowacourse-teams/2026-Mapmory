package com.mapmory.shared.presentation.group

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Accept only the public invitation route. Never forward arbitrary URLs to the API. */
fun groupInvitationToken(url: String): String? {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (parsed.protocol != URLProtocol.HTTPS || parsed.host != "map-mory.com" || parsed.port != 443 ||
        !parsed.user.isNullOrEmpty() || !parsed.password.isNullOrEmpty() || parsed.encodedQuery.isNotEmpty() || parsed.fragment.isNotEmpty()
    ) return null
    val token = parsed.encodedPath.removePrefix("/invite/")
    if (parsed.encodedPath != "/invite/$token" || token.length !in 6..512 ||
        token.any { !it.isLetterOrDigit() && it != '-' && it != '_' } || token.any { it.code > 127 }
    ) return null
    return token
}

/** Owned by the platform entry point so login and navigation resets preserve the invitation. */
class GroupInvitationInbox(
    initialUrl: String? = null,
    private val save: (String?) -> Unit = {},
) {
    var pendingUrl by mutableStateOf(initialUrl?.takeIf { groupInvitationToken(it) != null })
        private set
    var revision by mutableLongStateOf(0L)
        private set

    fun receive(url: String): Boolean {
        if (groupInvitationToken(url) == null) return false
        save(url)
        revision++
        pendingUrl = url
        return true
    }

    fun dismiss(url: String) {
        if (pendingUrl != url) return
        save(null)
        pendingUrl = null
    }
}

internal val LocalGroupInvitationInbox = staticCompositionLocalOf<GroupInvitationInbox?> { null }
