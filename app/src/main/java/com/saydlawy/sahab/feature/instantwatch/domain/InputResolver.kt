package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.ImdbResolver
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.data.TmdbRepository

sealed class InputResolutionResult {
    data class SingleMatch(val item: InstantMediaItem) : InputResolutionResult()
    data class MultipleMatches(val items: List<InstantMediaItem>) : InputResolutionResult()
    data object NoResults : InputResolutionResult()
    data class Denied(val gate: GateResult) : InputResolutionResult()
    data class InvalidInput(val message: String) : InputResolutionResult()
}

class InputResolver(
    private val authGate: AuthenticationGate,
    private val tmdbRepository: TmdbRepository,
    private val imdbResolver: ImdbResolver,
) {
    suspend fun resolve(rawInput: String): InputResolutionResult {
        val gate = authGate.verifyAccess()
        if (gate !is GateResult.Authorized) {
            return InputResolutionResult.Denied(gate)
        }

        val input = rawInput.trim()
        if (input.isEmpty()) {
            return InputResolutionResult.InvalidInput("يرجى إدخال اسم العمل أو رابط أو رقم TMDB / IMDb.")
        }

        val items: List<InstantMediaItem> = resolveSmart(input)

        return when {
            items.isEmpty() -> InputResolutionResult.NoResults
            items.size == 1 -> InputResolutionResult.SingleMatch(items.first())
            else -> InputResolutionResult.MultipleMatches(items)
        }
    }

    private suspend fun resolveSmart(input: String): List<InstantMediaItem> {
        // 1. Any IMDb ID or IMDb URL (e.g., tt0137523 or https://www.imdb.com/title/tt0137523/)
        val imdbMatch = IMDB_ANYWHERE_REGEX.find(input)
        if (imdbMatch != null) {
            val imdbId = imdbMatch.value.lowercase()
            return imdbResolver.resolveImdbId(imdbId)
        }

        // 2. TMDB or embed URL containing /movie/{id} or /tv/{id}
        val typedUrlMatch = TYPED_MEDIA_URL_REGEX.find(input)
        if (typedUrlMatch != null) {
            val typeToken = typedUrlMatch.groupValues[1].lowercase()
            val tmdbId = typedUrlMatch.groupValues[2].toIntOrNull()
            if (tmdbId != null) {
                val type = if (typeToken == "tv" || typeToken == "series") {
                    InstantMediaType.TV
                } else {
                    InstantMediaType.MOVIE
                }
                val item = tmdbRepository.getDetails(tmdbId, type)
                if (item != null) return listOf(item)
            }
        }

        // 3. Pure numeric TMDB ID
        if (NUMERIC_TMDB_REGEX.matches(input)) {
            val id = input.toIntOrNull() ?: return emptyList()
            return tmdbRepository.resolveByNumericId(id)
        }

        // 4. Free text search (Arabic / English / title + year)
        return tmdbRepository.searchMulti(input)
    }

    companion object {
        private val NUMERIC_TMDB_REGEX = Regex("^\d{1,9}$")
        private val IMDB_ANYWHERE_REGEX = Regex("\btt\d{6,10}\b", RegexOption.IGNORE_CASE)
        private val TYPED_MEDIA_URL_REGEX = Regex(
            "(?:themoviedb\.org|tmdb\.org|https?://[^\s]+)/(movie|tv|series)/(?:tmdb/)?(\d{1,9})",
            RegexOption.IGNORE_CASE,
        )
    }
}
