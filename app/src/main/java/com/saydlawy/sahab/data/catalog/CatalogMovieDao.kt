package com.saydlawy.sahab.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogMovieDao {

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 ORDER BY novaAdded DESC")
    fun observeAll(): Flow<List<CatalogMovieEntity>>

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND categoryId = :catId ORDER BY novaAdded DESC")
    fun observeByCategory(catId: String): Flow<List<CatalogMovieEntity>>

    @Query("SELECT * FROM catalog_movies WHERE sahabId = :id")
    suspend fun findById(id: String): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE sahabId IN (:ids) AND deleted = 0")
    suspend fun findByIds(ids: List<String>): List<CatalogMovieEntity>

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId = :streamId AND mediaType = :mediaType LIMIT 1")
    suspend fun findByStreamId(streamId: String, mediaType: String = "movie"): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId = :streamId AND mediaType = :mediaType AND deleted = 0 LIMIT 1")
    suspend fun findByStreamIdAndType(streamId: String, mediaType: String): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId IN (:streamIds) AND mediaType = :mediaType AND deleted = 0")
    suspend fun findByStreamIds(streamIds: List<String>, mediaType: String = "movie"): List<CatalogMovieEntity>

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND novaStreamId != '' ORDER BY novaAdded DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int, mediaType: String = "movie"): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = 'movie' AND novaStreamId != '' " +
            "AND year >= '2025' AND COALESCE(tmdbVoteCount, 0) >= 300 " +
            "AND CAST(rating AS REAL) >= 7.0 AND CAST(rating AS REAL) <= 9.2 " +
            "ORDER BY COALESCE(tmdbVoteCount, 0) DESC, CAST(rating AS REAL) DESC " +
            "LIMIT :limit"
    )
    suspend fun getTopPopularMovies(limit: Int): List<CatalogMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogMovieEntity>)

    @Query("UPDATE catalog_movies SET lastSeen = :now, novaAdded = :added WHERE novaStreamId = :streamId AND mediaType = :mediaType")
    suspend fun touch(streamId: String, added: Long, now: Long, mediaType: String = "movie")

    @Query("UPDATE catalog_movies SET lastSeen = :now, deleted = 0 WHERE novaStreamId IN (:streamIds) AND mediaType = :mediaType")
    suspend fun touchBulk(streamIds: List<String>, now: Long, mediaType: String = "movie"): Int

    @Transaction
    suspend fun applySyncBatch(
        existingIds: List<String>,
        newEntities: List<CatalogMovieEntity>,
        now: Long,
        mediaType: String,
    ) {
        if (existingIds.isNotEmpty()) {
            touchBulk(existingIds, now, mediaType)
        }
        if (newEntities.isNotEmpty()) {
            upsertAll(newEntities)
        }
    }

    @Query("UPDATE catalog_movies SET deleted = 1 WHERE deleted = 0 AND lastSeen < :cutoff")
    suspend fun softDeleteBefore(cutoff: Long): Int

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND needsEnrichment = 1 ORDER BY novaAdded DESC LIMIT :limit")
    suspend fun pendingEnrichment(limit: Int, mediaType: String = "movie"): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsEnrichment = 1")
    suspend fun countPendingEnrichment(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsEnrichment = 0")
    suspend fun countEnriched(): Int

    @Query(
        "UPDATE catalog_movies SET " +
            "tmdbId = :tmdbId, titleEn = :titleEn, year = :year, " +
            "plot = :plot, cast = :cast, castJson = :castJson, director = :director, genre = :genre, " +
            "runtime = :runtime, rating = :rating, backdropUrl = :backdropUrl, " +
            "postersJson = :postersJson, trailerUrl = :trailerUrl, " +
            "tmdbVoteCount = :voteCount, needsEnrichment = :status, lastEnriched = :now " +
            "WHERE novaStreamId = :streamId AND mediaType = :mediaType"
    )
    suspend fun applyEnrichment(
        streamId: String,
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
        mediaType: String = "movie",
        postersJson: String = "",
        trailerUrl: String = "",
        castJson: String = "",
    )

    @Query("SELECT novaStreamId FROM catalog_movies WHERE mediaType = :mediaType")
    suspend fun allStreamIds(mediaType: String = "movie"): List<String>

    @Query("UPDATE catalog_movies SET needsEnrichment = -1, lastEnriched = :now WHERE novaStreamId = :streamId AND mediaType = :mediaType")
    suspend fun markFailed(streamId: String, now: Long, mediaType: String = "movie")

    @Query(
        "UPDATE catalog_movies SET " +
            "plot = :plot, plotSource = :source, plotConfidence = :confidence, " +
            "identifiedTitle = :identifiedTitle, lastEnriched = :now " +
            "WHERE sahabId = :sahabId"
    )
    suspend fun updateAiPlot(
        sahabId: String,
        plot: String,
        source: String,
        confidence: String,
        identifiedTitle: String,
        now: Long,
    )

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (titleAr LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') " +
            "ORDER BY novaAdded DESC LIMIT :limit"
    )
    suspend fun searchCatalog(query: String, mediaType: String = "movie", limit: Int = 100): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPaged(mediaType: String = "movie", limit: Int, offset: Int): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND categoryId = :catId " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedByCategory(catId: String, mediaType: String = "movie", limit: Int, offset: Int): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (titleAr LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun searchCatalogPaged(query: String, mediaType: String = "movie", limit: Int = 100, offset: Int = 0): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType")
    suspend fun countByType(mediaType: String = "movie"): Int

    @Query(
        "SELECT novaStreamId, year, genre, `cast`, rating, novaAdded, tmdbVoteCount FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND novaStreamId != ''"
    )
    suspend fun getFacetMetadata(mediaType: String): List<CatalogFacetRow>

    @Query(
        "SELECT novaStreamId, year, genre, `cast`, rating, novaAdded, tmdbVoteCount FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND novaStreamId != ''"
    )
    fun observeFacetMetadata(mediaType: String): Flow<List<CatalogFacetRow>>

    @Query("DELETE FROM catalog_movies")
    suspend fun wipe()
}

data class CatalogFacetRow(
    val novaStreamId: String,
    val year: String,
    val genre: String,
    val cast: String,
    val rating: String,
    val novaAdded: Long,
    val tmdbVoteCount: Int? = null,
)
