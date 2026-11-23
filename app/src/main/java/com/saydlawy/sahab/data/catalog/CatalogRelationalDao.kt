package com.saydlawy.sahab.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogRelationalDao {

    // ═══ Categories ═══

    @Query("SELECT * FROM catalog_categories WHERE mediaType = :mediaType ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategories(mediaType: String): List<CatalogCategoryEntity>

    @Query("SELECT * FROM catalog_categories WHERE mediaType = :mediaType ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(mediaType: String): Flow<List<CatalogCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<CatalogCategoryEntity>)

    @Query("DELETE FROM catalog_categories WHERE mediaType = :mediaType")
    suspend fun clearCategories(mediaType: String)

    @Transaction
    suspend fun replaceCategories(mediaType: String, items: List<CatalogCategoryEntity>) {
        if (items.isEmpty()) return
        clearCategories(mediaType)
        upsertCategories(items)
    }

    // ═══ Seasons ═══

    @Query("SELECT * FROM series_seasons WHERE seriesStreamId = :seriesStreamId ORDER BY seasonNumber ASC")
    suspend fun getSeasons(seriesStreamId: String): List<SeriesSeasonEntity>

    @Query("SELECT * FROM series_seasons WHERE seriesStreamId = :seriesStreamId ORDER BY seasonNumber ASC")
    fun observeSeasons(seriesStreamId: String): Flow<List<SeriesSeasonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasons(items: List<SeriesSeasonEntity>)

    // ═══ Episodes ═══

    @Query("SELECT * FROM series_episodes WHERE seriesStreamId = :seriesStreamId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getAllEpisodesForSeries(seriesStreamId: String): List<SeriesEpisodeEntity>

    @Query("SELECT * FROM series_episodes WHERE seriesStreamId = :seriesStreamId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    suspend fun getEpisodesForSeason(seriesStreamId: String, seasonNumber: Int): List<SeriesEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<SeriesEpisodeEntity>)

    @Query("DELETE FROM series_episodes WHERE seriesStreamId = :seriesStreamId AND episodeId NOT IN (:keepIds)")
    suspend fun deleteRemovedEpisodes(seriesStreamId: String, keepIds: List<String>)

    @Transaction
    suspend fun syncSeriesEpisodesPreservingTmdb(
        seriesStreamId: String,
        incoming: List<SeriesEpisodeEntity>,
        now: Long,
    ) {
        if (incoming.isEmpty()) return
        val existingById = getAllEpisodesForSeries(seriesStreamId).associateBy { it.episodeId }
        val merged = incoming.map { ep ->
            val prev = existingById[ep.episodeId]
            if (prev == null) ep
            else ep.copy(
                tmdbStillUrl = prev.tmdbStillUrl.ifBlank { ep.tmdbStillUrl },
                plot = ep.plot.ifBlank { prev.plot },
                airDate = ep.airDate.ifBlank { prev.airDate },
                updatedAt = now,
            )
        }
        val seasonNums = merged.map { it.seasonNumber }.distinct().sorted()
        val existingSeasons = getSeasons(seriesStreamId).associateBy { it.seasonNumber }
        val seasonEntities = seasonNums.map { num ->
            existingSeasons[num]?.copy(updatedAt = now)
                ?: SeriesSeasonEntity(seriesStreamId = seriesStreamId, seasonNumber = num, updatedAt = now)
        }
        upsertSeasons(seasonEntities)
        merged.chunked(500).forEach { upsertEpisodes(it) }
        val keepIds = merged.map { it.episodeId }
        if (existingById.isNotEmpty() && keepIds.size >= (existingById.size * 0.70).toInt()) {
            deleteRemovedEpisodes(seriesStreamId, keepIds)
        }
    }

    // ═══ Sync Metadata ═══

    @Query("SELECT * FROM sync_metadata WHERE syncKey = :key LIMIT 1")
    suspend fun getSyncMeta(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMeta(meta: SyncMetadataEntity)

    @Query("DELETE FROM catalog_categories")
    suspend fun wipeCategories()

    @Query("DELETE FROM series_seasons")
    suspend fun wipeSeasons()

    @Query("DELETE FROM series_episodes")
    suspend fun wipeEpisodes()

    @Query("DELETE FROM sync_metadata")
    suspend fun wipeSyncMetadata()

    @Transaction
    suspend fun wipeAllRelational() {
        wipeEpisodes()
        wipeSeasons()
        wipeCategories()
        wipeSyncMetadata()
    }
}
