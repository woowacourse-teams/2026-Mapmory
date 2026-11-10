package com.mapmory.shared

/** Allows a platform entry point to delegate system back presses to the shared NavController. */
class MapmoryNavigation(
    val invitations: com.mapmory.shared.presentation.group.GroupInvitationInbox = com.mapmory.shared.presentation.group.GroupInvitationInbox(),
) {
    private var backHandler: (() -> Boolean)? = null

    internal fun bindBackHandler(handler: () -> Boolean) {
        backHandler = handler
    }

    internal fun unbindBackHandler() {
        backHandler = null
    }

    fun popBackStack(): Boolean = backHandler?.invoke() == true
}
