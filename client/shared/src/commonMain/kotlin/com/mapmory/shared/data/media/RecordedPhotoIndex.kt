package com.mapmory.shared.data.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 기기 사진 식별자만 저장한다. 같은 사진을 여러 기록이 사용하면 마지막 기록 삭제까지 유지한다. */
class RecordedPhotoIndex(private val storage: PhotoPreviewCache?) {
    private val mutex = Mutex()
    private var initialized = false
    private var records = emptyMap<Long, Set<String>>()
    private val mutableIds = MutableStateFlow<Set<String>>(emptySet())
    val ids = mutableIds.asStateFlow()

    suspend fun initialize() = mutex.withLock { load() }

    suspend fun replace(recordId: Long, photoIds: Set<String>) = mutex.withLock {
        load()
        records = if (photoIds.isEmpty()) records - recordId else records + (recordId to photoIds)
        mutableIds.value = records.values.flatten().toSet()
        storage?.writeRecordedPhotoIndex(Json.encodeToString(records))
    }

    private suspend fun load() {
        if (initialized) return
        val saved = storage?.readRecordedPhotoIndex()
        records = saved?.let {
            runCatching { Json.decodeFromString<Map<Long, Set<String>>>(it) }.getOrNull()
        }.orEmpty()
        mutableIds.value = records.values.flatten().toSet()
        initialized = true
    }
}
