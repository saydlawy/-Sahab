package com.saydlawy.sahab.data.central

import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.Prefs
import com.saydlawy.sahab.data.auth.AuthManager
import com.saydlawy.sahab.data.catalog.CatalogMovieDao
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.forAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Room-backed local adapter implementing [CentralCatalogProvider] at zero network cost. */
class LocalRoomCentralCatalogAdapter(
    private val dao: CatalogMovieDao,
) : CentralCatalogProvider {

    override suspend fun getByContentId(id: CentralContentId): CentralCatalogItem? = withContext(Dispatchers.IO) {
        val byCanonical = dao.findById(id.canonical)
        val entity = byCanonical ?: if (id.scheme == "pending" || id.scheme == "series_pending") {
            dao.findByStreamIdAndType(id.rawId, id.mediaType)
        } else {
            null
        }
        entity?.toCentralItem()
    }

    override suspend fun searchPaged(
        query: String,
        mediaType: String,
        limit: Int,
        offset: Int,
    ): List<CentralCatalogItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val entities = if (cleanQuery.isBlank()) {
            dao.getPaged(mediaType = mediaType, limit = limit, offset = offset)
        } else {
            dao.searchCatalogPaged(query = cleanQuery, mediaType = mediaType, limit = limit, offset = offset)
        }
        entities.map { it.toCentralItem() }
    }

    override suspend fun countItems(mediaType: String): Int = withContext(Dispatchers.IO) {
        dao.countByType(mediaType)
    }

    private fun CatalogMovieEntity.toCentralItem(): CentralCatalogItem = CentralCatalogItem(
        contentId = CentralContentId.parse(sahabId, mediaType),
        streamId = novaStreamId,
        titleAr = titleAr,
        titleEn = titleEn,
        year = year,
        plot = plot,
        genre = genre,
        rating = rating,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        tmdbId = tmdbId,
        updatedAtMs = lastEnriched,
    )
}

/** Local Prefs + optional Firebase Auth adapter for [CentralIdentityProvider]. */
class LocalCentralIdentityAdapter(
    private val prefs: Prefs,
    private val authManager: AuthManager? = null,
    private val flags: CentralFeatureFlags = CentralFeatureFlags(),
) : CentralIdentityProvider {

    override suspend fun currentAccountId(): String = prefs.currentActiveAccountId()

    override val isSignedInCloud: Boolean
        get() = authManager?.currentUser != null

    override val isCatalogAdmin: Boolean
        get() = authManager?.isAdmin == true

    override fun featureFlags(): CentralFeatureFlags = flags.copy(
        zeroCostMode = true,
        paidTierBillingAllowed = false,
    )
}

/** Local zero-cost adapter implementing [CentralSyncBridge] over DataStore and repository stream builders. */
class LocalCentralSyncBridgeAdapter(
    private val prefs: Prefs,
    private val resolveStreamUrl: (PlayItem) -> String = { it.url },
) : CentralSyncBridge {

    override suspend fun buildEnvelope(): CentralSyncEnvelope {
        val accountId = prefs.currentActiveAccountId()
        val deltaCursor = prefs.lastCloudDeltaSyncTime.first()
        val favCount = prefs.favorites.first().forAccount(accountId).size
        val recentCount = prefs.recents.first().forAccount(accountId).size
        return CentralSyncEnvelope(
            accountId = accountId,
            deltaCursorMs = deltaCursor,
            favoriteCount = favCount,
            recentCount = recentCount,
            sourceMode = "local-offline-first",
        )
    }

    override suspend fun resolvePlaybackGrant(item: PlayItem, resumeMs: Long): CentralPlaybackGrant =
        CentralPlaybackGrant(
            streamUrl = resolveStreamUrl(item),
            containerExt = item.ext,
            isLive = item.kind == Kind.LIVE,
            resumePositionMs = resumeMs.coerceAtLeast(0L),
            sourceLabel = if (item.url.isNotBlank()) "direct-url" else "local-xtream",
            costUsd = 0.0,
        )
}

/** No-op strictly zero-cost telemetry implementation for offline and free-tier compliance. */
object NoOpZeroCostTelemetrySink : CentralTelemetrySink {
    override fun recordEvent(name: String, attributes: Map<String, String>) = Unit
    override fun totalEstimatedCostUsd(): Double = 0.0
}

/** Deterministic offline matcher classifying match confidence per Sahab Central rules. */
object LocalThresholdContentMatcher : CentralContentMatcher {
    override fun evaluateMatch(
        connectionId: String,
        privateStreamId: String,
        mediaType: String,
        candidateTmdbId: Int?,
        confidencePercent: Int,
        reviewedByAdmin: Boolean,
    ): CentralContentMapping {
        val bounded = confidencePercent.coerceIn(0, 100)
        val tier = when {
            candidateTmdbId == null -> CentralMatchTier.UNMATCHED
            bounded >= 98 || reviewedByAdmin -> CentralMatchTier.AUTO_MATCH
            bounded >= 85 -> CentralMatchTier.ADMIN_REVIEW
            else -> CentralMatchTier.UNMATCHED
        }
        val globalId = if (candidateTmdbId != null && tier != CentralMatchTier.UNMATCHED) {
            CentralContentId.fromTmdb(candidateTmdbId, mediaType)
        } else {
            CentralContentId.fromStream(privateStreamId, mediaType)
        }
        return CentralContentMapping(
            connectionId = connectionId,
            privateStreamId = privateStreamId,
            mediaType = mediaType,
            globalContentId = globalId,
            confidencePercent = bounded,
            tier = tier,
            reviewedByAdmin = reviewedByAdmin,
        )
    }
}

/** Local Room-backed provider connector stub for zero-cost integration and unit testing. */
class LocalOfflineProviderConnector(
    override val providerType: String = "xtream-local",
    private val dao: CatalogMovieDao,
) : ProviderConnector {
    override suspend fun fetchCatalogVersion(connection: CentralConnectionDescriptor): CentralCatalogVersion =
        withContext(Dispatchers.IO) {
            val count = dao.countByType("movie") + dao.countByType("series")
            connection.version.copy(
                itemCount = count,
                updatedAtMs = connection.version.updatedAtMs.coerceAtLeast(1L),
            )
        }

    override suspend fun fetchDelta(
        connection: CentralConnectionDescriptor,
        sinceGenerationId: Long,
    ): CentralDeltaBatchDto = CentralDeltaBatchDto(
        baseGenerationId = sinceGenerationId,
        nextGenerationId = connection.version.generationId.coerceAtLeast(sinceGenerationId),
        upserts = emptyList(),
        deletedContentIds = emptyList(),
        completedCleanly = true,
    )
}
