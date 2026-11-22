package com.saydlawy.sahab.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * Unified movie catalog — Nova (Xtream) metadata + TMDB enrichment merged.
 *
 * sahabId states:
 *   "pending:{novaStreamId}"  → not yet enriched
 *   "tmdb:{tmdbId}"           → successfully matched with TMDB
 *   "local:{hash}"            → TMDB match failed after retries
 *
 * needsEnrichment:
 *   1  = pending (will be tried)
 *   0  = done
 *  -1  = permanently failed (skip)
 */
@Serializable
@Entity(
    tableName = "catalog_movies",
    indices = [
        Index(value = ["mediaType", "novaStreamId"], unique = true),
        Index(value = ["categoryId", "novaAdded"]),
        Index(value = ["needsEnrichment", "novaAdded"]),
        Index(value = ["tmdbId"]),
        Index(value = ["lastSeen"]),
        Index(value = ["deleted"]),
        Index(value = ["needsCloudPush", "updatedAt"]),
        Index(value = ["mediaType", "deleted", "novaAdded"]),
        Index(value = ["mediaType", "deleted", "rating"]),
        Index(value = ["mediaType", "deleted", "year"]),
    ],
)
data class CatalogMovieEntity(
    @PrimaryKey val sahabId: String,
    val novaStreamId: String,
    val categoryId: String = "",
    val titleAr: String = "",
    val titleEn: String = "",
    val year: String = "",
    val plot: String = "",
    val plotSource: String = "",
    val plotConfidence: String = "",
    val identifiedTitle: String = "",
    val mediaType: String = "movie",
    val cast: String = "",
    val castJson: String = "",
    val director: String = "",
    val genre: String = "",
    val runtime: String = "",
    val rating: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val postersJson: String = "",
    val trailerUrl: String = "",
    val imdbId: String = "",
    val novaAdded: Long = 0L,
    val containerExtension: String = "m3u8",
    val isAdult: Int = 0,
    val tmdbId: Int? = null,
    val tmdbVoteCount: Int? = null,
    val needsEnrichment: Int = 1,
    val lastEnriched: Long = 0L,
    val lastSeen: Long = 0L,
    val deleted: Int = 0,
    val needsCloudPush: Int = 0,
    val updatedAt: Long = 0L,
) {
    val displayTitle: String
        get() = titleAr.ifBlank { titleEn }

    companion object {
        fun idPending(streamId: String, mediaType: String = "movie"): String =
            if (mediaType.equals("series", ignoreCase = true)) "series_pending:$streamId" else "pending:$streamId"
        fun idFromTmdb(tmdbId: Int): String = "tmdb:$tmdbId"
        fun idLocal(name: String, year: String): String {
            val raw = "${name.lowercase().trim()}|${year.trim()}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return "local:" + digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

fun encodeTmdbCastJson(cast: List<com.saydlawy.sahab.data.Cast>?): String {
    if (cast.isNullOrEmpty()) return ""
    return cast
        .filter { it.name.isNotBlank() }
        .joinToString("\n") { member ->
            val safeName = member.name.replace("|", " ").replace("\n", " ").trim()
            val photo = member.profilePath?.takeIf { it.isNotBlank() }
                ?.let { "https://image.tmdb.org/t/p/w185$it" }
                .orEmpty()
            "$safeName|$photo"
        }
}

fun parseCastMembersJson(raw: String): List<com.saydlawy.sahab.data.CastMember> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split('|', limit = 2)
            val name = parts[0].trim()
            if (name.isEmpty()) return@mapNotNull null
            val photo = parts.getOrNull(1)?.trim().orEmpty()
            com.saydlawy.sahab.data.CastMember(name = name, photoUrl = photo)
        }
        .toList()
}
