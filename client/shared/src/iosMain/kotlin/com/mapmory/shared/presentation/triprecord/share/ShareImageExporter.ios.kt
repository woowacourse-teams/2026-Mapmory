@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun rememberShareImageExporter(): ShareImageExporter = remember { IosShareImageExporter() }

private class IosShareImageExporter : ShareImageExporter {
    override suspend fun save(image: ImageBitmap) {
        val nativeImage = image.toUIImage()
        withContext(Dispatchers.Main) {
            val permission = when (val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)) {
                PHAuthorizationStatusNotDetermined -> suspendCancellableCoroutine<Long> { continuation ->
                    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
                else -> status
            }
            if (permission != PHAuthorizationStatusAuthorized) {
                throw ShareMediaPermissionDeniedException("사진 저장 권한이 필요해요. 설정에서 사진 추가를 허용해 주세요.")
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    { PHAssetChangeRequest.creationRequestForAssetFromImage(nativeImage) },
                    { success, _ ->
                        if (continuation.isActive) {
                            if (success) continuation.resume(Unit)
                            else continuation.resumeWithException(IllegalStateException("사진을 저장하지 못했어요. 다시 시도해 주세요."))
                        }
                    },
                )
            }
        }
    }

    override suspend fun share(image: ImageBitmap) {
        val nativeImage = image.toUIImage()
        withContext(Dispatchers.Main) {
            val presenter = checkNotNull(topShareViewController()) { "공유 화면을 열지 못했어요. 다시 시도해 주세요." }
            val activity = UIActivityViewController(listOf(nativeImage), null)
            activity.popoverPresentationController?.apply {
                sourceView = presenter.view
                sourceRect = presenter.view.bounds
                permittedArrowDirections = 0uL
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                presenter.presentViewController(activity, animated = true) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }
}

private suspend fun ImageBitmap.toUIImage(): UIImage = withContext(Dispatchers.Default) {
    val bitmap = Image.makeFromBitmap(asSkiaBitmap())
    val bytes = try {
        val encoded = checkNotNull(bitmap.encodeToData(EncodedImageFormat.PNG)) { "공유 이미지를 만들지 못했어요." }
        try {
            encoded.bytes
        } finally {
            encoded.close()
        }
    } finally {
        bitmap.close()
    }
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    checkNotNull(UIImage.imageWithData(data)) { "공유 이미지를 만들지 못했어요." }
}

private fun topShareViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val windows = application.connectedScenes.filterIsInstance<UIWindowScene>()
        .filter { it.activationState == UISceneActivationStateForegroundActive }
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
    val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
