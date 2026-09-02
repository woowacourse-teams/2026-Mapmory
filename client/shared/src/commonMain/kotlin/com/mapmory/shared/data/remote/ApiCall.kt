package com.mapmory.shared.data.remote

import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> apiCall(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error.toUserFriendlyRemoteFailure())
}

internal class MapmoryConnectionException(
    message: String,
    cause: Throwable,
) : IllegalStateException(message, cause)

internal class MapmoryRemoteRequestException(
    cause: Throwable,
) : IllegalStateException(RemoteRequestFailureMessage, cause)

internal fun Throwable.toUserFriendlyRemoteFailure(): Throwable {
    if (this is MapmoryApiException || this is MapmoryConnectionException) return this

    val causes = generateSequence(this) { error -> error.cause }
    val isNameResolutionFailure = causes.any { error ->
        error::class.simpleName in NameResolutionExceptionNames ||
            error.message.containsAny(NameResolutionMessageFragments)
    }
    if (isNameResolutionFailure) {
        return MapmoryConnectionException(ServerConnectionFailureMessage, this)
    }

    val isTimeout = generateSequence(this) { error -> error.cause }.any { error ->
        error::class.simpleName in TimeoutExceptionNames ||
            error.message.containsAny(TimeoutMessageFragments)
    }
    if (isTimeout) {
        return MapmoryConnectionException(ServerTimeoutMessage, this)
    }

    val isConnectionFailure = generateSequence(this) { error -> error.cause }.any { error ->
        error::class.simpleName in ConnectionExceptionNames ||
            error.message.containsAny(ConnectionMessageFragments)
    }
    val isResponseParsingFailure = generateSequence(this) { error -> error.cause }.any { error ->
        val name = error::class.simpleName.orEmpty()
        name in ResponseParsingExceptionNames || name.endsWith("SerializationException")
    }
    return if (isConnectionFailure) {
        MapmoryConnectionException(ServerConnectionFailureMessage, this)
    } else if (isResponseParsingFailure) {
        MapmoryRemoteRequestException(this)
    } else if (this is IllegalArgumentException || this is MissingAccessTokenException) {
        this
    } else {
        MapmoryRemoteRequestException(this)
    }
}

private fun String?.containsAny(fragments: Set<String>): Boolean {
    val normalized = this?.lowercase() ?: return false
    return fragments.any(normalized::contains)
}

private val NameResolutionExceptionNames = setOf("UnknownHostException", "UnresolvedAddressException")
private val TimeoutExceptionNames = setOf(
    "HttpRequestTimeoutException",
    "ConnectTimeoutException",
    "SocketTimeoutException",
)
private val ConnectionExceptionNames = setOf("ConnectException", "ConnectionRefusedException")
private val ResponseParsingExceptionNames = setOf(
    "JsonConvertException",
    "JsonDecodingException",
    "NoTransformationFoundException",
)
private val NameResolutionMessageFragments = setOf(
    "unable to resolve host",
    "no address associated with hostname",
    "could not resolve host",
    "nodename nor servname provided",
)
private val TimeoutMessageFragments = setOf("timed out", "timeout")
private val ConnectionMessageFragments = setOf(
    "failed to connect",
    "connection refused",
    "network is unreachable",
)

private const val ServerConnectionFailureMessage =
    "서버에 연결할 수 없습니다. 인터넷 연결을 확인한 뒤 잠시 후 다시 시도해 주세요."
private const val ServerTimeoutMessage =
    "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."
private const val RemoteRequestFailureMessage =
    "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
