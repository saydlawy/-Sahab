package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

class ImdbResolver(
    private val tmdbRepository: TmdbRepository,
    private val apiKey: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun resolveImdbId(imdbId: String): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        val cleaned = imdbId.trim().lowercase()
        if (apiKey.isBlank() || !IMDB_REGEX.matches(cleaned)) return@withContext emptyList()

        val url = "https://api.themoviedb.org/3/find/$cleaned".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("external_source", "imdb_id")
            .addQueryParameter("language", "ar-SA")
            .build()

        val body = tmdbRepository.executeGet(url.toString()) ?: return@withContext emptyList()
        val parsed = runCatching { json.decodeFromString<FindResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        val results = mutableListOf<InstantMediaItem>()

        parsed.movieResults.forEach { dto ->
            val details = tmdbRepository.getDetails(dto.id, InstantMediaType.MOVIE)
            if (details != null) {
                results.add(details.copy(imdbId = cleaned))
            }
        }

        parsed.tvResults.forEach { dto ->
            val details = tmdbRepository.getDetails(dto.id, InstantMediaType.TV)
            if (details != null) {
                results.add(details.copy(imdbId = cleaned))
            }
        }

        results
    }

    @Serializable
    private data class FindResponse(
        @SerialName("movie_results") val movieResults: List<FindItemDto> = emptyList(),
        @SerialName("tv_results") val tvResults: List<FindItemDto> = emptyList(),
    )

    @Serializable
    private data class FindItemDto(val id: Int)

    companion object {
        val IMDB_REGEX = Regex("^tt\\d{6,10}$")
    }
}
