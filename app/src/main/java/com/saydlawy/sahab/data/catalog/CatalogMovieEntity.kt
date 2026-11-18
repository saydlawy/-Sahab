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
    val director: String = "",
    val genre: String = "",
    val runtime: String = "",
    val rating: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
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
) {
    val displayTitle: String
        get() = titleAr.ifBlank { titleEn }

    companion object {
        fun idPending(streamId: String): String = "pending:$streamId"
        fun idFromTmdb(tmdbId: Int): String = "tmdb:$tmdbId"
        fun idLocal(name: String, year: String): String {
            val raw = "${name.lowercase().trim()}|${year.trim()}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return "local:" + digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
