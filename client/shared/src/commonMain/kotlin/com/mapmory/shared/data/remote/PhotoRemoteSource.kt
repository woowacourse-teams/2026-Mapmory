package com.mapmory.shared.data.remote

import com.mapmory.shared.data.media.PhotoRemoteSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** 서버가 내려준 조회용 Presigned GET URL로 S3에서 사진 원본을 읽는다. */
internal class PresignedPhotoRemoteSource(
    private val client: HttpClient,
) : PhotoRemoteSource {
    override suspend fun download(url: String): Result<ByteArray> = apiCall {
        require(url.startsWith(HttpsPrefix)) {
            "사진을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
        client.get(url).requireSuccess().body()
    }
}

private const val HttpsPrefix = "https://"
