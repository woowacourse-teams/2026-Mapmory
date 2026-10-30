package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient

internal actual fun createPhotoUploadTransfer(client: HttpClient, namespace: String): PhotoUploadTransfer = HttpPhotoUploadTransfer(client)
