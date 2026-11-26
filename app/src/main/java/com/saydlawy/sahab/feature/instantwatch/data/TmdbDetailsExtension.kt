package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
private const val PROFILE_BASE = "https://image.tmdb.org/t/p/w185"
private const val STILL_BASE = "https://image.tmdb.org/t/p/w300"

private val detailJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

data class InstantCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
)

data class InstantEpisodeSummary(
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val stillUrl: String?,
    val runtimeMinutes: Int?,
    val rating: Double?,
)

data class InstantMediaDetails(
    val item: InstantMediaItem,
    val backdropUrl: String?,
    val runtimeMinutes: Int?,
    val voteCount: Int,
    val genres: List<String>,
    val tagline: String,
    val cast: List<InstantCastMember>,
    val similar: List<InstantMediaItem>,
)

suspend fun TmdbRepository.getFullDetails(
    tmdbId: Int,
    type: InstantMediaType,
): InstantMediaDetails? = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() || tmdbId <= 0) return@withContext null
    val url = "https://api.themoviedb.org/3/${type.apiValue}/$tmdbId".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("language", "ar-SA")
        .addQueryParameter("append_to_response", "credits,external_ids,similar")
        .build()

    val body = executeGet(url.toString()) ?: return@withContext null
    val dto = runCatching { detailJson.decodeFromString<FullDetailDto>(body) }.getOrNull()
        ?: return@withContext null

    val displayTitle = (dto.title ?: dto.name).orEmpty().ifBlank { return@withContext null }
    val origTitle = (dto.originalTitle ?: dto.originalName).orEmpty().ifBlank { displayTitle }
    val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull()
    val seasons = dto.seasons.orEmpty()
        .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
        .map { InstantSeasonSummary(it.seasonNumber, it.name.orEmpty().ifBlank { "الموسم ${it.seasonNumber}" }, it.episodeCount) }

    val baseItem = InstantMediaItem(
        tmdbId = dto.id,
        mediaType = type,
        title = displayTitle,
        originalTitle = origTitle,
        releaseYear = year,
        posterUrl = dto.posterPath?.let { "${TmdbRepository.POSTER_BASE}$it" },
        overview = dto.overview.orEmpty(),
        imdbId = dto.imdbId ?: dto.externalIds?.imdbId,
        seasons = seasons,
        rating = dto.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
        genreIds = dto.genres.orEmpty().map { it.id },
    )

    val cast = dto.credits?.cast.orEmpty().take(15).mapNotNull { c ->
        val name = c.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        InstantCastMember(
            id = c.id,
            name = name,
            character = c.character.orEmpty(),
            profileUrl = c.profilePath?.let { "$PROFILE_BASE$it" },
        )
    }

    val similar = dto.similar?.results.orEmpty().take(12).mapNotNull { s ->
        val sTitle = (s.title ?: s.name).orEmpty().ifBlank { return@mapNotNull null }
        val sOrig = (s.originalTitle ?: s.originalName).orEmpty().ifBlank { sTitle }
        val sYear = (s.releaseDate ?: s.firstAirDate)?.take(4)?.toIntOrNull()
        InstantMediaItem(
            tmdbId = s.id,
            mediaType = type,
            title = sTitle,
            originalTitle = sOrig,
            releaseYear = sYear,
            posterUrl = s.posterPath?.let { "${TmdbRepository.POSTER_BASE}$it" },
            overview = s.overview.orEmpty(),
            rating = s.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
            genreIds = s.genreIds.orEmpty(),
        )
    }

    InstantMediaDetails(
        item = baseItem,
        backdropUrl = dto.backdropPath?.let { "$BACKDROP_BASE$it" },
        runtimeMinutes = dto.runtime ?: dto.episodeRunTime?.firstOrNull(),
        voteCount = dto.voteCount ?: 0,
        genres = dto.genres.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
        tagline = dto.tagline.orEmpty(),
        cast = cast,
        similar = similar,
    )
}

suspend fun TmdbRepository.getSeasonEpisodes(
    tmdbId: Int,
    seasonNumber: Int,
): List<InstantEpisodeSummary> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() || tmdbId <= 0 || seasonNumber <= 0) return@withContext emptyList()
    val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("language", "ar-SA")
        .build()

    val body = executeGet(url.toString()) ?: return@withContext emptyList()
    val dto = runCatching { detailJson.decodeFromString<SeasonDetailDto>(body) }.getOrNull()
        ?: return@withContext emptyList()

    dto.episodes.orEmpty().map { ep ->
        InstantEpisodeSummary(
            episodeNumber = ep.episodeNumber,
            name = ep.name.orEmpty().ifBlank { "الحلقة ${ep.episodeNumber}" },
            overview = ep.overview.orEmpty(),
            stillUrl = ep.stillPath?.let { "$STILL_BASE$it" },
            runtimeMinutes = ep.runtime,
            rating = ep.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
        )
    }
}

@Serializable
private data class FullDetailDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: ExtIdsDto? = null,
    val genres: List<GenreItemDto>? = null,
    val seasons: List<SeasonItemDto>? = null,
    val credits: CreditsDto? = null,
    val similar: SimilarRespDto? = null,
)

@Serializable private data class ExtIdsDto(@SerialName("imdb_id") val imdbId: String? = null)
@Serializable private data class GenreItemDto(val id: Int, val name: String? = null)
@Serializable private data class SeasonItemDto(@SerialName("season_number") val seasonNumber: Int = 0, val name: String? = null, @SerialName("episode_count") val episodeCount: Int = 0)
@Serializable private data class CreditsDto(val cast: List<CastDto>? = null)
@Serializable private data class CastDto(val id: Int = 0, val name: String? = null, val character: String? = null, @SerialName("profile_path") val profilePath: String? = null)
@Serializable private data class SimilarRespDto(val results: List<SimilarItemDto>? = null)
@Serializable private data class SimilarItemDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
)
@Serializable private data class SeasonDetailDto(val episodes: List<EpisodeDto>? = null)
@Serializable private data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)
