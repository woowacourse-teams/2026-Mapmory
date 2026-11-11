@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSURL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSinceNow
import platform.Foundation.NSLog
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.darwin.dispatch_queue_create
import kotlin.native.Platform
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.writeToFile
import platform.Photos.PHAsset
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypePhoto
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosLocalPhotoDataSource : LocalPhotoDataSource, OriginalPhotoPrefetcher {
    init {
        // Start watching early so the first prefetch already knows about Low Data Mode.
        IosNetworkConditions.start()
    }

    override fun prefetch(localIds: List<String>) {
        // PHPicker fallback photos are already on the device.
        val assetIds = localIds.filterNot { it.startsWith(IosPendingPhotoPrefix) }
        if (assetIds.isEmpty()) return
        if (!shouldPrefetchOriginals(lowDataMode = IosNetworkConditions.isLowDataMode)) {
            logPhotoPerformance("prefetch_skipped reason=low_data_mode photos=${assetIds.size}")
            // Still record the selection so an earlier release keeps originals already downloaded.
            iosOriginalPrefetchQueue.markSelected(assetIds)
            return
        }
        iosOriginalPrefetchQueue.prefetch(assetIds)
    }

    override fun pruneCache(keep: Set<String>) {
        iosOriginalPrefetchScope.launch { pruneIosOriginalCache(keep) }
    }

    override fun releasePrefetched(localIds: Set<String>) {
        // Only originals this prefetcher downloaded; picker files belong to the save flow.
        val assetIds = localIds.filterNot { it.startsWith(IosPendingPhotoPrefix) }.toSet()
        if (assetIds.isEmpty()) return
        // Queued in call order, so deselecting and reselecting quickly keeps the original.
        iosOriginalPrefetchQueue.releaseLater(assetIds)
    }

    override suspend fun release(localId: String) {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            val root = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory, NSUserDomainMask, true,
            ).firstOrNull() as? String ?: return
            val directory = "$root/$IosPendingPhotoDirectory/"
            val path = localId.removePrefix(IosPendingPhotoPrefix)
            // Only remove a direct child created by cacheIosPickerPhoto.
            val filename = path.removePrefix(directory)
            if (path.startsWith(directory) && filename.isNotEmpty() &&
                filename != "." && filename != ".." && '/' !in filename
            ) {
                withContext(Dispatchers.Default) {
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                }
            }
            return
        }
        val root = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val directory = "$root/$IosOriginalCacheDirectoryName"
        val prefix = "$localId:".encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val manager = NSFileManager.defaultManager
        manager.contentsOfDirectoryAtPath(directory, null)?.filterIsInstance<String>()
            ?.filter { it.startsWith(prefix) }?.forEach { manager.removeItemAtPath("$directory/$it", null) }
    }

    override suspend fun capturedAt(localId: String): String? {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            return NSData.dataWithContentsOfFile(localId.removePrefix(IosPendingPhotoPrefix))?.originalPhotoDate()
        }
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null).firstObject as? PHAsset
            ?: return null
        return asset.creationDate?.let { date ->
            platform.Foundation.NSDateFormatter().run {
                // 기기 달력이 불기·일본력이어도 서버와 같은 서기 연도로 적는다.
                locale = platform.Foundation.NSLocale(localeIdentifier = "en_US_POSIX")
                calendar = platform.Foundation.NSCalendar.calendarWithIdentifier(platform.Foundation.NSCalendarIdentifierGregorian)
                dateFormat = "yyyy.MM.dd"
                stringFromDate(date)
            }
        }
    }

    override suspend fun read(localId: String): ByteArray? {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            val path = localId.removePrefix(IosPendingPhotoPrefix)
            return NSData.dataWithContentsOfFile(path)
                ?.toByteArray()
                ?.takeIf(ByteArray::isNotEmpty)
        }

        val startedAtMillis = nowMillis()
        // A prefetch already downloading this photo is awaited instead of starting a second download.
        val waited = iosOriginalPrefetchQueue.awaitPrefetch(localId)
        val original = ensureIosOriginalFile(localId) ?: return null
        logPhotoPerformance(
            "original_read_ms=${elapsedMillis(startedAtMillis)} source=" + when {
                original.downloaded -> "download"
                waited -> "prefetch_wait"
                else -> "cache"
            },
        )
        return withContext(Dispatchers.Default) {
            NSData.dataWithContentsOfFile(original.path)?.toByteArray()?.takeIf(ByteArray::isNotEmpty)
        }
    }
}

private class IosOriginalFile(val path: String, val downloaded: Boolean)

/** Downloads the photo's original into the upload cache once; later calls reuse the file. */
private suspend fun ensureIosOriginalFile(localId: String): IosOriginalFile? {
    val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null)
        .firstObject as? PHAsset
        ?: return null
    val resources = PHAssetResource.assetResourcesForAsset(asset)
        .filterIsInstance<PHAssetResource>()
    val resource = resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
        ?: resources.firstOrNull()
        ?: return null

    val directory = iosOriginalCacheDirectory() ?: return null
    val manager = NSFileManager.defaultManager
    val version = asset.modificationDate?.timeIntervalSinceReferenceDate ?: 0.0
    val path = "$directory/${iosOriginalCacheFileName(localId, version)}"
    var downloaded = false
    if (!manager.fileExistsAtPath(path)) {
        downloaded = true
        val temporary = "$directory/${NSUUID().UUIDString}.part"
        val success = suspendCoroutine<Boolean> { continuation ->
            PHAssetResourceManager.defaultManager().writeDataForAssetResource(
                resource,
                toFile = NSURL.fileURLWithPath(temporary),
                options = PHAssetResourceRequestOptions().apply { networkAccessAllowed = true },
            ) { error -> continuation.resume(error == null) }
        }
        if (!success) {
            manager.removeItemAtPath(temporary, null)
            return null
        }
        if (!manager.moveItemAtPath(temporary, path, null)) {
            manager.removeItemAtPath(temporary, null)
            if (!manager.fileExistsAtPath(path)) return null
        }
    }
    return IosOriginalFile(path, downloaded)
}

internal fun iosOriginalCacheDirectory(): String? {
    val root = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    val directory = "$root/$IosOriginalCacheDirectoryName"
    return directory.takeIf { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
}

/** Hex of "localId:version"; [IosLocalPhotoDataSource.release] matches files by the "localId:" prefix. */
internal fun iosOriginalCacheFileName(localId: String, version: Double): String =
    "$localId:$version".encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

private fun iosOriginalCacheLocalId(fileName: String): String? {
    if (fileName.length % 2 != 0) return null
    val bytes = fileName.chunked(2).map { it.toIntOrNull(16)?.toByte() ?: return null }.toByteArray()
    return bytes.decodeToString().substringBeforeLast(':', missingDelimiterValue = "").takeIf(String::isNotEmpty)
}

/**
 * Removes originals left by flows that ended without onCleared (app killed) and interrupted downloads.
 * Runs once at startup, keeping originals that restored background saves will read.
 * A save whose original is missing downloads it again, so removing too much costs time, not data.
 */
internal suspend fun pruneIosOriginalCache(keep: Set<String>) = withContext(Dispatchers.Default) {
    val directory = iosOriginalCacheDirectory() ?: return@withContext
    val manager = NSFileManager.defaultManager
    manager.contentsOfDirectoryAtPath(directory, null)?.filterIsInstance<String>()?.forEach { fileName ->
        val path = "$directory/$fileName"
        val remove = if (fileName.endsWith(".part")) {
            // Restored saves start downloading right away; only leftovers from earlier runs are stale.
            isOlderThan(path, StalePartialDownloadSeconds)
        } else {
            iosOriginalCacheLocalId(fileName)?.let { it !in keep } ?: true
        }
        if (remove) manager.removeItemAtPath(path, null)
    }
}

private fun isOlderThan(path: String, seconds: Double): Boolean {
    val modified = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
        ?.get(NSFileModificationDate) as? NSDate
        ?: return false
    return -modified.timeIntervalSinceNow > seconds
}

private const val StalePartialDownloadSeconds = 10.0 * 60.0

private val iosOriginalPrefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** App-wide so a save reading through any IosLocalPhotoDataSource sees prefetches started elsewhere. */
private val iosOriginalPrefetchQueue = OriginalPrefetchQueue(
    scope = iosOriginalPrefetchScope,
    download = { localId ->
        val startedAtMillis = nowMillis()
        val ok = ensureIosOriginalFile(localId) != null
        logPhotoPerformance("prefetch_ms=${elapsedMillis(startedAtMillis)} ok=$ok")
        ok
    },
    delete = { localId -> IosLocalPhotoDataSource().release(localId) },
)

/** Tracks Low Data Mode (NWPath.isConstrained) for the original prefetch policy. */
private object IosNetworkConditions {
    @kotlin.concurrent.Volatile
    private var constrained = false

    // Held for the app's lifetime; a released monitor stops delivering path updates.
    private val monitor = nw_path_monitor_create().also { monitor ->
        nw_path_monitor_set_update_handler(monitor) { path -> constrained = nw_path_is_constrained(path) }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.mapmory.network-conditions", null))
        nw_path_monitor_start(monitor)
    }

    val isLowDataMode: Boolean get() = constrained

    /** Object initialization starts the monitor exactly once. */
    fun start() {
        monitor
    }
}

private fun nowMillis(): Long = (NSDate().timeIntervalSinceReferenceDate * 1000.0).toLong()

private fun elapsedMillis(startedAtMillis: Long): Long = (nowMillis() - startedAtMillis).coerceAtLeast(0L)

private fun logPhotoPerformance(message: String) {
    if (Platform.isDebugBinary) NSLog("MapmoryPhotoPerf $message")
}

internal fun cacheIosPickerPhoto(data: NSData): String? {
    if (data.length == 0UL) return null
    val root = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() ?: return null
    val directory = "$root/$IosPendingPhotoDirectory"
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    ) {
        return null
    }
    val path = "$directory/${NSUUID().UUIDString}"
    return if (data.writeToFile(path = path, atomically = true)) {
        "$IosPendingPhotoPrefix$path"
    } else {
        null
    }
}

internal fun isIosLocalPhotoAvailable(localId: String): Boolean =
    if (localId.startsWith(IosPendingPhotoPrefix)) {
        NSFileManager.defaultManager.fileExistsAtPath(localId.removePrefix(IosPendingPhotoPrefix))
    } else {
        PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null).firstObject != null
    }

private fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    return ByteArray(length.toInt()).also { bytes ->
        bytes.usePinned { pinned -> getBytes(pinned.addressOf(0), length) }
    }
}

private const val IosPendingPhotoPrefix = "mapmory-cache://"
private const val IosOriginalCacheDirectoryName = "mapmory-upload-originals"
private const val IosPendingPhotoDirectory = "mapmory-pending-photos"
