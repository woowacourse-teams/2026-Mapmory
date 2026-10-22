package com.mapmory.shared.presentation.triprecord.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.HttpsURLConnection

@Composable
internal actual fun TripTimelineVideoPreview(video: ShareVideoFile, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val videoView = remember { arrayOfNulls<VideoView>(1) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) videoView[0]?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            videoView[0]?.pause()
        }
    }

    androidx.compose.runtime.key(video.path) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                VideoView(context).apply {
                    val controller = MediaController(context)
                    setMediaController(controller)
                    controller.setAnchorView(this)
                    setOnPreparedListener { controller.show(3_000) }
                    setVideoURI(Uri.fromFile(File(video.path)))
                    videoView[0] = this
                }
            },
            onRelease = { releasedView ->
                releasedView.stopPlayback()
                if (videoView[0] === releasedView) videoView[0] = null
            },
        )
    }
}

@Composable
internal actual fun rememberShareVideoExporter(): ShareVideoExporter {
    val context = LocalContext.current
    val permission = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission[0]?.complete(it)
    }
    return remember(context, launcher) {
        AndroidShareVideoExporter(context) { request ->
            val result = CompletableDeferred<Boolean>()
            permission[0] = result
            try {
                launcher.launch(request)
                result.await()
            } finally {
                permission[0] = null
            }
        }
    }
}

private class AndroidShareVideoExporter(
    private val context: Context,
    private val requestPermission: suspend (String) -> Boolean,
) : ShareVideoExporter {
    override suspend fun download(url: String): ShareVideoFile = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared-videos").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        val file = File(directory, "Mapmory-${UUID.randomUUID()}.mp4")
        val connection = (URL(url).openConnection() as? HttpsURLConnection)
            ?: error("안전한 영상 다운로드 주소가 아니에요.")
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()
            check(connection.url.protocol.equals("https", ignoreCase = true)) {
                "안전한 영상 다운로드 주소가 아니에요."
            }
            check(connection.responseCode in 200..299) { "영상을 다운로드하지 못했어요. 다시 시도해 주세요." }
            connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            check(file.length() > 0L) { "다운로드한 영상이 비어 있어요. 다시 시도해 주세요." }
            ShareVideoFile(file.absolutePath)
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun save(video: ShareVideoFile) {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            check(requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                "동영상 저장 권한을 허용해 주세요."
            }
        }
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Mapmory-${UUID.randomUUID()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Mapmory")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("동영상을 저장할 공간을 만들지 못했어요.")
            try {
                val source = File(video.path)
                check(source.isFile && source.length() > 0L) { "다운로드한 영상을 찾지 못했어요. 다시 받아주세요." }
                val output = resolver.openOutputStream(uri) ?: error("동영상 파일을 열지 못했어요.")
                source.inputStream().use { input -> output.use { target -> input.copyTo(target) } }
                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    override suspend fun share(video: ShareVideoFile) {
        val file = File(video.path)
        check(file.isFile && file.length() > 0L) { "다운로드한 영상을 찾지 못했어요. 다시 받아주세요." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share-images", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "여행 영상 공유"))
        }
    }
}
