package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent

internal interface PhotoUploadTransfer {
    suspend fun completedObjectKey(localId: String): String? = null
    suspend fun put(source: PhotoUploadSource, url: String, objectKey: String, contentType: ContentType): String
    suspend fun forget(localIds: List<String>) {}
}

internal class HttpPhotoUploadTransfer(private val client: HttpClient) : PhotoUploadTransfer {
    override suspend fun put(source: PhotoUploadSource, url: String, objectKey: String, contentType: ContentType): String {
        client.put(url) { setBody(ByteArrayContent(source.bytes, contentType)) }.requireSuccess()
        return objectKey
    }
}

internal expect fun createPhotoUploadTransfer(client: HttpClient, namespace: String): PhotoUploadTransfer
