package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.SubtitleQuery
import com.saydlawy.sahab.feature.instantwatch.data.SubtitleRepository
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchPreferences

data class ResolvedSubtitleTrack(
    val url: String,
    val label: String = "العربية",
    val language: String = "ar",
    val mimeType: String = "application/x-subrip",
    val fileName: String = "",
)

class SubtitleManager(
    private val preferences: InstantWatchPreferences,
    private val subtitleRepository: SubtitleRepository,
) {
    suspend fun resolveArabicSubtitle(
        item: InstantMediaItem,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        releaseHint: String? = null,
    ): ResolvedSubtitleTrack? {
        if (!preferences.settings.value.autoArabicSubtitles) return null

        val query = SubtitleQuery(
            tmdbId = item.tmdbId,
            originalTitle = item.originalTitle,
            releaseYear = item.releaseYear,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            language = "ar",
            releaseHint = releaseHint,
        )

        val candidates = subtitleRepository.searchArabicSubtitles(query)
        val best = candidates.firstOrNull() ?: return null
        val downloadUrl = subtitleRepository.requestDownloadLink(best.fileId) ?: return null

        val mimeType = when {
            best.fileName.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
            best.fileName.endsWith(".ass", ignoreCase = true) ||
                best.fileName.endsWith(".ssa", ignoreCase = true) -> "text/x-ssa"
            else -> "application/x-subrip"
        }

        return ResolvedSubtitleTrack(
            url = downloadUrl,
            label = "العربية",
            language = "ar",
            mimeType = mimeType,
            fileName = best.fileName,
        )
    }
}
