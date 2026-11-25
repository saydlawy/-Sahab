package com.saydlawy.sahab.feature.instantwatch.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface InstantWatchDao {
    @Query("SELECT * FROM instant_watch_history ORDER BY updatedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<InstantWatchHistoryEntity>>

    @Query("SELECT * FROM instant_watch_history WHERE contentKey = :contentKey LIMIT 1")
    suspend fun getByKey(contentKey: String): InstantWatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: InstantWatchHistoryEntity)

    @Query("DELETE FROM instant_watch_history WHERE contentKey = :contentKey")
    suspend fun deleteByKey(contentKey: String)

    @Query("DELETE FROM instant_watch_history")
    suspend fun clearAll()
}

@Database(
    entities = [InstantWatchHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class InstantWatchDatabase : RoomDatabase() {
    abstract fun instantWatchDao(): InstantWatchDao

    companion object {
        @Volatile
        private var instance: InstantWatchDatabase? = null

        fun getInstance(context: Context): InstantWatchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InstantWatchDatabase::class.java,
                    "instant_watch.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
