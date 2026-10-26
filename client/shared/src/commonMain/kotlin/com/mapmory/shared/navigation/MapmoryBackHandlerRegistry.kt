package com.mapmory.shared.navigation

internal class MapmoryBackHandlerRegistry {
    private val registrations = mutableListOf<Registration>()
    var isRestoringGesture: Boolean = false
        private set

    fun register(
        ownerId: String,
        gestureBack: (() -> Boolean)? = null,
        gestureForward: (() -> Boolean)? = null,
        handler: () -> Boolean,
    ): Registration {
        val registration = Registration(ownerId, handler, gestureBack, gestureForward)
        registrations += registration
        return registration
    }

    fun unregister(registration: Registration) {
        registrations.remove(registration)
    }

    fun handleBack(ownerId: String?): Boolean {
        return registrations
            .lastOrNull { registration -> registration.ownerId == ownerId }
            ?.handler
            ?.invoke() == true
    }

    fun handleGestureBack(ownerId: String?, restoring: Boolean = false): Boolean = withGestureOrigin(restoring) {
        registrations.lastOrNull { it.ownerId == ownerId }?.gestureBack?.invoke() == true
    }

    fun handleGestureForward(ownerId: String?, restoring: Boolean = false): Boolean = withGestureOrigin(restoring) {
        registrations.lastOrNull { it.ownerId == ownerId }?.gestureForward?.invoke() == true
    }

    private fun withGestureOrigin(restoring: Boolean, block: () -> Boolean): Boolean {
        val previous = isRestoringGesture
        isRestoringGesture = restoring
        return try { block() } finally { isRestoringGesture = previous }
    }

    class Registration internal constructor(
        internal val ownerId: String,
        internal val handler: () -> Boolean,
        internal val gestureBack: (() -> Boolean)?,
        internal val gestureForward: (() -> Boolean)?,
    )
}
