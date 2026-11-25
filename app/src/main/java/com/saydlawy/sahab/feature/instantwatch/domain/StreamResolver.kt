package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.VidLinkRepository
import com.saydlawy.sahab.feature.instantwatch.security.RequestCategory
import com.saydlawy.sahab.feature.instantwatch.security.RequestInspector
import com.saydlawy.sahab.feature.instantwatch.security.StreamSecurityPolicy

sealed class ResolvedPlaybackSource {
    data class NativeMedia3(
        val streamUrl: String,
        val subtitle: ResolvedSubtitleTrack? = null,
    ) : ResolvedPlaybackSource()

    data class IsolatedEmbed(
        val embedUrl: String,
        val subtitle: ResolvedSubtitleTrack? = null,
    ) : ResolvedPlaybackSource()

    data class Rejected(val reason: String) : ResolvedPlaybackSource()
}

class StreamResolver(
    private val vidLinkRepository: VidLinkRepository,
    private val securityPolicy: StreamSecurityPolicy,
    private val requestInspector: RequestInspector,
) {
    /**
     * Resolves and validates a playback source through the 5 pipeline stages:
     * 1) Source Acquisition
     * 2) Request Inspection
     * 3) Security Validation
     * 4) Stream Resolution (direct stream only if explicitly provided & allowed)
     * 5) Playback Handoff (Media3 vs Isolated WebView)
     */
    fun resolve(
        item: InstantMediaItem,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        subtitle: ResolvedSubtitleTrack? = null,
        directAuthorizedStreamUrl: String? = null,
    ): ResolvedPlaybackSource {
        // Optional direct stream path ONLY when a source legitimately provides one
        if (!directAuthorizedStreamUrl.isNullOrBlank()) {
            val check = securityPolicy.validateUrl(directAuthorizedStreamUrl, requireTrustedHost = false)
            if (!check.isAllowed) {
                return ResolvedPlaybackSource.Rejected("تم رفض مصدر الفيديو المباشر: ${check.reason}")
            }
            val inspected = requestInspector.inspect(directAuthorizedStreamUrl, isForMainFrame = false)
            if (inspected.allowed && inspected.category == RequestCategory.VIDEO_STREAM) {
                return ResolvedPlaybackSource.NativeMedia3(
                    streamUrl = directAuthorizedStreamUrl,
                    subtitle = subtitle,
                )
            }
        }

        // Standard VidLink isolated embed path (no raw stream scraping / no DRM bypass)
        val target = vidLinkRepository.buildEmbedTarget(
            tmdbId = item.tmdbId,
            mediaType = item.mediaType,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            subtitleUrl = subtitle?.url,
            subtitleLabel = subtitle?.label ?: "العربية",
        ) ?: return ResolvedPlaybackSource.Rejected("تعذر بناء رابط التشغيل الآمن لهذا العمل.")

        val embedCheck = securityPolicy.validateUrl(target.embedUrl, requireTrustedHost = true)
        if (!embedCheck.isAllowed) {
            return ResolvedPlaybackSource.Rejected("تم رفض مصدر التشغيل لعدم توافقه مع سياسة الحماية: ${embedCheck.reason}")
        }

        return ResolvedPlaybackSource.IsolatedEmbed(
            embedUrl = target.embedUrl,
            subtitle = subtitle,
        )
    }
}
