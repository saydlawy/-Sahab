package com.saydlawy.sahab.util

import java.util.Locale

/**
 * Canonical sanitizer and Bayesian ranker for TMDB / Xtream ratings (0..10 scale).
 * Rejects sentinel/placeholder ratings (<= 0.0 or >= 9.9, e.g. "10", "10.0", "10/10")
 * and tiny-sample TMDB flukes (1..9 votes with >= 9.0).
 */
object RatingSanitizer {
    private const val MAX_VALID_RATING = 9.9
    private const val MIN_VOTES_FOR_HIGH_RATING = 10
    private const val HIGH_RATING_THRESHOLD = 9.0
    private const val BAYESIAN_PRIOR_MEAN = 6.2
    private const val BAYESIAN_MIN_VOTES = 50.0

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

    fun rankingScore(rawRating: String?, voteCount: Int? = null): Double {
        val r = parseValid(rawRating, voteCount) ?: return 0.0
        val v = voteCount?.takeIf { it > 0 }?.toDouble()
        return if (v != null) {
            (v / (v + BAYESIAN_MIN_VOTES)) * r + (BAYESIAN_MIN_VOTES / (v + BAYESIAN_MIN_VOTES)) * BAYESIAN_PRIOR_MEAN
        } else {
            r * 0.85
        }
    }
}
