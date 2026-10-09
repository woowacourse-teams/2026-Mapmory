package com.mapmory.shared.data.local.triprecord

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "trip_record_pages")
data class TripRecordPageEntity(
    @PrimaryKey val queryKey: String,
    val payload: String,
)

@Dao
interface TripRecordPageDao {
    @Query("SELECT payload FROM trip_record_pages WHERE queryKey = :queryKey")
    suspend fun read(queryKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write(page: TripRecordPageEntity)

    @Query("DELETE FROM trip_record_pages WHERE queryKey = :queryKey AND payload = :payload")
    suspend fun deleteIfMatches(queryKey: String, payload: String)

    @Query("DELETE FROM trip_record_pages")
    suspend fun clear()
}

// 사진 메타데이터 DB와 분리해 기존 사진 색인과 스키마를 유지한다.
@Database(entities = [TripRecordPageEntity::class], version = 1, exportSchema = false)
abstract class TripRecordListDatabase : RoomDatabase() {
    abstract fun pages(): TripRecordPageDao

    companion object {
        private val instances = mutableMapOf<String, TripRecordListDatabase>()

        fun getInstance(context: Context, environmentKey: String): TripRecordListDatabase = synchronized(instances) {
            instances.getOrPut(environmentKey) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TripRecordListDatabase::class.java,
                    "mapmory-trip-records-$environmentKey.db",
                ).build()
            }
        }
    }
}
