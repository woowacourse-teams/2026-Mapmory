package com.mapmory.shared.presentation.triprecord.share

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberShareImageExporter(): ShareImageExporter {
    val context = LocalContext.current
    val permission = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission[0]?.complete(it)
    }
    return remember(context, launcher) {
        object : ShareImageExporter {
            override suspend fun save(image: ImageBitmap) {
                if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    val result = CompletableDeferred<Boolean>()
                    permission[0] = result
                    try {
                        launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        if (!result.await()) throw ShareMediaPermissionDeniedException("사진 저장 권한을 허용해 주세요.")
                    } finally {
                        permission[0] = null
                    }
                }
                withContext(Dispatchers.IO) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Mapmory-${UUID.randomUUID()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mapmory")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("사진을 저장할 공간을 만들지 못했어요.")
                    try {
                        val output = resolver.openOutputStream(uri) ?: error("사진 파일을 열지 못했어요.")
                        output.use {
                            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) {
                                "공유 이미지를 만들지 못했어요."
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 29) {
                            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                        }
                    } catch (error: Throwable) {
                        resolver.delete(uri, null, null)
                        throw error
                    }
                }
            }

            override suspend fun share(image: ImageBitmap) {
                val uri = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "shared-posters").apply { mkdirs() }
                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
                        ?.forEach { it.delete() }
                    val file = File(directory, "Mapmory-${UUID.randomUUID()}.png")
                    try {
                        file.outputStream().use {
                            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) {
                                "공유 이미지를 만들지 못했어요."
                            }
                        }
                        FileProvider.getUriForFile(context, "${context.packageName}.share-images", file)
                    } catch (error: Throwable) {
                        file.delete()
                        throw error
                    }
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Mapmory", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "여행 기록 공유"))
                }
            }
        }
    }
}
