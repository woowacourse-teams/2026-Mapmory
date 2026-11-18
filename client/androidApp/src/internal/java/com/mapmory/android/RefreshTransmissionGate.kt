package com.mapmory.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot pause before a real refresh is sent; no response or transport failure is fabricated. */
internal class RefreshTransmissionGate(private val timeoutMillis: Long = 90_000) {
    enum class State { IDLE, ARMED, PAUSED }
    enum class Result { NOT_ARMED, RESUMED, CANCELLED, TIMED_OUT }

    private class Ticket {
        var claimed = false
        val release = CompletableDeferred<Boolean>()
    }

    private val lock = Any()
    private var ticket: Ticket? = null
    private val mutableState = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = mutableState

    fun arm(): Boolean = synchronized(lock) {
        if (ticket != null) return@synchronized false
        ticket = Ticket()
        mutableState.value = State.ARMED
        true
    }

    fun resume() = synchronized(lock) {
        ticket?.takeIf { it.claimed }?.release?.complete(true) ?: false
    }

    fun cancel() = synchronized(lock) {
        val current = ticket ?: return@synchronized false
        current.release.complete(false)
        if (!current.claimed) {
            ticket = null
            mutableState.value = State.IDLE
        }
        true
    }

    suspend fun awaitIfArmed(onPaused: () -> Unit): Result {
        val current = synchronized(lock) {
            ticket?.takeUnless { it.claimed }?.also {
                it.claimed = true
                mutableState.value = State.PAUSED
            }
        } ?: return Result.NOT_ARMED
        try {
            onPaused()
            return when (withTimeoutOrNull(timeoutMillis) { current.release.await() }) {
                true -> Result.RESUMED
                false -> Result.CANCELLED
                null -> Result.TIMED_OUT
            }
        } finally {
            synchronized(lock) {
                if (ticket === current) {
                    ticket = null
                    mutableState.value = State.IDLE
                }
            }
        }
    }
}
