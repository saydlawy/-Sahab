package com.saydlawy.sahab.data

import kotlinx.serialization.Serializable

object Kind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val SERIES = "series"
}

data class Account(val server: String, val user: String, val pass: String)

data class UserInfo(
    val status: String,
    val expiry: Long?,
    val maxConnections: String,
    val activeConnections: String,
    val isTrial: Boolean,
    val formats: List<String>,
)

data class Category(val id: String, val name: String)

data class Channel(
    val id: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val epgId: String,
)

/** A movie or a series shown as a poster in a grid. */
data class Poster(
    val id: String,
    val name: String,
    val image: String,
    val rating: String,
    val ext: String,
    val year: String,
)

data class MovieDetails(
    val id: String,
    val name: String,
    val image: String,
    val backdrop: String,
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val year: String,
    val duration: String,
    val rating: String,
    val ext: String,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val image: String,
    val plot: String,
    val duration: String,
)

data class SeriesDetails(
    val id: String,
    val name: String,
    val cover: String,
    val backdrop: String,
    val plot: String,
    val cast: String,
    val genre: String,
    val year: String,
    val rating: String,
    val episodes: List<Episode>,
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
}

/** Program times are Unix seconds. */
data class Program(
    val title: String,
    val description: String,
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
