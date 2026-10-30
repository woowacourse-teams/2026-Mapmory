package com.mapmory.shared.navigation

internal class MapmoryBackHandlerRegistry {
    private val registrations = mutableListOf<Registration>()

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

    fun handleGestureBack(ownerId: String?): Boolean {
        val registration = registrations.lastOrNull { it.ownerId == ownerId } ?: return false
        return registration.gestureBack?.invoke() == true
    }

    fun handleGestureForward(ownerId: String?): Boolean =
        registrations.lastOrNull { it.ownerId == ownerId }?.gestureForward?.invoke() == true

    class Registration internal constructor(
        internal val ownerId: String,
        internal val handler: () -> Boolean,
        internal val gestureBack: (() -> Boolean)?,
        internal val gestureForward: (() -> Boolean)?,
    )
}
