package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ProblemFieldErrorDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

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
        val rawError = IllegalStateException("internal client state")

        val result = apiCall<Unit> { throw rawError }.exceptionOrNull()

        assertIs<MapmoryRemoteRequestException>(result)
        assertEquals("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", result.message)
        assertSame(rawError, result.cause)
    }

    @Test
    fun `DNS 오류를 사용자 친화적인 연결 안내로 변환한다`() = runBlocking {
        val rawError = IllegalStateException(
            "Unable to resolve host \"api.map-mory.com\": No address associated with hostname",
        )

        val result = apiCall<Unit> { throw rawError }.exceptionOrNull()

        assertIs<MapmoryConnectionException>(result)
        assertEquals(
            "서버에 연결할 수 없습니다. 인터넷 연결을 확인한 뒤 잠시 후 다시 시도해 주세요.",
            result.message,
        )
        assertSame(rawError, result.cause)
    }

    @Test
    fun `시간 초과 오류를 재시도 안내로 변환한다`() {
        val rawError = IllegalStateException("request timed out")

        val result = rawError.toUserFriendlyRemoteFailure()

        assertIs<MapmoryConnectionException>(result)
        assertEquals(
            "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.",
            result.message,
        )
    }

    @Test
    fun `서버가 제공한 API 오류 문구는 유지한다`() {
        val apiError = MapmoryApiException(
            statusCode = 400,
            code = "VALIDATION_ERROR",
            title = "요청 값이 올바르지 않습니다.",
            detail = "제목을 확인해 주세요.",
            instance = "/api/v1/travel-records",
            errors = listOf(ProblemFieldErrorDto("title", "제목을 확인해 주세요.")),
        )

        assertSame(apiError, apiError.toUserFriendlyRemoteFailure())
    }

    @Test
    fun `사용자에게 안내할 입력 오류는 문구를 유지한다`() {
        val error = IllegalArgumentException("여행 기록을 찾을 수 없습니다.")

        assertSame(error, error.toUserFriendlyRemoteFailure())
    }

    @Test
    fun `서버 응답 파싱 오류는 내부 내용을 숨기고 재시도를 안내한다`() {
        val rawError = SerializationException("Unexpected JSON token at offset 42")

        val result = rawError.toUserFriendlyRemoteFailure()

        assertIs<MapmoryRemoteRequestException>(result)
        assertEquals("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", result.message)
        assertSame(rawError, result.cause)
    }

    @Test
    fun `사진 식별자 오류는 사진 재선택을 안내한다`() {
        val error = MapmoryApiException(
            statusCode = 400,
            code = "INVALID_OBJECT_KEY",
            title = "Object Key가 올바르지 않습니다.",
            detail = "다른 기록에서 사용 중인 Object Key입니다.",
            instance = "/api/v1/travel-records",
            errors = emptyList(),
        )

        assertEquals(
            "사진을 저장하지 못했습니다. 잠시 후 다시 저장해 주세요.",
            error.message,
        )
    }

    @Test
    fun `인증 토큰 오류는 재로그인을 안내한다`() {
        val error = MapmoryApiException(
            statusCode = 401,
            code = "EXPIRED_REFRESH_TOKEN",
            title = "만료된 refresh 토큰입니다.",
            detail = "refresh 토큰이 만료되었습니다.",
            instance = "/api/v1/auth/token/refresh",
            errors = emptyList(),
        )

        assertEquals("로그인 정보가 만료되었습니다. 다시 로그인해 주세요.", error.message)
    }
}
