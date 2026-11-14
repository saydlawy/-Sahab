package com.saydlawy.sahab.data.channel

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    // ═══ Channels ═══

    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY sortOrder ASC, displayName ASC")
    fun observeVisible(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isHidden = 0 AND categoryId = :categoryId ORDER BY sortOrder ASC, displayName ASC")
    fun observeByCategory(categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE streamId = :streamId")
    suspend fun findById(streamId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE epgId = :epgId LIMIT 1")
    suspend fun findByEpgId(epgId: String): ChannelEntity?

    @Query("SELECT streamId FROM channels")
    suspend fun allStreamIds(): List<String>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("SELECT * FROM channels")
    suspend fun all(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :fav, updatedAt = :now WHERE streamId = :streamId")
    suspend fun setFavorite(streamId: String, fav: Boolean, now: Long)

    @Query("UPDATE channels SET isHidden = :hidden, updatedAt = :now WHERE streamId = :streamId")
    suspend fun setHidden(streamId: String, hidden: Boolean, now: Long)

    @Query("UPDATE channels SET sortOrder = :order, updatedAt = :now WHERE streamId = :streamId")
    suspend fun setSortOrder(streamId: String, order: Int, now: Long)

    @Query("UPDATE channels SET lastWatchedAt = :now WHERE streamId = :streamId")
    suspend fun markWatched(streamId: String, now: Long)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    // ═══ EPG ═══

    @Query("SELECT * FROM epg_programs WHERE epgId = :epgId AND end > :nowSec ORDER BY start ASC")
    suspend fun programsForChannel(epgId: String, nowSec: Long): List<EpgProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE start <= :nowSec AND end > :nowSec ORDER BY start ASC")
    fun observeNowPlaying(nowSec: Long): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrograms(items: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE epgId = :epgId")
    suspend fun deleteProgramsForChannel(epgId: String)

    @Query("DELETE FROM epg_programs WHERE end < :cutoffSec")
    suspend fun deleteOldPrograms(cutoffSec: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllPrograms()

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun programCount(): Int

    // ═══ Custom Lists ═══

    @Query("SELECT * FROM channel_lists ORDER BY sortOrder ASC, name ASC")
    fun observeLists(): Flow<List<ChannelListEntity>>

    @Query("SELECT streamId FROM channel_list_items WHERE listId = :listId ORDER BY position ASC")
    suspend fun itemsOfList(listId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ChannelListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListItems(items: List<ChannelListItemEntity>)

    @Query("DELETE FROM channel_list_items WHERE listId = :listId")
    suspend fun clearListItems(listId: String)

    @Query("DELETE FROM channel_lists WHERE listId = :listId")
    suspend fun deleteList(listId: String)
}
