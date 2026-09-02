package com.mapmory.shared.logging

internal actual fun mapmoryDebugLog(tag: String, message: String) {
    println("D/$tag: $message")
}
