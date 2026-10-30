package com.mapmory.shared.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.darwin.NSObject
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
import platform.UIKit.UIScrollViewDelegateProtocol

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformStatusBarTap(onTap: () -> Unit) {
    val controller = LocalUIViewController.current
    val latestOnTap by rememberUpdatedState(onTap)
    // UIScrollView holds its delegate weakly; keep it alive for the composition lifetime.
    val delegate = remember(controller) {
        object : NSObject(), UIScrollViewDelegateProtocol {
            override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
                latestOnTap()
                return false // Keep the receiver offset so subsequent status-bar taps also work.
            }
        }
    }
    DisposableEffect(controller, delegate) {
        // Compose lists are not UIScrollViews. One native receiver forwards the system tap.
        val receiver = UIScrollView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)).apply {
            contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever
            setContentSize(CGSizeMake(1.0, 2.0))
            setContentOffset(CGPointMake(0.0, 1.0), animated = false)
            scrollsToTop = true
            showsVerticalScrollIndicator = false
            showsHorizontalScrollIndicator = false
            panGestureRecognizer.setEnabled(false)
            this.delegate = delegate
        }
        controller.view.insertSubview(receiver, atIndex = 0)
        onDispose {
            receiver.delegate = null
            receiver.removeFromSuperview()
        }
    }
}
