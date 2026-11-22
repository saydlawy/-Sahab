package com.saydlawy.sahab.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.saydlawy.sahab.util.normalizeForSearch
import kotlinx.coroutines.flow.Flow

internal const val CATALOG_ENRICHED_PAGE_SIZE = 200
internal const val CATALOG_SYNC_SNAPSHOT_PAGE_SIZE = 500
private const val PROVIDER_ROW_CONDITION =
    "novaStreamId != '' AND novaStreamId NOT LIKE '$CLOUD_REF_STREAM_PREFIX%'"

@Dao
interface CatalogMovieDao {

    @Query("SELECT * FROM catalog_movies WHERE sahabId = :id")
    suspend fun findById(id: String): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE sahabId IN (:ids) AND deleted = 0")
    suspend fun findByIds(ids: List<String>): List<CatalogMovieEntity>

    @Query("SELECT * FROM catalog_movies WHERE tmdbId IN (:tmdbIds) AND deleted = 0")
    suspend fun findByTmdbIds(tmdbIds: List<Int>): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND tmdbId = :tmdbId AND plot != '' " +
            "LIMIT 1",
    )
    suspend fun findBestEnrichedByTmdbId(tmdbId: Int, mediaType: String): CatalogMovieEntity?

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND tmdbId IS NOT NULL AND tmdbId > 0 AND plot != '' " +
            "AND (:cleanTitle != '' AND (searchTitle = :cleanTitle OR searchTitle LIKE '%' || :cleanTitle || '%')) " +
            "LIMIT 10",
    )
    suspend fun findEnrichedCandidatesBySearchTitle(cleanTitle: String, mediaType: String): List<CatalogMovieEntity>

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId = :streamId AND mediaType = :mediaType LIMIT 1")
    suspend fun findByStreamId(streamId: String, mediaType: String = "movie"): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId = :streamId AND mediaType = :mediaType AND deleted = 0 LIMIT 1")
    suspend fun findByStreamIdAndType(streamId: String, mediaType: String): CatalogMovieEntity?

    @Query("SELECT * FROM catalog_movies WHERE novaStreamId IN (:streamIds) AND mediaType = :mediaType AND deleted = 0")
    suspend fun findByStreamIds(streamIds: List<String>, mediaType: String = "movie"): List<CatalogMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogMovieEntity>)

    /**
     * Merges a batch of `global_catalog` cloud documents into local Room rows without ever
     * replacing user IPTV provider identifiers (`novaStreamId`, `categoryId`, `providerOrder`,
     * `containerExtension`, `titleAr`, `posterUrl`, or locally cached `episodesJson`).
     * Unmatched cloud documents are stored with a unique `__cloud__:` sentinel streamId so they
     * act as local enrichment references without violating `UNIQUE(mediaType, novaStreamId)` or
     * appearing as phantom items in browse/search queries.
     */
    @Transaction
    suspend fun mergeCloudBatchPreservingLocal(cloudItems: List<CatalogMovieEntity>) {
        mergeCloudBatchPreservingLocalWithDao(cloudItems)
    }

    @Query(
        "UPDATE catalog_movies SET lastSeen = :now, deleted = 0 " +
            "WHERE novaStreamId IN (:streamIds) AND mediaType = :mediaType",
    )
    suspend fun touchBulk(streamIds: List<String>, now: Long, mediaType: String = "movie"): Int

    @Query(
        "UPDATE catalog_movies SET " +
            "categoryId = :categoryId, " +
            "titleAr = :titleAr, " +
            "posterUrl = :posterUrl, " +
            "containerExtension = :containerExtension, " +
            "novaAdded = :added, " +
            "providerOrder = :providerOrder, " +
            "searchTitle = :searchTitle, " +
            "lastSeen = :now, " +
            "deleted = 0 " +
            "WHERE novaStreamId = :streamId AND mediaType = :mediaType",
    )
    suspend fun updateProviderFields(
        streamId: String,
        mediaType: String,
        categoryId: String,
        titleAr: String,
        posterUrl: String,
        containerExtension: String,
        added: Long,
        providerOrder: Int,
        searchTitle: String,
        now: Long,
    ): Int

    @Transaction
    suspend fun applySyncBatch(
        unchangedIds: List<String>,
        updatedFields: List<ProviderSyncUpdate>,
        newEntities: List<CatalogMovieEntity>,
        now: Long,
        mediaType: String,
    ) {
        if (unchangedIds.isNotEmpty()) {
            unchangedIds.chunked(500).forEach { chunk ->
                touchBulk(chunk, now, mediaType)
            }
        }
        for (u in updatedFields) {
            updateProviderFields(
                streamId = u.streamId,
                mediaType = mediaType,
                categoryId = u.categoryId,
                titleAr = u.titleAr,
                posterUrl = u.posterUrl,
                containerExtension = u.containerExtension,
                added = u.added,
                providerOrder = u.providerOrder,
                searchTitle = u.searchTitle,
                now = now,
            )
        }
        if (newEntities.isNotEmpty()) {
            upsertAll(newEntities)
        }
    }

    @Query(
        "SELECT novaStreamId, categoryId, titleAr, titleEn, identifiedTitle, posterUrl, " +
            "containerExtension, providerOrder, searchTitle, deleted " +
            "FROM catalog_movies " +
            "WHERE mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "AND novaStreamId > :afterStreamId " +
            "ORDER BY novaStreamId ASC LIMIT :limit",
    )
    suspend fun getProviderSyncSnapshotPage(
        mediaType: String,
        afterStreamId: String = "",
        limit: Int = CATALOG_SYNC_SNAPSHOT_PAGE_SIZE,
    ): List<ProviderSyncSnapshotRow>

    @Query(
        "UPDATE catalog_movies SET deleted = 1 " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION AND lastSeen < :cutoff",
    )
    suspend fun softDeleteBefore(cutoff: Long, mediaType: String = "movie"): Int

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "AND needsEnrichment = 1 ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit",
    )
    suspend fun pendingEnrichment(limit: Int, mediaType: String = "movie"): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND $PROVIDER_ROW_CONDITION AND needsEnrichment = 1")
    suspend fun countPendingEnrichment(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND $PROVIDER_ROW_CONDITION")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND $PROVIDER_ROW_CONDITION")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND $PROVIDER_ROW_CONDITION AND needsEnrichment = 0")
    suspend fun countEnriched(): Int

    @Query(
        "SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType " +
            "AND $PROVIDER_ROW_CONDITION AND (providerOrder = 2147483647 OR searchTitle = '')",
    )
    suspend fun countMissingProviderOrderOrSearch(mediaType: String): Int

    @Query(
        "SELECT * FROM catalog_movies WHERE deleted = 0 AND $PROVIDER_ROW_CONDITION " +
            "AND tmdbId IS NOT NULL AND tmdbId > 0 " +
            "AND sahabId > :afterSahabId ORDER BY sahabId ASC LIMIT :limit",
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
            "searchTitle = CASE WHEN :searchTitle != '' THEN :searchTitle ELSE searchTitle END, " +
            "tmdbVoteCount = :voteCount, needsEnrichment = :status, lastEnriched = :now, " +
            "deleted = 0, lastSeen = :now, needsCloudPush = :needsCloudPush, updatedAt = :now " +
            "WHERE novaStreamId = :streamId AND mediaType = :mediaType",
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
        searchTitle: String = "",
        needsCloudPush: Int = 1,
    )

    @Query("SELECT novaStreamId FROM catalog_movies WHERE mediaType = :mediaType AND $PROVIDER_ROW_CONDITION")
    suspend fun allStreamIds(mediaType: String = "movie"): List<String>

    @Query("UPDATE catalog_movies SET needsEnrichment = -1, lastEnriched = :now WHERE novaStreamId = :streamId AND mediaType = :mediaType")
    suspend fun markFailed(streamId: String, now: Long, mediaType: String = "movie")

    @Query(
        "UPDATE catalog_movies SET " +
            "plot = :plot, plotSource = :source, plotConfidence = :confidence, " +
            "identifiedTitle = :identifiedTitle, " +
            "searchTitle = CASE WHEN :searchTitle != '' THEN :searchTitle ELSE searchTitle END, " +
            "lastEnriched = :now, " +
            "needsCloudPush = :needsCloudPush, updatedAt = :now " +
            "WHERE sahabId = :sahabId",
    )
    suspend fun updateAiPlot(
        sahabId: String,
        plot: String,
        source: String,
        confidence: String,
        identifiedTitle: String,
        now: Long,
        searchTitle: String = "",
        needsCloudPush: Int = 1,
    )

    @Query(
        "UPDATE catalog_movies SET episodesJson = :episodesJson, lastSeen = :now " +
            "WHERE novaStreamId = :streamId AND mediaType = 'series'",
    )
    suspend fun updateSeriesEpisodes(streamId: String, episodesJson: String, now: Long)

    @Query(
        "UPDATE catalog_movies SET containerExtension = :ext, lastSeen = :now " +
            "WHERE novaStreamId = :streamId AND mediaType = 'movie' AND :ext != ''",
    )
    suspend fun updateMovieExtension(streamId: String, ext: String, now: Long)

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "AND ((:normalizedQuery != '' AND searchTitle LIKE '%' || :normalizedQuery || '%') " +
            "OR titleAr LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit",
    )
    suspend fun searchCatalog(
        query: String,
        normalizedQuery: String = query.normalizeForSearch(),
        mediaType: String = "movie",
        limit: Int = 100,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun getPaged(mediaType: String = "movie", limit: Int, offset: Int = 0): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "AND (providerOrder > :lastOrder OR (providerOrder = :lastOrder AND sahabId > :lastSahabId)) " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit",
    )
    suspend fun getPagedAfter(
        mediaType: String = "movie",
        lastOrder: Int,
        lastSahabId: String,
        limit: Int,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND categoryId = :catId AND $PROVIDER_ROW_CONDITION " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun getPagedByCategory(catId: String, mediaType: String = "movie", limit: Int, offset: Int = 0): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND categoryId = :catId AND $PROVIDER_ROW_CONDITION " +
            "AND (providerOrder > :lastOrder OR (providerOrder = :lastOrder AND sahabId > :lastSahabId)) " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit",
    )
    suspend fun getPagedByCategoryAfter(
        catId: String,
        mediaType: String = "movie",
        lastOrder: Int,
        lastSahabId: String,
        limit: Int,
    ): List<CatalogMovieEntity>

    @Query(
        "SELECT * FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION " +
            "AND ((:normalizedQuery != '' AND searchTitle LIKE '%' || :normalizedQuery || '%') " +
            "OR titleAr LIKE '%' || :query || '%' OR titleEn LIKE '%' || :query || '%') " +
            "ORDER BY providerOrder ASC, sahabId ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchCatalogPaged(
        query: String,
        normalizedQuery: String = query.normalizeForSearch(),
        mediaType: String = "movie",
        limit: Int = 100,
        offset: Int = 0,
    ): List<CatalogMovieEntity>

    @Query("SELECT COUNT(*) FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION")
    suspend fun countByType(mediaType: String = "movie"): Int

    @Query(
        "SELECT categoryId, COUNT(*) AS cnt FROM catalog_movies " +
            "WHERE deleted = 0 AND mediaType = :mediaType AND categoryId != '' AND $PROVIDER_ROW_CONDITION " +
            "GROUP BY categoryId",
    )
    suspend fun getCategoryCounts(mediaType: String = "movie"): List<CategoryCountRow>

    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = 0 THEN 1 ELSE 0 END), 0) AS enriched, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = 1 THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN needsEnrichment = -1 THEN 1 ELSE 0 END), 0) AS failed, " +
            "COALESCE(SUM(CASE WHEN tmdbId IS NOT NULL AND tmdbId > 0 THEN 1 ELSE 0 END), 0) AS withTmdb " +
            "FROM catalog_movies WHERE deleted = 0 AND mediaType = :mediaType AND $PROVIDER_ROW_CONDITION",
    )
    suspend fun getMediaFlowStats(mediaType: String): MediaFlowStats

    @Query("UPDATE catalog_movies SET needsEnrichment = 1 WHERE deleted = 0 AND needsEnrichment = -1 AND $PROVIDER_ROW_CONDITION")
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

suspend fun CatalogMovieDao.loadAllProviderSyncSnapshot(
    mediaType: String,
    pageSize: Int = CATALOG_SYNC_SNAPSHOT_PAGE_SIZE,
): Map<String, ProviderSyncSnapshotRow> {
    val safePageSize = pageSize.coerceAtLeast(1)
    val out = HashMap<String, ProviderSyncSnapshotRow>(safePageSize)
    var cursor = ""
    while (true) {
        val page = getProviderSyncSnapshotPage(
            mediaType = mediaType,
            afterStreamId = cursor,
            limit = safePageSize,
        )
        if (page.isEmpty()) break
        for (row in page) {
            out[row.novaStreamId] = row
        }
        val nextCursor = page.last().novaStreamId
        if (page.size < safePageSize || nextCursor.isEmpty() || nextCursor <= cursor) break
        cursor = nextCursor
    }
    return out
}

internal suspend fun CatalogMovieDao.mergeCloudBatchPreservingLocalWithDao(
    cloudItems: List<CatalogMovieEntity>,
) {
    if (cloudItems.isEmpty()) return
    val existingBySahabId = HashMap<String, CatalogMovieEntity>(cloudItems.size)
    cloudItems.map { it.sahabId }.distinct().chunked(500).forEach { idChunk ->
        findByIds(idChunk).forEach { existingBySahabId[it.sahabId] = it }
    }
    val existingByTmdbId = HashMap<Int, MutableList<CatalogMovieEntity>>(cloudItems.size)
    cloudItems.mapNotNull { it.tmdbId?.takeIf { tid -> tid > 0 } }.distinct().chunked(500).forEach { tmdbChunk ->
        findByTmdbIds(tmdbChunk).forEach { row ->
            row.tmdbId?.let { tid -> existingByTmdbId.getOrPut(tid) { mutableListOf() }.add(row) }
        }
    }

    val toUpsert = ArrayList<CatalogMovieEntity>(cloudItems.size)
    val queuedSahabIds = HashSet<String>(cloudItems.size)

    for (cloud in cloudItems) {
        val matchedLocals = buildList {
            existingBySahabId[cloud.sahabId]?.let { add(it) }
            cloud.tmdbId?.takeIf { it > 0 }?.let { tid ->
                existingByTmdbId[tid]?.forEach { row ->
                    if (row.sahabId != cloud.sahabId && row.mediaType == cloud.mediaType) {
                        add(row)
                    }
                }
            }
        }.distinctBy { it.sahabId }

        if (matchedLocals.isEmpty()) {
            if (queuedSahabIds.add(cloud.sahabId)) {
                toUpsert += cloud.copy(
                    novaStreamId = CatalogMovieEntity.cloudRefStreamId(cloud.sahabId),
                    categoryId = "",
                    providerOrder = Int.MAX_VALUE,
                    episodesJson = "",
                    novaAdded = 0L,
                    needsCloudPush = 0,
                    searchTitle = buildCatalogSearchTitle(cloud.titleAr, cloud.titleEn, cloud.identifiedTitle),
                )
            }
        } else {
            for (local in matchedLocals) {
                if (!queuedSahabIds.add(local.sahabId)) continue
                if (local.needsCloudPush == 1 && local.updatedAt >= cloud.updatedAt) {
                    continue
                }
                val keepLocalAiPlot = local.plotSource == "gemini_user_key" && local.plot.isNotBlank()
                val mergedTitleAr = local.titleAr.ifBlank { cloud.titleAr }
                val mergedTitleEn = cloud.titleEn.ifBlank { local.titleEn }
                val mergedIdentified = if (keepLocalAiPlot) local.identifiedTitle else cloud.identifiedTitle.ifBlank { local.identifiedTitle }
                toUpsert += cloud.copy(
                    sahabId = local.sahabId,
                    novaStreamId = local.novaStreamId,
                    categoryId = local.categoryId,
                    titleAr = mergedTitleAr,
                    titleEn = mergedTitleEn,
                    year = cloud.year.ifBlank { local.year },
                    plot = if (keepLocalAiPlot) local.plot else cloud.plot.ifBlank { local.plot },
                    plotSource = if (keepLocalAiPlot) local.plotSource else cloud.plotSource.ifBlank { local.plotSource },
                    plotConfidence = if (keepLocalAiPlot) local.plotConfidence else cloud.plotConfidence.ifBlank { local.plotConfidence },
                    identifiedTitle = mergedIdentified,
                    mediaType = local.mediaType,
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
                    novaAdded = local.novaAdded,
                    containerExtension = local.containerExtension.ifBlank { cloud.containerExtension },
                    tmdbId = cloud.tmdbId ?: local.tmdbId,
                    tmdbVoteCount = cloud.tmdbVoteCount ?: local.tmdbVoteCount,
                    deleted = local.deleted,
                    lastSeen = local.lastSeen,
                    needsCloudPush = 0,
                    updatedAt = maxOf(local.updatedAt, cloud.updatedAt),
                    providerOrder = local.providerOrder,
                    episodesJson = local.episodesJson,
                    searchTitle = buildCatalogSearchTitle(mergedTitleAr, mergedTitleEn, mergedIdentified),
                )
            }
        }
    }
    if (toUpsert.isNotEmpty()) {
        upsertAll(toUpsert)
    }
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

data class ProviderSyncSnapshotRow(
    val novaStreamId: String,
    val categoryId: String,
    val titleAr: String,
    val titleEn: String,
    val identifiedTitle: String,
    val posterUrl: String,
    val containerExtension: String,
    val providerOrder: Int,
    val searchTitle: String,
    val deleted: Int,
)

data class ProviderSyncUpdate(
    val streamId: String,
    val categoryId: String,
    val titleAr: String,
    val posterUrl: String,
    val containerExtension: String,
    val added: Long,
    val providerOrder: Int,
    val searchTitle: String,
)

data class CategoryCountRow(val categoryId: String, val cnt: Int)
