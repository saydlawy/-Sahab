package com.saydlawy.sahab.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogMovieDao {

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 ORDER BY novaAdded DESC")
    fun observeAll(): Flow<List<CatalogMovieEntity>>

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND categoryId = :catId ORDER BY novaAdded DESC")
    fun observeByCategory(catId: String): Flow<List<CatalogMovieEntity>>

    @Query("SELECT * FROM catalog_movies WHERE sahabId = :id")
    suspend fun findById(id: String): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId = :streamId")
    suspend fun findByStreamId(streamId: String): CatalogMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogMovieEntity>)

    @Query("UPDATE catalog_movies SET lastSeen = :now, novaAdded = :added WHERE novaStreamId = :streamId")
    suspend fun touch(streamId: String, added: Long, now: Long)

    @Query("UPDATE catalog_movies SET lastSeen = :now WHERE novaStreamId IN (:streamIds)")
    suspend fun touchBulk(streamIds: List<String>, now: Long): Int

    @Query("UPDATE catalog_movies SET deleted = 1 WHERE deleted = 0 AND lastSeen < :cutoff")
    suspend fun softDeleteBefore(cutoff: Long): Int

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND needsEnrichment = 1 ORDER BY novaAdded DESC LIMIT :limit")
    suspend fun pendingEnrichment(limit: Int): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsEnrichment = 1")
    suspend fun countPendingEnrichment(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsEnrichment = 0")
    suspend fun countEnriched(): Int

    @Query(
        "UPDATE catalog_movies SET " +
            "sahabId = :newId, tmdbId = :tmdbId, titleEn = :titleEn, year = :year, " +
            "plot = :plot, cast = :cast, director = :director, genre = :genre, " +
            "runtime = :runtime, rating = :rating, backdropUrl = :backdropUrl, " +
            "tmdbVoteCount = :voteCount, needsEnrichment = :status, lastEnriched = :now " +
            "WHERE novaStreamId = :streamId"
    )
    suspend fun applyEnrichment(
        streamId: String,
        newId: String,
        tmdbId: Int?,
        titleEn: String,
        year: String,
        plot: String,
        cast: String,
        director: String,
        genre: String,
        runtime: String,
        rating: String,
        backdropUrl: String,
        voteCount: Int?,
        status: Int,
        now: Long,
    )

    @Query("SELECT novaStreamId FROM catalog_movies WHERE deleted = 0")
    suspend fun allStreamIds(): List<String>

    @Query("UPDATE catalog_movies SET needsEnrichment = -1, lastEnriched = :now WHERE novaStreamId = :streamId")
    suspend fun markFailed(streamId: String, now: Long)

    @Query("DELETE FROM catalog_movies")
    suspend fun wipe()
}
