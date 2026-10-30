package com.mapmory.shared.data.auth

import io.ktor.http.Url

/** 서로 다른 서버의 자격 증명을 재사용하지 않는 영속 저장소 이름. */
fun authTokenStorageKey(apiBaseUrl: String): String {
    val url = Url(apiBaseUrl)
    val canonical = "${url.protocol.name}://${url.host}:${url.port}${url.encodedPath.trimEnd('/')}"
    if (canonical == "https://api.map-mory.com:443/api/v1") return "production"
    return canonical.encodeToByteArray().joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
}

fun isDevelopmentAuthEnvironment(apiBaseUrl: String): Boolean =
    Url(apiBaseUrl).let {
        it.protocol.name == "https" && it.host == "dev-api.map-mory.com" &&
            it.port == 443 && it.encodedPath.trimEnd('/') == "/api/v1"
    }
