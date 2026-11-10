package com.mapmory.android

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.mapmory.shared.presentation.group.GroupInvitationInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Read once per install. Transient service errors are retried on the next activity launch. */
internal suspend fun restoreInstallInvitation(context: Context, inbox: GroupInvitationInbox) {
    val preferences = context.getSharedPreferences("group-invitations", Context.MODE_PRIVATE)
    val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    if (preferences.getLong("referrer-read-install-time", -1) == installedAt) return
    val initialPendingUrl = inbox.pendingUrl
    val initialRevision = inbox.revision
    val referrer = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<String?> { continuation ->
                val client = InstallReferrerClient.newBuilder(context).build()
                continuation.invokeOnCancellation { client.endConnection() }
                try {
                    client.startConnection(object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            val value = when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK ->
                                    runCatching { client.installReferrer.installReferrer }.getOrNull()
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> ""
                                else -> null
                            }
                            client.endConnection()
                            if (continuation.isActive) continuation.resume(value)
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            client.endConnection()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                } catch (_: Exception) {
                    client.endConnection()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    } ?: return
    // The landing page encodes invite_token=<token> into Google Play's referrer parameter.
    val token = Uri.parse("https://map-mory.com/?$referrer").getQueryParameter("invite_token")
    // A direct link received during this request always takes precedence over an old install referrer.
    if (initialPendingUrl == null && inbox.pendingUrl == null && inbox.revision == initialRevision && token != null) {
        inbox.receive("https://map-mory.com/invite/$token")
    }
    preferences.edit().putLong("referrer-read-install-time", installedAt).apply()
}
