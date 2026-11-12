package com.saydlawy.sahab.data

import kotlinx.serialization.Serializable

object Kind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val SERIES = "series"
}

@Serializable
data class Account(val server: String, val user: String, val pass: String)

@Serializable
data class UserInfo(
    val status: String,
    val expiry: Long? = null,
    val maxConnections: String = "",
    val activeConnections: String = "",
    val isTrial: Boolean = false,
    val formats: List<String> = emptyList(),
)

@Serializable
data class Category(val id: String, val name: String)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val icon: String = "",
    val categoryId: String = "",
    val epgId: String = "",
    val added: Long = 0L,
)

/** A movie or a series shown as a poster in a grid. */
@Serializable
data class Poster(
    val id: String,
    val name: String,
    val image: String = "",
    val rating: String = "",
    val ext: String = "",
    val year: String = "",
    val categoryId: String = "",
    val added: Long = 0L,
)

@Serializable
data class MovieDetails(
    val id: String,
    val name: String,
    val image: String = "",
    val backdrop: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val year: String = "",
    val duration: String = "",
    val rating: String = "",
    val ext: String = "",
)

@Serializable
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String = "",
    val image: String = "",
    val plot: String = "",
    val duration: String = "",
)

@Serializable
data class SeriesDetails(
    val id: String,
    val name: String,
    val cover: String = "",
    val backdrop: String = "",
    val plot: String = "",
    val cast: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val episodes: List<Episode> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
}

/** Program times are Unix seconds. */
@Serializable
data class Program(
    val title: String,
    val description: String = "",
    val start: Long,
    val end: Long,
)

data class PlayItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: String = "",
    val ext: String = "",
    val groupId: String = "",
)

data class PlayRequest(
    val items: List<PlayItem>,
    val startIndex: Int,
    val fromStart: Boolean = false,
    val initialPositionMs: Long = -1L,
)

@Serializable
data class FavItem(
    val kind: String,
    val id: String,
    val name: String,
    val icon: String = "",
    val ext: String = "",
)

@Serializable
data class RecentItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: String = "",
    val ext: String = "",
    val groupId: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)
