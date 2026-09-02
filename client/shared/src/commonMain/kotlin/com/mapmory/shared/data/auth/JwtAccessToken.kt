package com.mapmory.shared.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun String.hasUsableJwtLifetime(
    nowEpochSeconds: Long,
    minimumRemainingSeconds: Long = MinimumRemainingAccessTokenSeconds,
): Boolean {
    val payload = split('.').takeIf { parts -> parts.size == JwtPartCount }?.get(1) ?: return false
    val expiration = runCatching {
        Json.parseToJsonElement(payload.decodeBase64Url().decodeToString())
            .jsonObject[ExpirationClaim]
            ?.jsonPrimitive
            ?.longOrNull
    }.getOrNull() ?: return false
    return expiration > nowEpochSeconds + minimumRemainingSeconds
}

private fun String.decodeBase64Url(): ByteArray {
    val output = ArrayList<Byte>((length * 3) / 4)
    var buffer = 0
    var bitCount = 0
    for (character in this) {
        if (character == '=') break
        val value = Base64UrlAlphabet.indexOf(character)
        require(value >= 0) { "올바르지 않은 Base64 URL 문자입니다." }
        buffer = (buffer shl BitsPerBase64Character) or value
        bitCount += BitsPerBase64Character
        if (bitCount >= BitsPerByte) {
            bitCount -= BitsPerByte
            output += ((buffer shr bitCount) and ByteMask).toByte()
        }
    }
    return output.toByteArray()
}

private const val ExpirationClaim = "exp"
private const val JwtPartCount = 3
private const val MinimumRemainingAccessTokenSeconds = 30L
private const val BitsPerBase64Character = 6
private const val BitsPerByte = 8
private const val ByteMask = 0xFF
private const val Base64UrlAlphabet =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
