package com.saydlawy.sahab.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal const val CATALOG_FACET_PAGE_SIZE = 400
internal const val CATALOG_ENRICHED_PAGE_SIZE = 200

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

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND novaStreamId != '' " +
            "AND rating != '' AND CAST(rating AS REAL) >= 6.0 AND CAST(rating AS REAL) < 9.9 " +
            "AND (posterUrl != '' OR backdropUrl != '') " +
            "AND UPPER(titleAr || ' ' || titleEn) NOT LIKE '%H265%' " +
            "AND UPPER(titleAr || ' ' || titleEn) NOT LIKE '%HEVC%' " +
            "AND UPPER(titleAr || ' ' || titleEn) NOT LIKE '% CAM%' " +
            "AND UPPER(titleAr || ' ' || titleEn) NOT LIKE '%HDCAM%' " +
            "GROUP BY COALESCE(NULLIF(tmdbId, 0), sahabId) " +
            "ORDER BY CAST(rating AS REAL) DESC, COALESCE(tmdbVoteCount, 0) DESC, novaAdded DESC " +
            "LIMIT :limit",
    )
    suspend fun getTopRatedUnique(mediaType: String = "movie", limit: Int = 10): List<CatalogMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogMovieEntity>)

    @Transaction
    suspend fun mergeCloudBatchPreservingLocal(cloudItems: List<CatalogMovieEntity>) {
        if (cloudItems.isEmpty()) return
        val existingById = HashMap<String, CatalogMovieEntity>(cloudItems.size)
        cloudItems.map { it.sahabId }.distinct().chunked(500).forEach { idChunk ->
            findByIds(idChunk).forEach { existingById[it.sahabId] = it }
        }
        val merged = cloudItems.map { cloud ->
            val local = existingById[cloud.sahabId]
            if (local == null) {
                cloud.copy(needsCloudPush = 0)
            } else if (local.needsCloudPush == 1 && local.updatedAt >= cloud.updatedAt) {
                // Keep pending local edit until it is confirmed pushed to cloud.
                local
            } else {
                val keepLocalAiPlot = local.plotSource == "gemini_user_key" && local.plot.isNotBlank()
                cloud.copy(
                    novaStreamId = local.novaStreamId.ifBlank { cloud.novaStreamId },
                    categoryId = local.categoryId.ifBlank { cloud.categoryId },
                    titleAr = local.titleAr.ifBlank { cloud.titleAr },
                    titleEn = cloud.titleEn.ifBlank { local.titleEn },
                    year = cloud.year.ifBlank { local.year },
                    plot = if (keepLocalAiPlot) local.plot else cloud.plot.ifBlank { local.plot },
                    plotSource = if (keepLocalAiPlot) local.plotSource else cloud.plotSource.ifBlank { local.plotSource },
                    plotConfidence = if (keepLocalAiPlot) local.plotConfidence else cloud.plotConfidence.ifBlank { local.plotConfidence },
                    identifiedTitle = if (keepLocalAiPlot) local.identifiedTitle else cloud.identifiedTitle.ifBlank { local.identifiedTitle },
                    cast = cloud.cast.ifBlank { local.cast },
                    castJson = cloud.castJson.ifBlank { local.castJson },
                    director = cloud.director.ifBlank { local.director },
                    genre = cloud.genre.ifBlank { local.genre },
                    runtime = cloud.runtime.ifBlank { local.runtime },
                    rating = cloud.rating.ifBlank { local.rating },
                    posterUrl = local.posterUrl.ifBlank { cloud.posterUrl },
                    backdropUrl = cloud.backdropUrl.ifBlank { local.backdropUrl },
                    postersJson = cloud.postersJson.ifBlank { local.postersJson },
                    trailerUrl = cloud.trailerUrl.ifBlank { local.trailerUrl },
                    imdbId = cloud.imdbId.ifBlank { local.imdbId },
                    novaAdded = maxOf(local.novaAdded, cloud.novaAdded),
                    containerExtension = local.containerExtension.ifBlank { cloud.containerExtension },
                    tmdbId = cloud.tmdbId ?: local.tmdbId,
                    tmdbVoteCount = cloud.tmdbVoteCount ?: local.tmdbVoteCount,
                    deleted = local.deleted,
                    needsCloudPush = 0,
                    updatedAt = maxOf(local.updatedAt, cloud.updatedAt),
                )
            }
        }
        upsertAll(merged)
    }

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

    @Query("UPDATE catalog_movies SET deleted = 1 WHERE deleted = 0 AND mediaType = :mediaType AND lastSeen < :cutoff")
    suspend fun softDeleteBefore(cutoff: Long, mediaType: String = "movie"): Int

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
        "SELECT * FROM catalog_movies WHERE deleted = 0 AND tmdbId IS NOT NULL AND tmdbId > 0 " +
            "AND sahabId > :afterSahabId ORDER BY sahabId ASC LIMIT :limit"
    )
    suspend fun getEnrichedPage(
        afterSahabId: String = "",
        limit: Int = CATALOG_ENRICHED_PAGE_SIZE,
    ): List<CatalogMovieEntity>

    suspend fun getAllEnriched(): List<CatalogMovieEntity> = loadAllBySahabIdPage(CATALOG_ENRICHED_PAGE_SIZE) { cursor, limit ->
        getEnrichedPage(afterSahabId = cursor, limit = limit)
    }

    @Query(
        "UPDATE catalog_movies SET " +
            "tmdbId = :tmdbId, titleEn = :titleEn, year = :year, " +
            "plot = :plot, cast = :cast, castJson = :castJson, director = :director, genre = :genre, " +
            "runtime = :runtime, rating = :rating, backdropUrl = :backdropUrl, " +
            "postersJson = :postersJson, trailerUrl = :trailerUrl, " +
            "tmdbVoteCount = :voteCount, needsEnrichment = :status, lastEnriched = :now, " +
            "deleted = 0, lastSeen = :now, needsCloudPush = :needsCloudPush, updatedAt = :now " +
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
        needsCloudPush: Int = 1,
    )

    @Query("SELECT novaStreamId FROM catalog_movies WHERE mediaType = :mediaType")
    suspend fun allStreamIds(mediaType: String = "movie"): List<String>

    @Query("UPDATE catalog_movies SET needsEnrichment = -1, lastEnriched = :now WHERE novaStreamId = :streamId AND mediaType = :mediaType")
    suspend fun markFailed(streamId: String, now: Long, mediaType: String = "movie")

    @Query(
        "UPDATE catalog_movies SET " +
            "plot = :plot, plotSource = :source, plotConfidence = :confidence, " +
            "identifiedTitle = :identifiedTitle, lastEnriched = :now, " +
            "needsCloudPush = :needsCloudPush, updatedAt = :now " +
            "WHERE sahabId = :sahabId"
    )
    suspend fun updateAiPlot(
        sahabId: String,
        plot: String,
        source: String,
        confidence: String,
        identifiedTitle: String,
        now: Long,
        needsCloudPush: Int = 1,
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
        "SELECT categoryId, COUNT(*) AS cnt FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND categoryId != '' " +
            "GROUP BY categoryId"
    )
    suspend fun getCategoryCounts(mediaType: String = "movie"): List<CategoryCountRow>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (',' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(genre), '،', ','), '/', ','), '|', ','), ', ', ','), ' ,', ',') || ',') " +
            "LIKE '%,' || TRIM(:genre) || ',%' " +
            "ORDER BY CAST(rating AS REAL) DESC, novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedByGenre(
        genre: String,
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND year LIKE '%' || :year || '%' " +
            "ORDER BY CAST(rating AS REAL) DESC, novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedByYear(
        year: String,
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (',' || REPLACE(REPLACE(REPLACE(TRIM(`cast`), '،', ','), ', ', ','), ' ,', ',') || ',') " +
            "LIKE '%,' || TRIM(:actor) || ',%' " +
            "ORDER BY CAST(rating AS REAL) DESC, novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedByCast(
        actor: String,
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query("SELECT genre FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND genre != ''")
    suspend fun getRawGenres(mediaType: String = "movie"): List<String>

    @Query("SELECT year FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND year != ''")
    suspend fun getRawYears(mediaType: String = "movie"): List<String>

    @Query("SELECT `cast` FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND `cast` != ''")
    suspend fun getRawCasts(mediaType: String = "movie"): List<String>

    @Query(
        "SELECT rating FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND rating != '' " +
            "AND CAST(rating AS REAL) >= 6.0 AND CAST(rating AS REAL) < 9.9"
    )
    suspend fun getRawRatings(mediaType: String = "movie"): List<String>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND rating != '' " +
            "AND CAST(rating AS REAL) >= :minRating AND CAST(rating AS REAL) < :maxRating " +
            "ORDER BY CAST(rating AS REAL) DESC, COALESCE(tmdbVoteCount, 0) DESC, novaAdded DESC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedByRatingRange(
        minRating: Double,
        maxRating: Double,
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(genre) = ''")
    suspend fun countEmptyGenre(mediaType: String = "movie"): Int

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(genre) = '' " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedEmptyGenre(
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(year) = ''")
    suspend fun countEmptyYear(mediaType: String = "movie"): Int

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(year) = '' " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedEmptyYear(
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(`cast`) = ''")
    suspend fun countEmptyCast(mediaType: String = "movie"): Int

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND TRIM(`cast`) = '' " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedEmptyCast(
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT COUNT(*) FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (TRIM(rating) = '' OR CAST(rating AS REAL) <= 0 OR CAST(rating AS REAL) >= 9.9)"
    )
    suspend fun countEmptyRating(mediaType: String = "movie"): Int

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (TRIM(rating) = '' OR CAST(rating AS REAL) <= 0 OR CAST(rating AS REAL) >= 9.9) " +
            "ORDER BY novaAdded DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPagedEmptyRating(
        mediaType: String = "movie",
        limit: Int = 120,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT novaStreamId, year, genre, `cast`, rating, novaAdded, tmdbVoteCount, titleAr, titleEn, identifiedTitle, sahabId " +
            "FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND (novaStreamId != '' OR genre != '' OR year != '' OR rating != '') " +
            "AND sahabId > :afterSahabId " +
            "ORDER BY sahabId ASC LIMIT :limit"
    )
    suspend fun getFacetMetadataPage(
        mediaType: String,
        afterSahabId: String = "",
        limit: Int = CATALOG_FACET_PAGE_SIZE,
    ): List<CatalogFacetRow>

    @Query(
        "SELECT COALESCE(MAX(lastEnriched), 0) || ':' || COUNT(*) FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType"
    )
    fun observeFacetVersion(mediaType: String): Flow<String>

    @Query(
        "SELECT COALESCE(MAX(lastEnriched), 0) || ':' || COUNT(*) FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType"
    )
    suspend fun getFacetVersion(mediaType: String): String

    suspend fun getFacetMetadata(mediaType: String): List<CatalogFacetRow> =
        loadAllFacetsByPage(mediaType, CATALOG_FACET_PAGE_SIZE)

    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = 0 THEN 1 ELSE 0 END), 0) AS enriched, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = 1 THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = -1 THEN 1 ELSE 0 END), 0) AS failed, " +
            "COALESCE(SUM(CASE WHEN tmdbId IS NOT NULL AND tmdbId > 0 THEN 1 ELSE 0 END), 0) AS withTmdb " +
            "FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType"
    )
    suspend fun getMediaFlowStats(mediaType: String): MediaFlowStats

    @Query("UPDATE catalog_movies SET needsEnrichment = 1 WHERE deleted = 0 AND needsEnrichment = -1")
    suspend fun resetFailedEnrichment(): Int

    @Query("SELECT * FROM catalog_movies WHERE deleted = 0 AND needsCloudPush = 1 ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingCloudPush(limit: Int = 50): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsCloudPush = 1")
    suspend fun countPendingCloudPush(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND needsCloudPush = 1")
    fun observePendingCloudPushCount(): Flow<Int>

    @Query("UPDATE catalog_movies SET needsCloudPush = 0 WHERE sahabId IN (:sahabIds)")
    suspend fun markCloudPushed(sahabIds: List<String>)

    @Query("UPDATE catalog_movies SET needsCloudPush = 1, updatedAt = :now WHERE novaStreamId = :streamId AND mediaType = :mediaType")
    suspend fun markNeedsCloudPush(streamId: String, mediaType: String, now: Long)

    @Query("DELETE FROM catalog_movies")
    suspend fun wipe()
}

suspend fun CatalogMovieDao.loadAllFacetsByPage(
    mediaType: String,
    pageSize: Int = CATALOG_FACET_PAGE_SIZE,
): List<CatalogFacetRow> {
    val safePageSize = pageSize.coerceAtLeast(1)
    val out = ArrayList<CatalogFacetRow>(safePageSize)
    var cursor = ""
    while (true) {
        val page = getFacetMetadataPage(mediaType = mediaType, afterSahabId = cursor, limit = safePageSize)
        if (page.isEmpty()) break
        out.addAll(page)
        val nextCursor = page.last().sahabId
        if (page.size < safePageSize || nextCursor.isEmpty() || nextCursor <= cursor) break
        cursor = nextCursor
    }
    return out
}

private suspend inline fun loadAllBySahabIdPage(
    pageSize: Int,
    crossinline fetchPage: suspend (afterSahabId: String, limit: Int) -> List<CatalogMovieEntity>,
): List<CatalogMovieEntity> {
    val safePageSize = pageSize.coerceAtLeast(1)
    val out = ArrayList<CatalogMovieEntity>(safePageSize)
    var cursor = ""
    while (true) {
        val page = fetchPage(cursor, safePageSize)
        if (page.isEmpty()) break
        out.addAll(page)
        val nextCursor = page.last().sahabId
        if (page.size < safePageSize || nextCursor.isEmpty() || nextCursor <= cursor) break
        cursor = nextCursor
    }
    return out
}

data class MediaFlowStats(
    val total: Int = 0,
    val enriched: Int = 0,
    val pending: Int = 0,
    val failed: Int = 0,
    val withTmdb: Int = 0,
)

data class CatalogFacetRow(
    val novaStreamId: String,
    val year: String,
    val genre: String,
    val cast: String,
    val rating: String,
    val novaAdded: Long,
    val tmdbVoteCount: Int? = null,
    val titleAr: String = "",
    val titleEn: String = "",
    val identifiedTitle: String = "",
    val sahabId: String = "",
)

data class CategoryCountRow(val categoryId: String, val cnt: Int)
