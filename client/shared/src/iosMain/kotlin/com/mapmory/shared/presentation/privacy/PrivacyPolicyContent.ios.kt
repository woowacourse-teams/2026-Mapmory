@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@Composable
actual fun PrivacyPolicyContent(
    url: String,
    modifier: Modifier,
) {
    val currentUrl = rememberUpdatedState(url)
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView().apply {
                NSURL.URLWithString(currentUrl.value)?.let { loadRequest(NSURLRequest(it)) }
            }
        },
        update = { webView ->
            if (webView.URL?.absoluteString != currentUrl.value) {
                NSURL.URLWithString(currentUrl.value)?.let { webView.loadRequest(NSURLRequest(it)) }
            }
        },
    )
}
