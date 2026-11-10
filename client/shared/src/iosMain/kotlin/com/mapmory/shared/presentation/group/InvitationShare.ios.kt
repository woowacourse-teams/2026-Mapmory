@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.*

@Composable
internal actual fun rememberInvitationShare(): (String) -> Unit = remember {
    { text ->
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>().firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        val windows = scene?.windows?.filterIsInstance<UIWindow>().orEmpty()
        var controller = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        val presenter = controller
        if (presenter != null) {
            val share = UIActivityViewController(listOf(text), null)
            share.popoverPresentationController?.sourceView = presenter.view
            presenter.presentViewController(share, animated = true, completion = null)
        }
    }
}
