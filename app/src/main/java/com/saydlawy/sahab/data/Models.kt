package com.saydlawy.sahab.data

import kotlinx.serialization.Serializable

object Kind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val EPISODE = "episode"
    const val SERIES = "series"
}

@Serializable
enum class VodLayoutStyle(val id: String, val labelAr: String, val descAr: String) {
    CLASSIC("classic", "سحاب الكلاسيكي", "شبكة متوازنة بتصميم سحاب الأصلي"),
    PRIME("prime", "Prime Cinema", "واجهة عرض سينمائية بعرض بانورامي وتفاصيل سريعة"),
    NETFLIX("netflix", "Netflix Row", "بطل رئيسي + صفوف أفقية + قائمة أفضل ١٠"),
}

/** الامتداد الافتراضي الموحد لأفلام وحلقات VOD عند خلوه من استجابة الخادم. */
const val DEFAULT_VOD_EXT = "mp4"

@Serializable
data class Account(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "My TV",
    val type: String = "XTREAM",
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val m3uUrl: String = ""
)

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
    val url: String = "",
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
    val posterUrls: List<String> = emptyList(),
    val trailerUrl: String = "",
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
    val posterUrls: List<String> = emptyList(),
    val trailerUrl: String = "",
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

@Serializable
data class PlayItem(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: String = "",
    val ext: String = "",
    val groupId: String = "",
    val url: String = "",
)

@Serializable
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
    val url: String = "",
    val accountId: String = "",
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
    val url: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
    val accountId: String = "",
)

@JvmName("favsForAccount")
fun List<FavItem>.forAccount(accountId: String): List<FavItem> =
    if (accountId.isBlank()) filter { it.accountId.isBlank() } else filter { it.accountId == accountId }

fun List<FavItem>.isFav(accountId: String, kind: String, id: String): Boolean =
    any { it.accountId == accountId && it.kind == kind && it.id == id }

@JvmName("recentsForAccount")
fun List<RecentItem>.forAccount(accountId: String): List<RecentItem> =
    if (accountId.isBlank()) filter { it.accountId.isBlank() } else filter { it.accountId == accountId }

fun List<RecentItem>.trimPerAccount(maxPerAccount: Int = 30): List<RecentItem> =
    groupBy { it.accountId }
        .values
        .flatMap { group -> group.sortedByDescending { it.updatedAt }.take(maxPerAccount) }
        .sortedByDescending { it.updatedAt }
