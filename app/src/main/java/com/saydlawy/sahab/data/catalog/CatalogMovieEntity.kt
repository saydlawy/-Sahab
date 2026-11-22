package com.saydlawy.sahab.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.util.normalizeForSearch
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal const val CLOUD_REF_STREAM_PREFIX = "__cloud__:"

/**
 * Unified catalog entity — IPTV provider fields (navigation & playback) merged with
 * cloud / TMDB enrichment metadata and locally cached series episodes.
 *
 * sahabId states:
 *   "pending:{novaStreamId}"         → movie not yet enriched
 *   "series_pending:{novaStreamId}"  → series not yet enriched
 *   "tmdb:{tmdbId}"                  → matched/reference TMDB document
 *   "local:{hash}"                   → TMDB match failed after retries
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
        Index(value = ["mediaType", "deleted", "providerOrder", "sahabId"]),
        Index(value = ["mediaType", "deleted", "categoryId", "providerOrder", "sahabId"]),
        Index(value = ["mediaType", "deleted", "needsEnrichment", "providerOrder"]),
        Index(value = ["tmdbId"]),
        Index(value = ["needsCloudPush", "updatedAt"]),
        Index(value = ["mediaType", "deleted", "lastSeen"]),
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
    val providerOrder: Int = Int.MAX_VALUE,
    val episodesJson: String = "",
    val searchTitle: String = "",
) {
    val displayTitle: String
        get() = titleAr.ifBlank { titleEn }

    val isProviderItem: Boolean
        get() = novaStreamId.isNotBlank() && !novaStreamId.startsWith(CLOUD_REF_STREAM_PREFIX)

    companion object {
        fun idPending(streamId: String, mediaType: String = "movie"): String =
            if (mediaType.equals("series", ignoreCase = true)) "series_pending:$streamId" else "pending:$streamId"
        fun idFromTmdb(tmdbId: Int): String = "tmdb:$tmdbId"
        fun cloudRefStreamId(sahabId: String): String = "$CLOUD_REF_STREAM_PREFIX$sahabId"
        fun idLocal(name: String, year: String): String {
            val raw = "${name.lowercase().trim()}|${year.trim()}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return "local:" + digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

private val catalogEpisodeJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
private val SEARCH_BRACKETS_REGEX = Regex("""\[.*?]|\(.*?\)""")
private val SEARCH_MULTI_SPACE_REGEX = Regex("""\s+""")

fun encodeEpisodesJson(episodes: List<Episode>): String {
    if (episodes.isEmpty()) return ""
    return runCatching {
        catalogEpisodeJson.encodeToString(ListSerializer(Episode.serializer()), episodes)
    }.getOrDefault("")
}

fun parseEpisodesJson(raw: String): List<Episode> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        catalogEpisodeJson.decodeFromString(ListSerializer(Episode.serializer()), raw)
    }.getOrDefault(emptyList())
}

fun buildCatalogSearchTitle(vararg parts: String): String {
    val combined = parts.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
    if (combined.isBlank()) return ""
    val normalizedRaw = combined.normalizeForSearch().replace(SEARCH_MULTI_SPACE_REGEX, " ").trim()
    val strippedBrackets = combined.replace(SEARCH_BRACKETS_REGEX, " ").normalizeForSearch()
        .replace(SEARCH_MULTI_SPACE_REGEX, " ")
        .trim()
    return if (strippedBrackets.isNotBlank() && strippedBrackets != normalizedRaw) {
        "$normalizedRaw $strippedBrackets"
    } else {
        normalizedRaw
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
