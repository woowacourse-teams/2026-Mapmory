package com.mapmory.shared.presentation.privacy

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PrivacyPolicyContent(
    url: String,
    modifier: Modifier,
) {
    val currentUrl = rememberUpdatedState(url)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                loadUrl(currentUrl.value)
            }
        },
        update = { webView ->
            if (webView.url != currentUrl.value) webView.loadUrl(currentUrl.value)
        },
    )
}
