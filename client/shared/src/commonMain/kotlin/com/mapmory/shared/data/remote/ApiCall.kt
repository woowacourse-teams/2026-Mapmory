package com.mapmory.shared.data.remote

import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> apiCall(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
