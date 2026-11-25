package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType

enum class EmbedServer(val label: String) {
    VIDLINK("VidLink"),
    VIDSRC("VidSrc");

    fun buildUrl(
        defaultVidLinkUrl: String,
        tmdbId: Int,
        mediaType: InstantMediaType,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): String {
        val s = seasonNumber ?: 1
        val e = episodeNumber ?: 1
        val isTv = mediaType == InstantMediaType.TV
        return when (this) {
            VIDLINK -> defaultVidLinkUrl
            VIDSRC -> if (isTv) {
                "https://vidsrc.cc/v2/embed/tv/$tmdbId/$s/$e"
            } else {
                "https://vidsrc.cc/v2/embed/movie/$tmdbId"
            }
        }
    }
}
