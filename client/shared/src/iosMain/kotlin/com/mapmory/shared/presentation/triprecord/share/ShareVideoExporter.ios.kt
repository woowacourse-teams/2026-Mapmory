@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.triprecord.share

import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.rate
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.CoreMedia.CMTimeGetSeconds

import androidx.compose.runtime.LaunchedEffect
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.ShareVideoPreviewAnalytics
import kotlinx.coroutines.delay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation

@Composable
internal actual fun TripTimelineVideoPreview(video: ShareVideoFile, modifier: Modifier) {
    val analytics = LocalMapmoryAnalytics.current
    val previewAnalytics = remember(video.path, analytics) { ShareVideoPreviewAnalytics(analytics) }
    DisposableEffect(previewAnalytics) {
        previewAnalytics.opened()
        onDispose { previewAnalytics.closed() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(video.path) { AVPlayer(uRL = NSURL.fileURLWithPath(video.path)) }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.pause()
        }
    }

    LaunchedEffect(player, previewAnalytics, lifecycle) {
        while (true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val item = player.currentItem
                when (item?.status) {
                    AVPlayerItemStatusReadyToPlay -> previewAnalytics.ready()
                    AVPlayerItemStatusFailed -> previewAnalytics.failed(ShareVideoPlaybackException(item.error?.code ?: 0L))
                }
                val duration = item?.duration?.let(::CMTimeGetSeconds) ?: 0.0
                val position = CMTimeGetSeconds(player.currentTime())
                if (player.rate == 0f && duration.isFinite() && duration > 0 && position > 0 && position >= duration - 0.05) {
                    previewAnalytics.completed()
                } else {
                    previewAnalytics.playbackChanged(player.rate > 0f)
                }
            }
            delay(250)
        }
    }
    key(video.path) {
        UIKitViewController(
            factory = {
                AVPlayerViewController().apply {
                    showsPlaybackControls = true
                    this.player = player
                }
            },
            modifier = modifier,
            onRelease = { controller ->
                controller.player?.pause()
                controller.player = null
            },
        )
    }
}

@Composable
internal actual fun rememberShareVideoExporter(): ShareVideoExporter = remember { IosShareVideoExporter() }

private val iosVideoDownloader by lazy { IosVideoDownloader() }

private class IosShareVideoExporter : ShareVideoExporter {
    override suspend fun download(url: String, jobId: Long): ShareVideoFile {
        val remoteUrl = checkNotNull(NSURL.URLWithString(url)) { "영상 다운로드 주소가 올바르지 않아요." }
        check(remoteUrl.scheme.equals("https", ignoreCase = true)) { "안전한 영상 다운로드 주소가 아니에요." }
        return iosVideoDownloader.download(remoteUrl, jobId)
    }

    override suspend fun save(video: ShareVideoFile) {
        val fileUrl = NSURL.fileURLWithPath(video.path)
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
                throw ShareMediaPermissionDeniedException("동영상 저장 권한이 필요해요. 설정에서 사진 추가를 허용해 주세요.")
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    { PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(fileUrl) },
                    { success, error ->
                        if (continuation.isActive) {
                            if (success) continuation.resume(Unit)
                            else continuation.resumeWithException(
                                IllegalStateException(error?.localizedDescription ?: "동영상을 저장하지 못했어요. 다시 시도해 주세요."),
                            )
                        }
                    },
                )
            }
        }
    }

    override suspend fun share(video: ShareVideoFile) {
        val fileUrl = NSURL.fileURLWithPath(video.path)
        check(NSFileManager.defaultManager.fileExistsAtPath(video.path)) { "다운로드한 영상을 찾지 못했어요. 다시 받아주세요." }
        withContext(Dispatchers.Main) {
            val presenter = checkNotNull(topShareViewController()) { "공유 화면을 열지 못했어요. 다시 시도해 주세요." }
            val activity = UIActivityViewController(listOf(fileUrl), null)
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

private class IosVideoDownloader : NSObject(), NSURLSessionDownloadDelegateProtocol {
    private val waiters = mutableMapOf<String, CancellableContinuation<ShareVideoFile>>()
    private val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration,
        this,
        NSOperationQueue.mainQueue(),
    )

    suspend fun download(url: NSURL, jobId: Long): ShareVideoFile = withContext(Dispatchers.Main) {
        val cache = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: error("영상을 저장할 공간을 찾지 못했어요.")
        val directory = "$cache/shared-videos"
        val manager = NSFileManager.defaultManager
        check(manager.createDirectoryAtPath(directory, true, null, null)) {
            "영상을 저장할 공간을 만들지 못했어요."
        }
        val path = "$directory/Mapmory-$jobId.mp4"
        if (manager.fileExistsAtPath(path)) return@withContext ShareVideoFile(path)
        suspendCancellableCoroutine { continuation ->
            val task = session.downloadTaskWithURL(url)
            val id = NSUUID().UUIDString
            task.taskDescription = "$id:$jobId"
            waiters[id] = continuation
            continuation.invokeOnCancellation {
                waiters.remove(id)
                task.cancel()
            }
            task.resume()
        }
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        val taskDescription = downloadTask.taskDescription ?: return
        val id = taskDescription.substringBefore(':')
        val jobId = taskDescription.substringAfter(':').toLongOrNull() ?: return
        val continuation = waiters.remove(id) ?: return
        val response = downloadTask.response as? NSHTTPURLResponse
        if (response == null || response.statusCode !in 200..299 ||
            !response.URL?.scheme.equals("https", ignoreCase = true)
        ) {
            continuation.resumeWithException(IllegalStateException("영상을 다운로드하지 못했어요. 다시 시도해 주세요."))
            return
        }
        val manager = NSFileManager.defaultManager
        val cache = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: run {
                continuation.resumeWithException(IllegalStateException("영상을 저장할 공간을 찾지 못했어요."))
                return
            }
        val directory = "$cache/shared-videos"
        if (!manager.createDirectoryAtPath(directory, true, null, null)) {
            continuation.resumeWithException(IllegalStateException("영상을 저장할 공간을 만들지 못했어요."))
            return
        }
        val path = "$directory/Mapmory-$jobId.mp4"
        if (!manager.moveItemAtPath(didFinishDownloadingToURL.path ?: "", path, null)) {
            continuation.resumeWithException(IllegalStateException("다운로드한 영상을 저장하지 못했어요."))
            return
        }
        continuation.resume(ShareVideoFile(path))
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: platform.Foundation.NSError?) {
        if (didCompleteWithError == null) return
        val id = task.taskDescription?.substringBefore(':') ?: return
        waiters.remove(id)?.resumeWithException(
            IllegalStateException("인터넷 연결이 끊겨 영상을 다운로드하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요."),
        )
    }
}

private fun topShareViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .filter { it.activationState == UISceneActivationStateForegroundActive }
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
    val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
