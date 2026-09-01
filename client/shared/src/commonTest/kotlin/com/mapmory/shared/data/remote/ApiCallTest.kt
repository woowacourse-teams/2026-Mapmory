package com.mapmory.shared.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ApiCallTest {
    @Test
    fun cancellationExceptionIsRethrown() = runBlocking {
        val cancellation = CancellationException("요청 취소")

        val thrown = assertFailsWith<CancellationException> {
            apiCall<Unit> { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun regularExceptionIsReturnedAsFailure() = runBlocking {
        val result = apiCall<Unit> { throw IllegalStateException("요청 실패") }

        assertIs<IllegalStateException>(result.exceptionOrNull())
        Unit
    }
}
