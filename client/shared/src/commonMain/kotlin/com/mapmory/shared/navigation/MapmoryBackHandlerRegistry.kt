package com.mapmory.shared.navigation

internal class MapmoryBackHandlerRegistry {
    private var currentRegistration: Registration? = null

    fun register(handler: () -> Boolean): Registration {
        val registration = Registration(handler)
        currentRegistration = registration
        return registration
    }

    fun unregister(registration: Registration) {
        if (currentRegistration === registration) {
            currentRegistration = null
        }
    }

    fun handleBack(): Boolean = currentRegistration?.handler?.invoke() == true

    class Registration internal constructor(
        internal val handler: () -> Boolean,
    )
}
