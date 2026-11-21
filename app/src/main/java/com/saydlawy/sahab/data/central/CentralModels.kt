package com.saydlawy.sahab.data.central

import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import kotlinx.serialization.Serializable

/**
 * Canonical content identity bridging legacy local IDs (`pending:`, `series_pending:`, `tmdb:`, `local:`)
 * with future Sahab Central Platform catalogs.
 */
@Serializable
data class CentralContentId(
    val scheme: String,
    val rawId: String,
    val mediaType: String = "movie",
) {
    val canonical: String get() = "$scheme:$rawId"

    companion object {
        fun parse(sahabId: String, mediaType: String = "movie"): CentralContentId {
            val trimmed = sahabId.trim()
            val idx = trimmed.indexOf(':')
            return if (idx > 0 && idx < trimmed.lastIndex) {
                val scheme = trimmed.substring(0, idx)
                val raw = trimmed.substring(idx + 1)
                val resolvedType = if (scheme == "series_pending" || mediaType.equals("series", ignoreCase = true)) {
                    "series"
                } else {
                    mediaType.ifBlank { "movie" }
                }
                CentralContentId(scheme = scheme, rawId = raw, mediaType = resolvedType)
            } else {
                CentralContentId(
                    scheme = if (mediaType.equals("series", ignoreCase = true)) "series_pending" else "pending",
                    rawId = trimmed,
                    mediaType = mediaType.ifBlank { "movie" },
                )
            }
        }

        fun fromTmdb(tmdbId: Int, mediaType: String = "movie"): CentralContentId =
            CentralContentId(
                scheme = "tmdb",
                rawId = tmdbId.toString(),
                mediaType = mediaType.ifBlank { "movie" },
            )

        fun fromStream(streamId: String, mediaType: String = "movie"): CentralContentId =
            parse(CatalogMovieEntity.idPending(streamId, mediaType), mediaType)
    }
}

@Serializable
data class CentralCatalogItem(
    val contentId: CentralContentId,
    val streamId: String,
    val titleAr: String,
    val titleEn: String = "",
    val year: String = "",
    val plot: String = "",
    val genre: String = "",
    val rating: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val tmdbId: Int? = null,
    val updatedAtMs: Long = 0L,
)

@Serializable
data class CentralPlaybackGrant(
    val streamUrl: String,
    val containerExt: String = "",
    val isLive: Boolean = false,
    val resumePositionMs: Long = 0L,
    val sourceLabel: String = "local-xtream",
    val costUsd: Double = 0.0,
)

@Serializable
data class CentralSyncEnvelope(
    val accountId: String,
    val deltaCursorMs: Long,
    val favoriteCount: Int,
    val recentCount: Int,
    val sourceMode: String = "local-offline-first",
)

@Serializable
data class CentralFeatureFlags(
    val enableCentralSync: Boolean = false,
    val enableCloudCatalogWrite: Boolean = false,
    val zeroCostMode: Boolean = true,
    val paidTierBillingAllowed: Boolean = false,
)

/** Matching confidence tiers specified by Sahab Central Platform (Section 10.6). */
@Serializable
enum class CentralMatchTier {
    AUTO_MATCH,      // >= 98%
    ADMIN_REVIEW,    // 85% .. < 98%
    UNMATCHED,       // < 85%
}

/** Explicit catalog version & checksum tracking per private connection or global snapshot. */
@Serializable
data class CentralCatalogVersion(
    val schemaVersion: Int = 1,
    val generationId: Long = 0L,
    val catalogHash: String = "",
    val itemCount: Int = 0,
    val updatedAtMs: Long = 0L,
)

/** Private IPTV connection metadata scoped per user without exposing credentials in global metadata. */
@Serializable
data class CentralConnectionDescriptor(
    val connectionId: String,
    val providerType: String,
    val providerIdentifier: String,
    val credentialsRef: String,
    val syncStatus: String = "idle",
    val version: CentralCatalogVersion = CentralCatalogVersion(),
)

/** Explicit mapping between a user's private provider stream ID and a global TMDB content entity. */
@Serializable
data class CentralContentMapping(
    val connectionId: String,
    val privateStreamId: String,
    val mediaType: String,
    val globalContentId: CentralContentId? = null,
    val confidencePercent: Int = 0,
    val tier: CentralMatchTier = CentralMatchTier.UNMATCHED,
    val reviewedByAdmin: Boolean = false,
)

/** Wire-neutral snapshot & delta API DTOs for future Cloud Storage / HTTP synchronization. */
@Serializable
data class CentralCatalogSnapshotDto(
    val version: CentralCatalogVersion,
    val items: List<CentralCatalogItem>,
)

@Serializable
data class CentralDeltaBatchDto(
    val baseGenerationId: Long,
    val nextGenerationId: Long,
    val upserts: List<CentralCatalogItem>,
    val deletedContentIds: List<String> = emptyList(),
    val completedCleanly: Boolean = true,
)
