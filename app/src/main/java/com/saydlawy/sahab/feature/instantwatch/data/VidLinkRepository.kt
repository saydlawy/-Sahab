package com.saydlawy.sahab.feature.instantwatch.data

import com.saydlawy.sahab.feature.instantwatch.security.StreamSecurityPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl

data class VidLinkEmbedTarget(
    val embedUrl: String,
    val tmdbId: Int,
    val mediaType: InstantMediaType,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val subtitleUrl: String? = null,
    val subtitleLabel: String? = null,
)

class VidLinkRepository(
    private val securityPolicy: StreamSecurityPolicy,
) {
    fun buildEmbedTarget(
        tmdbId: Int,
        mediaType: InstantMediaType,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        subtitleUrl: String? = null,
        subtitleLabel: String = "العربية",
    ): VidLinkEmbedTarget? {
        if (tmdbId <= 0) return null

        val basePath = when (mediaType) {
            InstantMediaType.MOVIE -> "$BASE_URL/movie/$tmdbId"
            InstantMediaType.TV -> {
                val s = seasonNumber ?: return null
                val e = episodeNumber ?: return null
                if (s <= 0 || e <= 0) return null
                "$BASE_URL/tv/$tmdbId/$s/$e"
            }
        }

        val builder = basePath.toHttpUrl().newBuilder()
            .addQueryParameter("primaryColor", "3B82F6")
            .addQueryParameter("secondaryColor", "1E293B")
            .addQueryParameter("iconColor", "FFFFFF")
            .addQueryParameter("autoplay", "true")

        val validatedSub = subtitleUrl?.trim()?.takeIf { url ->
            url.isNotEmpty() && securityPolicy.validateUrl(url, requireTrustedHost = false).isAllowed
        }
        if (validatedSub != null) {
            builder.addQueryParameter("sub_file", validatedSub)
            builder.addQueryParameter("sub_label", subtitleLabel)
        }

        val finalUrl = builder.build().toString()
        val check = securityPolicy.validateUrl(finalUrl, requireTrustedHost = true)
        if (!check.isAllowed) return null

        return VidLinkEmbedTarget(
            embedUrl = finalUrl,
            tmdbId = tmdbId,
            mediaType = mediaType,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            subtitleUrl = validatedSub,
            subtitleLabel = if (validatedSub != null) subtitleLabel else null,
        )
    }

    companion object {
        private const val BASE_URL = "https://vidlink.pro"
    }
}
