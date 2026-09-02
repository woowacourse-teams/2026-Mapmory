package com.mapmory.shared.logging

import android.util.Log

internal actual fun mapmoryDebugLog(tag: String, message: String) {
    runCatching { Log.d(tag, message) }
        .onFailure { println("D/$tag: $message") }
}
