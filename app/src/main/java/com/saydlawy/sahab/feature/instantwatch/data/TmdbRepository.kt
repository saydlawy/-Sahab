package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

enum class InstantMediaType(val apiValue: String) {
    MOVIE("movie"),
    TV("tv"),
}

data class InstantMediaItem(
    val tmdbId: Int,
    val mediaType: InstantMediaType,
    val title: String,
    val originalTitle: String,
    val releaseYear: Int?,
    val posterUrl: String?,
    val overview: String = "",
    val imdbId: String? = null,
    val seasons: List<InstantSeasonSummary> = emptyList(),
) {
    val contentKey: String get() = "${mediaType.apiValue}:$tmdbId"
}

data class InstantSeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
)

class TmdbRepository(
    private val client: OkHttpClient,
    private val apiKey: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun searchMulti(query: String): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()
        val url = "https://api.themoviedb.org/3/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", query.trim())
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("language", "ar-SA")
            .build()

        val body = executeGet(url.toString()) ?: return@withContext emptyList()
        val parsed = runCatching { json.decodeFromString<MultiSearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        parsed.results.mapNotNull { dto ->
            val type = when (dto.mediaType) {
                "movie" -> InstantMediaType.MOVIE
                "tv" -> InstantMediaType.TV
                else -> return@mapNotNull null
            }
            val displayTitle = (dto.title ?: dto.name).orEmpty().ifBlank { return@mapNotNull null }
            val origTitle = (dto.originalTitle ?: dto.originalName).orEmpty().ifBlank { displayTitle }
            val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull()
            InstantMediaItem(
                tmdbId = dto.id,
                mediaType = type,
                title = displayTitle,
                originalTitle = origTitle,
                releaseYear = year,
                posterUrl = dto.posterPath?.let { "$POSTER_BASE$it" },
                overview = dto.overview.orEmpty(),
            )
        }
    }

    suspend fun getDetails(tmdbId: Int, type: InstantMediaType): InstantMediaItem? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || tmdbId <= 0) return@withContext null
        val url = "https://api.themoviedb.org/3/${type.apiValue}/$tmdbId".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "ar-SA")
            .addQueryParameter("append_to_response", "external_ids")
            .build()

        val body = executeGet(url.toString()) ?: return@withContext null
        val dto = runCatching { json.decodeFromString<DetailDto>(body) }.getOrNull()
            ?: return@withContext null

        val displayTitle = (dto.title ?: dto.name).orEmpty().ifBlank { return@withContext null }
        val origTitle = (dto.originalTitle ?: dto.originalName).orEmpty().ifBlank { displayTitle }
        val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull()
        val seasons = dto.seasons.orEmpty()
            .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .map { InstantSeasonSummary(it.seasonNumber, it.name.orEmpty().ifBlank { "الموسم ${it.seasonNumber}" }, it.episodeCount) }

        InstantMediaItem(
            tmdbId = dto.id,
            mediaType = type,
            title = displayTitle,
            originalTitle = origTitle,
            releaseYear = year,
            posterUrl = dto.posterPath?.let { "$POSTER_BASE$it" },
            overview = dto.overview.orEmpty(),
            imdbId = dto.imdbId ?: dto.externalIds?.imdbId,
            seasons = seasons,
        )
    }

    suspend fun resolveByNumericId(tmdbId: Int): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<InstantMediaItem>()
        getDetails(tmdbId, InstantMediaType.MOVIE)?.let { results.add(it) }
        getDetails(tmdbId, InstantMediaType.TV)?.let { results.add(it) }
        results
    }

    internal fun executeGet(url: String): String? {
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    @Serializable
    private data class MultiSearchResponse(val results: List<SearchItemDto> = emptyList())

    @Serializable
    private data class SearchItemDto(
        val id: Int,
        @SerialName("media_type") val mediaType: String? = null,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val overview: String? = null,
    )

    @Serializable
    private data class DetailDto(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val overview: String? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
        val seasons: List<SeasonDto>? = null,
    )

    @Serializable
    private data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)

    @Serializable
    private data class SeasonDto(
        @SerialName("season_number") val seasonNumber: Int = 0,
        val name: String? = null,
        @SerialName("episode_count") val episodeCount: Int = 0,
    )

    companion object {
        const val POSTER_BASE = "https://image.tmdb.org/t/p/w500"
    }
}
