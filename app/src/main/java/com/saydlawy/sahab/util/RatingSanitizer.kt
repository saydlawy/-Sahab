package com.saydlawy.sahab.util

import java.util.Locale

/**
 * Canonical sanitizer for TMDB / Xtream ratings (0..10 scale) and lightweight metadata formatting.
 * Rejects sentinel/placeholder ratings (<= 0.0 or >= 9.9, e.g. "10", "10.0", "10/10")
 * and tiny-sample TMDB flukes (1..9 votes with >= 9.0).
 */
object RatingSanitizer {
    private const val MAX_VALID_RATING = 9.9
    private const val MIN_VOTES_FOR_HIGH_RATING = 10
    private const val HIGH_RATING_THRESHOLD = 9.0
    private val YEAR_4_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    fun parseValid(raw: String?, voteCount: Int? = null): Double? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed
            .substringBefore('/')
            .replace(',', '.')
            .trim()
        val num = cleaned.toDoubleOrNull() ?: return null
        if (!num.isFinite() || num <= 0.0 || num >= MAX_VALID_RATING) return null
        if (voteCount != null && voteCount in 1 until MIN_VOTES_FOR_HIGH_RATING && num >= HIGH_RATING_THRESHOLD) {
            return null
        }
        return num.coerceAtMost(9.8)
    }

    fun format(raw: String?, voteCount: Int? = null): String {
        val valid = parseValid(raw, voteCount) ?: return ""
        return String.format(Locale.US, "%.1f", valid)
    }

    fun format(voteAverage: Double, voteCount: Int? = null): String {
        if (!voteAverage.isFinite() || voteAverage <= 0.0 || voteAverage >= MAX_VALID_RATING) return ""
        if (voteCount != null && voteCount in 1 until MIN_VOTES_FOR_HIGH_RATING && voteAverage >= HIGH_RATING_THRESHOLD) {
            return ""
        }
        return String.format(Locale.US, "%.1f", voteAverage.coerceAtMost(9.8))
    }

    fun extractFourDigitYear(raw: String?): String =
        raw?.let { YEAR_4_REGEX.find(it)?.value }.orEmpty()

    fun extractPrimaryGenre(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split(',', '،', '/', '|', '&').firstOrNull()?.trim().orEmpty()
    }
}
