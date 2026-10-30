@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.mapmory.shared.app.iosBackgroundSaves
import com.mapmory.shared.app.BackgroundSaveStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual fun createPhotoUploadTransfer(client: HttpClient, namespace: String): PhotoUploadTransfer = object : PhotoUploadTransfer {
    override suspend fun completedObjectKey(localId: String): String? = iosBackgroundUploads.completedObjectKey("$namespace:$localId")
    override suspend fun put(source: PhotoUploadSource, url: String, objectKey: String, contentType: ContentType) = iosBackgroundUploads.put(source.copy(localId = "$namespace:${source.localId}"), url, objectKey, contentType)
    override suspend fun forget(localIds: List<String>) = iosBackgroundUploads.forget(localIds.map { "$namespace:$it" })
}

/** Called by UIApplicationDelegate after iOS relaunches the app for a background session. */
fun handleBackgroundUploadEvents(completion: () -> Unit) {
    iosBackgroundUploads.completion = completion
}

@Serializable
private data class TransferEntry(val localId: String, val objectKey: String, val path: String, val completed: Boolean = false)

/** Files and task descriptions survive process termination. All mutable state is confined to Main. */
private val iosBackgroundUploads by lazy { IosBackgroundUploads() }

private class IosBackgroundUploads : NSObject(), NSURLSessionTaskDelegateProtocol {
    var completion: (() -> Unit)? = null
    private val root = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String) + "/record-uploads"
    private val json = Json { ignoreUnknownKeys = true }
    private val entries: MutableMap<String, TransferEntry>
    private val waiters = mutableMapOf<String, CancellableContinuation<String>>()
    private val session: NSURLSession

    init {
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        val payload = NSString.stringWithContentsOfFile("$root/transfers.json", NSUTF8StringEncoding, null)
        entries = payload?.let { json.decodeFromString<List<TransferEntry>>(it).associateBy { it.localId }.toMutableMap() } ?: mutableMapOf()
        val configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier("com.mapmory.record-uploads")
        configuration.setSessionSendsLaunchEvents(true)
        configuration.setDiscretionary(false)
        configuration.setHTTPMaximumConnectionsPerHost(2)
        session = NSURLSession.sessionWithConfiguration(configuration, this, NSOperationQueue.mainQueue)
    }

    suspend fun completedObjectKey(localId: String): String? = withContext(Dispatchers.Main) {
        entries[localId]?.takeIf { it.completed }?.objectKey
    }

    suspend fun put(source: PhotoUploadSource, url: String, objectKey: String, contentType: ContentType): String = withContext(Dispatchers.Main) {
        entries[source.localId]?.takeIf { it.completed }?.let { return@withContext it.objectKey }
        val tasks = suspendCancellableCoroutine<List<NSURLSessionTask>> { continuation ->
            session.getAllTasksWithCompletionHandler { tasks ->
                continuation.resume(tasks.orEmpty().filterIsInstance<NSURLSessionTask>())
            }
        }
        entries[source.localId]?.takeIf { it.completed }?.let { return@withContext it.objectKey }
        val existing = tasks.firstOrNull { it.taskDescription == source.localId }
        val preparedPath = if (existing == null) {
            val path = "$root/${NSUUID().UUIDString}.upload"
            try {
                withContext(Dispatchers.Default) {
                    check(source.bytes.usePinned {
                        NSData.create(it.addressOf(0), source.bytes.size.toULong()).writeToFile(path, true)
                    })
                }
                path
            } catch (error: Exception) {
                NSFileManager.defaultManager.removeItemAtPath(path, null)
                throw error
            }
        } else null
        suspendCancellableCoroutine { continuation ->
            waiters[source.localId] = continuation
            if (existing != null) {
                existing.resume()
            } else {
                entries.remove(source.localId)?.let { NSFileManager.defaultManager.removeItemAtPath(it.path, null) }
                val path = checkNotNull(preparedPath)
                val request = NSMutableURLRequest.requestWithURL(NSURL.URLWithString(url)!!).apply {
                    setHTTPMethod("PUT")
                    setValue(contentType.toString(), forHTTPHeaderField = "Content-Type")
                }
                val task = session.uploadTaskWithRequest(request, NSURL.fileURLWithPath(path))
                task.taskDescription = source.localId
                entries[source.localId] = TransferEntry(source.localId, objectKey, path)
                persist()
                task.resume()
            }
            // Cancellation of the UI coroutine must not cancel the OS-owned upload.
        }
    }

    suspend fun forget(localIds: List<String>) = withContext(Dispatchers.Main) {
        localIds.forEach { id -> entries.remove(id)?.let { NSFileManager.defaultManager.removeItemAtPath(it.path, null) } }
        persist()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        val id = task.taskDescription ?: return
        val entry = entries[id] ?: return
        val status = (task.response as? NSHTTPURLResponse)?.statusCode ?: 0
        val waiter = waiters.remove(id)
        if (didCompleteWithError == null && status in 200..299) {
            entries[id] = entry.copy(completed = true)
            persist()
            waiter?.resume(entry.objectKey)
        } else {
            entries.remove(id)
            NSFileManager.defaultManager.removeItemAtPath(entry.path, null)
            persist()
            waiter?.resumeWithException(IllegalStateException(didCompleteWithError?.localizedDescription ?: "사진 업로드 실패 ($status)"))
        }
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        val finished = completion
        completion = null
        CoroutineScope(Dispatchers.Main).launch {
            // Allow the resumed repository to persist the record and emit completion before suspension.
            withTimeoutOrNull(25_000) {
                iosBackgroundSaves?.first { saves -> saves.all { it.status == BackgroundSaveStatus.FAILED } }
            }
            finished?.invoke()
        }
    }

    private fun persist() {
        val bytes = json.encodeToString(entries.values.toList()).encodeToByteArray()
        check(bytes.usePinned { NSData.create(it.addressOf(0), bytes.size.toULong()).writeToFile("$root/transfers.json", true) })
    }
}
