package com.mapmory.shared.data.local.photo

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface PhotoMetadataDao {
    @Query("SELECT capturedAtMillis FROM photo_metadata WHERE contentUri = :contentUri LIMIT 1")
    suspend fun capturedAtMillis(contentUri: String): Long?

    @Query("SELECT * FROM photo_metadata")
    suspend fun getAll(): List<PhotoMetadataEntity>

    @Query(
        """
        SELECT * FROM photo_metadata
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        ORDER BY capturedAtMillis DESC
        """,
    )
    suspend fun getLocatedPhotos(): List<PhotoMetadataEntity>

    @Query(
        """
        SELECT * FROM photo_metadata
        WHERE capturedAtMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY capturedAtMillis DESC
        """,
    )
    suspend fun getPhotosCapturedBetween(
        fromMillis: Long,
        toMillis: Long,
    ): List<PhotoMetadataEntity>

    @Upsert
    suspend fun upsertAll(photos: List<PhotoMetadataEntity>)

    @Query("DELETE FROM photo_metadata WHERE scanId != :currentScanId")
    suspend fun deleteNotSeenIn(currentScanId: Long)

    @Transaction
    suspend fun replaceSnapshot(
        photos: List<PhotoMetadataEntity>,
        scanId: Long,
    ) {
        upsertAll(photos)
        deleteNotSeenIn(scanId)
    }
}
