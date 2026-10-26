package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createHttpClient(): HttpClient

internal fun HttpClientConfig<*>.configureCommonHttpClient() {
    install(HttpTimeout)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
