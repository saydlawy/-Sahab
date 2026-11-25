package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType

enum class EmbedServer(val label: String) {
    VIDLINK("VidLink"),
    VIDSRC("VidSrc"),
    EMBED_SU("Embed.su"),
    AUTOEMBED("AutoEmbed"),
    TWO_EMBED("2Embed");

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
            EMBED_SU -> if (isTv) {
                "https://embed.su/embed/tv/$tmdbId/$s/$e"
            } else {
                "https://embed.su/embed/movie/$tmdbId"
            }
            AUTOEMBED -> if (isTv) {
                "https://autoembed.sbs/embed/tv/$tmdbId/$s/$e/"
            } else {
                "https://autoembed.sbs/embed/movie/$tmdbId/"
            }
            TWO_EMBED -> if (isTv) {
                "https://www.2embed.cc/embedtv/$tmdbId&s=$s&e=$e"
            } else {
                "https://www.2embed.cc/embed/$tmdbId"
            }
        }
    }
}
