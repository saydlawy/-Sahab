package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchDao
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchHistoryEntity
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchPreferences
import kotlinx.coroutines.flow.Flow

data class PlaybackSessionRequest(
    val item: InstantMediaItem,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val directAuthorizedStreamUrl: String? = null,
)

sealed class OrchestrationResult {
    data class Ready(
        val item: InstantMediaItem,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val source: ResolvedPlaybackSource,
        val resumePositionMs: Long,
    ) : OrchestrationResult()

    data class Denied(val gate: GateResult) : OrchestrationResult()
    data class Error(val message: String) : OrchestrationResult()
}

class PlaybackOrchestrator(
    private val authGate: AuthenticationGate,
    private val streamResolver: StreamResolver,
    private val subtitleManager: SubtitleManager,
    private val historyDao: InstantWatchDao,
    private val preferences: InstantWatchPreferences,
) {
    fun observeRecentHistory(limit: Int = 20): Flow<List<InstantWatchHistoryEntity>> =
        historyDao.observeRecent(limit)

    suspend fun preparePlayback(request: PlaybackSessionRequest): OrchestrationResult {
        val gate = authGate.verifyAccess()
        if (gate !is GateResult.Authorized) {
            return OrchestrationResult.Denied(gate)
        }

        if (request.item.mediaType == InstantMediaType.TV &&
            (request.seasonNumber == null || request.episodeNumber == null)
        ) {
            return OrchestrationResult.Error("يرجى اختيار الموسم والحلقة أولاً.")
        }

        val subtitle = subtitleManager.resolveArabicSubtitle(
            item = request.item,
            seasonNumber = request.seasonNumber,
            episodeNumber = request.episodeNumber,
        )

        val resolved = streamResolver.resolve(
            item = request.item,
            seasonNumber = request.seasonNumber,
            episodeNumber = request.episodeNumber,
            subtitle = subtitle,
            directAuthorizedStreamUrl = request.directAuthorizedStreamUrl,
        )

        if (resolved is ResolvedPlaybackSource.Rejected) {
            return OrchestrationResult.Error(resolved.reason)
        }

        val saved = historyDao.getByKey(request.item.contentKey)
        val resumePos = if (
            saved != null &&
            saved.seasonNumber == request.seasonNumber &&
            saved.episodeNumber == request.episodeNumber
        ) {
            saved.positionMs
        } else {
            0L
        }

        saveProgress(
            item = request.item,
            seasonNumber = request.seasonNumber,
            episodeNumber = request.episodeNumber,
            positionMs = resumePos,
            durationMs = saved?.durationMs ?: 0L,
        )

        if (preferences.settings.value.saveProgressLocally) {
            historyDao.upsert(
                InstantWatchHistoryEntity(
                    contentKey = request.item.contentKey,
                    tmdbId = request.item.tmdbId,
                    imdbId = request.item.imdbId,
                    mediaType = if (request.item.mediaType == InstantMediaType.TV) "tv" else "movie",
                    title = request.item.title,
                    originalTitle = request.item.originalTitle,
                    releaseYear = request.item.releaseYear,
                    posterUrl = request.item.posterUrl,
                    seasonNumber = request.seasonNumber,
                    episodeNumber = request.episodeNumber,
                    positionMs = resumePos,
                    durationMs = saved?.durationMs ?: 0L,
                    updatedAtMs = System.currentTimeMillis(),
                )
            )
        }

        return OrchestrationResult.Ready(
            item = request.item,
            seasonNumber = request.seasonNumber,
            episodeNumber = request.episodeNumber,
            source = resolved,
            resumePositionMs = resumePos,
        )
    }

    suspend fun saveProgress(
        item: InstantMediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (!preferences.isEnabled || !preferences.settings.value.saveProgressLocally) return
        historyDao.upsert(
            InstantWatchHistoryEntity(
                contentKey = item.contentKey,
                tmdbId = item.tmdbId,
                imdbId = item.imdbId,
                mediaType = item.mediaType.apiValue,
                title = item.title,
                originalTitle = item.originalTitle,
                releaseYear = item.releaseYear,
                posterUrl = item.posterUrl,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }
}
