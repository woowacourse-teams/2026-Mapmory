package com.mapmory.shared.presentation.photo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.mapmory.shared.data.local.photo.PhotoMetadataDatabase
import com.mapmory.shared.domain.model.Location
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import com.mapmory.shared.data.local.photo.PhotoMetadataEntity

@Composable
actual fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
): PhotoLibraryActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestPicked by rememberUpdatedState(onPhotosPicked)
    val latestRecommended by rememberUpdatedState(onPhotosRecommended)
    val latestMessage by rememberUpdatedState(onMessage)
    val latestLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val latestLoadingProgressChanged by rememberUpdatedState(onLoadingProgressChanged)
    val latestRecommendationLoadingChanged by rememberUpdatedState(onRecommendationLoadingChanged)
    var pendingRecommendation by remember { mutableStateOf<Pair<Location, String?>?>(null) }
    val recommendationJob = remember { mutableStateOf<Job?>(null) }
    val recommendationGeneration = remember { mutableStateOf(0) }
    val recommendationSession = remember { mutableStateOf<AndroidRecommendationSession?>(null) }

    fun cancelRecommendations() {
        pendingRecommendation = null
        recommendationGeneration.value += 1
        recommendationJob.value?.cancel()
        recommendationJob.value = null
        recommendationSession.value = null
        latestRecommendationLoadingChanged(false)
        latestLoadingChanged(false)
    }

    fun loadRecommendations(target: Location, parentName: String?) {
        recommendationJob.value?.cancel()
        val generation = recommendationGeneration.value + 1
        recommendationGeneration.value = generation
        recommendationSession.value = null
        latestRecommendationLoadingChanged(true)
        latestLoadingChanged(true)
        recommendationJob.value = scope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    context.prepareRecommendationSession(target, parentName, generation) { progress ->
                        latestLoadingProgressChanged(progress)
                    }
                }
                if (generation != recommendationGeneration.value) return@launch
                if (session == null) {
                    latestRecommended(PhotoRecommendationPage(generation, emptyList(), hasMore = false))
                    return@launch
                }
                recommendationSession.value = session
                val page = withContext(Dispatchers.IO) {
                    context.loadRecommendationPage(session)
                }
                if (generation == recommendationGeneration.value) {
                    recommendationSession.value = session.copy(nextIndex = page.nextIndex)
                    latestRecommended(page.asPublicPage())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == recommendationGeneration.value) {
                    latestMessage("사진 추천을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            } finally {
                if (generation == recommendationGeneration.value) {
                    latestRecommendationLoadingChanged(false)
                    latestLoadingChanged(false)
                    recommendationJob.value = null
                }
            }
        }
    }

    fun loadNextRecommendationPage() {
        val session = recommendationSession.value ?: return
        if (!session.hasMore || recommendationJob.value?.isActive == true) return
        val generation = session.generation
        latestRecommendationLoadingChanged(true)
        latestLoadingChanged(true)
        recommendationJob.value = scope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    context.loadRecommendationPage(session)
                }
                if (
                    generation == recommendationGeneration.value &&
                    recommendationSession.value?.generation == generation
                ) {
                    recommendationSession.value = session.copy(nextIndex = page.nextIndex)
                    latestRecommended(page.asPublicPage())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == recommendationGeneration.value) {
                    latestMessage("사진 추천을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            } finally {
                if (generation == recommendationGeneration.value) {
                    latestRecommendationLoadingChanged(false)
                    latestLoadingChanged(false)
                    recommendationJob.value = null
                }
            }
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val hasGalleryAccess = context.canReadGallery()
        val canRecommend = context.canRecommendPhotos()
        val target = pendingRecommendation
        pendingRecommendation = null
        when {
            target == null -> Unit
            !context.hasFullGalleryAccess() && hasGalleryAccess -> {
                latestMessage(FullGalleryAccessMessage)
            }
            canRecommend -> loadRecommendations(target.first, target.second)
            else -> latestMessage("장소 기반 추천을 사용하려면 사진 접근을 허용해 주세요.")
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) {
            latestLoadingChanged(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
            val photos = withContext(Dispatchers.IO) {
                Trace.beginAsyncSection("photo.pick.total", TraceCookie.Pick)
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val result = traceSection("photo.pick.read") {
                        uris.mapNotNull { uri -> context.readPhoto(uri) }
                    }
                    logPhotoPickPerformance(
                        totalMillis = SystemClock.elapsedRealtime() - startedAt,
                        requestedPhotoCount = uris.size,
                        loadedPhotoCount = result.size,
                    )
                    result
                } finally {
                    Trace.endAsyncSection("photo.pick.total", TraceCookie.Pick)
                }
            }
                if (photos.isEmpty()) {
                    latestMessage("선택한 사진을 읽지 못했어요.")
                } else {
                    latestPicked(photos)
                }
            } finally {
                latestLoadingChanged(false)
            }
        }
    }

    return remember(context, galleryPicker, galleryPermissionLauncher) {
        PhotoLibraryActions(
            pickFromGallery = {
                latestLoadingChanged(true)
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            loadNextRecommendationPage = ::loadNextRecommendationPage,
            prepareForAdding = { photos, onReady ->
                scope.launch {
                    val preparedPhotos = withContext(Dispatchers.IO) {
                        photos.mapNotNull { photo ->
                            val originalBytes = photo.originalBytes
                                ?: context.readOriginalBytes(Uri.parse(photo.id))
                            originalBytes?.let { photo.copy(originalBytes = it) }
                        }
                    }
                    onReady(preparedPhotos)
                }
            },
            recommendForLocation = { location, parentName ->
                if (!context.hasFullGalleryAccess() && context.canReadGallery()) {
                    latestMessage(FullGalleryAccessMessage)
                } else if (context.canRecommendPhotos()) {
                    loadRecommendations(location, parentName)
                } else {
                    pendingRecommendation = location to parentName
                    galleryPermissionLauncher.launch(requiredRecommendationPermissions())
                }
            },
            cancelRecommendation = ::cancelRecommendations,
        )
    }
}

private fun Context.canReadGallery(): Boolean {
    val permissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return permissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Context.hasFullGalleryAccess(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED

    else -> ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.canRecommendPhotos(): Boolean =
    canReadGallery() && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        )

private fun requiredRecommendationPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}.toTypedArray()

@Suppress("UNUSED_PARAMETER")
private suspend fun Context.prepareRecommendationSession(
    target: Location,
    parentName: String?,
    generation: Int,
    onProgress: (PhotoLoadingProgress) -> Unit,
): AndroidRecommendationSession? {
    val totalStartedAt = SystemClock.elapsedRealtime()
    val boundaryStartedAt = SystemClock.elapsedRealtime()
    val region = traceSuspendSection(
        name = "photo.recommend.boundary_load",
        cookie = TraceCookie.BoundaryLoad,
    ) {
        target.photoRecommendationRegion()
    } ?: return null
    val boundaryLoadMillis = SystemClock.elapsedRealtime() - boundaryStartedAt
    val syncStartedAt = SystemClock.elapsedRealtime()
    val syncResult = syncPhotoMetadata(onProgress)
    val syncMillis = SystemClock.elapsedRealtime() - syncStartedAt
    val regionFilterStartedAt = SystemClock.elapsedRealtime()
    val matchedPhotos = traceSection("photo.recommend.region_filter") {
        val candidates = syncResult.photos
            .asSequence()
            .sortedByDescending { photo -> photo.capturedAtMillis ?: 0L }
            .mapNotNull { photo ->
                val latitude = photo.latitude ?: return@mapNotNull null
                val longitude = photo.longitude ?: return@mapNotNull null
                LocatedPhoto(photo, latitude, longitude)
            }
        selectPhotosInRegion(candidates, region)
    }
    val regionFilterMillis = SystemClock.elapsedRealtime() - regionFilterStartedAt
    return AndroidRecommendationSession(
        generation = generation,
        candidates = matchedPhotos,
        syncResult = syncResult,
        boundaryLoadMillis = boundaryLoadMillis,
        syncMillis = syncMillis,
        regionFilterMillis = regionFilterMillis,
        discoveryMillis = SystemClock.elapsedRealtime() - totalStartedAt,
    )
}

private suspend fun Context.loadRecommendationPage(
    session: AndroidRecommendationSession,
): RecommendationPreviewPage {
    val startIndex = session.nextIndex
    val endIndex = (startIndex + PhotoRecommendationPageSize).coerceAtMost(session.candidates.size)
    val previewStartedAt = SystemClock.elapsedRealtime()
    val result = traceSection("photo.recommend.preview") {
        session.candidates.subList(startIndex, endIndex).mapNotNull { photo ->
            coroutineContext.ensureActive()
            readPhoto(
                uri = Uri.parse(photo.contentUri),
                knownName = photo.displayName,
                knownCoordinates = requireNotNull(photo.latitude) to requireNotNull(photo.longitude),
                knownCapturedAtMillis = photo.capturedAtMillis,
                includeOriginalBytes = false,
            )
        }
    }
    val previewMillis = SystemClock.elapsedRealtime() - previewStartedAt
    logPerformance(
        totalMillis = session.discoveryMillis + previewMillis,
        boundaryLoadMillis = session.boundaryLoadMillis,
        syncMillis = session.syncMillis,
        regionFilterMillis = session.regionFilterMillis,
        previewMillis = previewMillis,
        syncResult = session.syncResult,
        recommendedPhotoCount = result.size,
    )
    return RecommendationPreviewPage(
        generation = session.generation,
        photos = result,
        nextIndex = endIndex,
        hasMore = endIndex < session.candidates.size,
    )
}

private suspend fun Context.syncPhotoMetadata(
    onProgress: (PhotoLoadingProgress) -> Unit,
): PhotoMetadataSyncResult {
    val dao = PhotoMetadataDatabase.getInstance(this).photoMetadataDao()
    return PhotoMetadataSync(
        readPrevious = {
            traceSuspendSection("photo.sync.room.read", TraceCookie.RoomRead) {
                dao.getAll()
            }
        },
        readCurrent = { queryPhotoMetadataSnapshot() },
        readCoordinates = { contentUri -> readCoordinates(Uri.parse(contentUri)) },
        writeSnapshot = { photos, scanId ->
            traceSuspendSection("photo.sync.room.write", TraceCookie.RoomWrite) {
                dao.replaceSnapshot(photos, scanId)
            }
        },
    ).sync { processed, total ->
        onProgress(PhotoLoadingProgress(processed, total))
    }
}

internal fun Context.queryPhotoMetadataSnapshot(): List<PhotoMetadataCandidate>? {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
    )
    return traceSection("photo.sync.mediastore") {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList {
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId,
                    )
                    add(
                        PhotoMetadataCandidate(
                            mediaId = mediaId,
                            contentUri = uri.toString(),
                            displayName = cursor.getStringOrNull(MediaStore.Images.Media.DISPLAY_NAME)
                                ?: "여행 사진",
                            capturedAtMillis = cursor.getLongOrNull(MediaStore.Images.Media.DATE_TAKEN)
                                ?.takeIf { it > 0L },
                            modifiedAtSeconds = cursor.getLongOrNull(MediaStore.Images.Media.DATE_MODIFIED) ?: 0L,
                            mimeType = cursor.getStringOrNull(MediaStore.Images.Media.MIME_TYPE),
                            sizeBytes = cursor.getLongOrNull(MediaStore.Images.Media.SIZE) ?: 0L,
                            width = cursor.getIntOrNull(MediaStore.Images.Media.WIDTH) ?: 0,
                            height = cursor.getIntOrNull(MediaStore.Images.Media.HEIGHT) ?: 0,
                        ),
                    )
                }
            }
        }
    }
}

private inline fun <T> traceSection(name: String, block: () -> T): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

private suspend inline fun <T> traceSuspendSection(
    name: String,
    cookie: Int,
    crossinline block: suspend () -> T,
): T {
    Trace.beginAsyncSection(name, cookie)
    return try {
        block()
    } finally {
        Trace.endAsyncSection(name, cookie)
    }
}

private fun logPerformance(
    totalMillis: Long,
    boundaryLoadMillis: Long,
    syncMillis: Long,
    regionFilterMillis: Long,
    previewMillis: Long,
    syncResult: PhotoMetadataSyncResult,
    recommendedPhotoCount: Int,
) {
    if (!Log.isLoggable(PhotoPerformanceTag, Log.DEBUG)) return
    Log.d(
        PhotoPerformanceTag,
        "recommend_total_ms=$totalMillis " +
            "boundary_load_ms=$boundaryLoadMillis " +
            "metadata_sync_ms=$syncMillis " +
            "region_filter_ms=$regionFilterMillis " +
            "preview_ms=$previewMillis " +
            "previous_photos=${syncResult.previousPhotoCount} " +
            "media_store_photos=${syncResult.photos.size} " +
            "exif_reads=${syncResult.exifReadCount} " +
            "reused_coordinates=${syncResult.reusedCoordinateCount} " +
            "recommended_photos=$recommendedPhotoCount",
    )
}

private fun logPhotoPickPerformance(
    totalMillis: Long,
    requestedPhotoCount: Int,
    loadedPhotoCount: Int,
) {
    if (!Log.isLoggable(PhotoPerformanceTag, Log.DEBUG)) return
    Log.d(
        PhotoPerformanceTag,
        "pick_total_ms=$totalMillis " +
            "requested_photos=$requestedPhotoCount " +
            "loaded_photos=$loadedPhotoCount",
    )
}

internal fun Context.readPhoto(
    uri: Uri,
    knownName: String? = null,
    knownCoordinates: Pair<Double, Double>? = null,
    knownCapturedAtMillis: Long? = null,
    includeOriginalBytes: Boolean = true,
): SelectedPhoto? = runCatching {
    val metadata = traceSection("photo.read.metadata") {
        if (knownName == null && knownCapturedAtMillis == null) {
            queryPhotoMetadata(uri)
        } else {
            null to null
        }
    }
    val coordinates = knownCoordinates ?: traceSection("photo.read.exif") {
        readCoordinates(uri)
    }
    val encodedBytes = traceSection("photo.read.original") {
        contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
    }?.takeIf(ByteArray::isNotEmpty) ?: return null
    val displayOrientedBytes = traceSection("photo.read.orientation") {
        encodedBytes.normalizeOrientation()
    }
    val previewBytes = traceSection("photo.read.preview") {
        displayOrientedBytes.toPreviewByteArray() ?: displayOrientedBytes
    }
    SelectedPhoto(
        id = uri.toString(),
        displayName = knownName ?: metadata.first ?: "여행 사진",
        previewBytes = previewBytes,
        latitude = coordinates?.first,
        longitude = coordinates?.second,
        capturedAt = formatDate(knownCapturedAtMillis ?: metadata.second),
        originalBytes = encodedBytes.takeIf { includeOriginalBytes },
    )
}.getOrNull()

private fun Context.readOriginalBytes(uri: Uri): ByteArray? = runCatching {
    contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        ?.takeIf(ByteArray::isNotEmpty)
}.getOrNull()

private fun ByteArray.normalizeOrientation(): ByteArray {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(this)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) {
        return this
    }

    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                setRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            else -> return this@normalizeOrientation
        }
    }
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
    val normalizedBitmap = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true,
    )
    return try {
        ByteArrayOutputStream().use { output ->
            if (!normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, OriginalJpegQuality, output)) {
                return@use this
            }
            output.toByteArray()
        }
    } finally {
        if (normalizedBitmap !== bitmap) normalizedBitmap.recycle()
        bitmap.recycle()
    }
}

private fun ByteArray.toPreviewByteArray(): ByteArray? {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder()
    } else {
        decodeWithBitmapFactory()
    } ?: return null

    val maxDimension = maxOf(bitmap.width, bitmap.height)
    val previewBitmap = if (maxDimension > PreviewSizePx) {
        val scale = PreviewSizePx.toFloat() / maxDimension
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }

    return try {
        ByteArrayOutputStream().use { output ->
            previewBitmap.compress(Bitmap.CompressFormat.JPEG, PreviewJpegQuality, output)
            output.toByteArray()
        }
    } finally {
        if (previewBitmap !== bitmap) previewBitmap.recycle()
        bitmap.recycle()
    }
}

private fun ByteArray.decodeWithImageDecoder(): Bitmap? {
    val source = ImageDecoder.createSource(ByteBuffer.wrap(this))
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val maxDimension = maxOf(info.size.width, info.size.height)
        if (maxDimension > PreviewSizePx) {
            val scale = PreviewSizePx.toFloat() / maxDimension
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
        }
    }
}

private fun ByteArray.decodeWithBitmapFactory(): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculatePreviewSampleSize(bounds.outWidth, bounds.outHeight)
    }
    return BitmapFactory.decodeByteArray(this, 0, size, options)
}

private fun calculatePreviewSampleSize(width: Int, height: Int): Int {
    val maxDimension = maxOf(width, height)
    var sampleSize = 1
    while (maxDimension / sampleSize > PreviewSizePx * 2) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun Context.queryPhotoMetadata(uri: Uri): Pair<String?, Long?> {
    val projection = arrayOf(
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
    )
    return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null to null
        val name = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            .takeIf { it >= 0 }
            ?.let(cursor::getString)
        val date = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            .takeIf { it >= 0 }
            ?.let(cursor::getLong)
            ?.takeIf { it > 0L }
        name to date
    } ?: (null to null)
}

internal fun Context.readCoordinates(uri: Uri): Pair<Double, Double>? = runCatching {
    val metadataUri = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        MediaStore.setRequireOriginal(uri)
    } else {
        uri
    }
    contentResolver.openInputStream(metadataUri)?.use { input ->
        ExifInterface(input).latLong?.let { it[0] to it[1] }
    }
}.getOrNull()

private fun Cursor.getStringOrNull(columnName: String): String? =
    getColumnIndex(columnName).takeIf { it >= 0 }?.let(::getString)

private fun Cursor.getLongOrNull(columnName: String): Long? =
    getColumnIndex(columnName).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

private fun Cursor.getIntOrNull(columnName: String): Int? =
    getColumnIndex(columnName).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)

private fun formatDate(epochMillis: Long?): String? = epochMillis?.let {
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(it))
}

private const val PreviewSizePx = 1280
private const val PreviewJpegQuality = 85
private const val OriginalJpegQuality = 100
private const val PhotoPerformanceTag = "MapmoryPhotoPerf"
private const val FullGalleryAccessMessage =
    "위치 기반 사진 추천을 사용하려면 전체 갤러리 접근 권한을 허용해 주세요."

private object TraceCookie {
    const val Recommend = 1
    const val RoomRead = 2
    const val RoomWrite = 3
    const val Pick = 4
    const val BoundaryLoad = 5
}
