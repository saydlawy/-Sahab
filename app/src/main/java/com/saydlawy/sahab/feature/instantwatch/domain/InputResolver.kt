package com.saydlawy.sahab.feature.instantwatch.domain

import com.saydlawy.sahab.feature.instantwatch.data.ImdbResolver
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
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
            return InputResolutionResult.InvalidInput("يرجى إدخال اسم الفيلم أو المسلسل أو رقم TMDB / IMDb.")
        }

        val items = when {
            ImdbResolver.IMDB_REGEX.matches(input.lowercase()) -> {
                imdbResolver.resolveImdbId(input)
            }
            NUMERIC_TMDB_REGEX.matches(input) -> {
                val id = input.toIntOrNull()
                    ?: return InputResolutionResult.InvalidInput("رقم TMDB غير صالح.")
                tmdbRepository.resolveByNumericId(id)
            }
            else -> {
                tmdbRepository.searchMulti(input)
            }
        }

        return when {
            items.isEmpty() -> InputResolutionResult.NoResults
            items.size == 1 -> InputResolutionResult.SingleMatch(items.first())
            else -> InputResolutionResult.MultipleMatches(items)
        }
    }

    companion object {
        private val NUMERIC_TMDB_REGEX = Regex("^\\d{1,9}$")
    }
}
