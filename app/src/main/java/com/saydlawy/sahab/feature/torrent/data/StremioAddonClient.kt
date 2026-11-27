package com.saydlawy.sahab.feature.torrent.data

import com.saydlawy.sahab.feature.torrent.domain.StreamMetadataParser
import com.saydlawy.sahab.feature.torrent.domain.TorrentAddonConfig
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class StremioStreamResponse(
    val streams: List<StremioStreamDto> = emptyList(),
)

@Serializable
private data class StremioStreamDto(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val sources: List<String> = emptyList(),
)

@Serializable
private data class StremioManifestDto(
    val name: String? = null,
)

class StremioAddonClient(
    private val client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun fetchAllStreams(
        addons: List<TorrentAddonConfig>,
        imdbId: String,
        isSeries: Boolean,
        season: Int? = null,
        episode: Int? = null,
    ): List<TorrentStreamCandidate> = coroutineScope {
        val cleanImdb = imdbId.trim()
        if (cleanImdb.isBlank()) return@coroutineScope emptyList()

        val activeAddons = addons.filter { it.enabled && it.baseUrl.startsWith("http") }
        if (activeAddons.isEmpty()) return@coroutineScope emptyList()

        val path = if (isSeries && season != null && episode != null) {
            "stream/series/$cleanImdb:$season:$episode.json"
        } else {
            "stream/movie/$cleanImdb.json"
        }

        val all = activeAddons.map { addon ->
            async(Dispatchers.IO) {
                fetchFromAddon(addon, "${addon.baseUrl}/$path")
            }
        }.awaitAll().flatten()

        val deduped = linkedMapOf<String, TorrentStreamCandidate>()
        for (item in all) {
            val key = item.directUrl?.lowercase()
                ?: item.infoHash?.let { "$it:${item.fileIdx ?: -1}" }
                ?: item.id
            val existing = deduped[key]
            if (existing == null || (!existing.isInstantCached && item.isInstantCached)) {
                deduped[key] = item
            }
        }
        StreamMetadataParser.sortCandidates(deduped.values.toList())
    }

    private fun fetchFromAddon(addon: TorrentAddonConfig, url: String): List<TorrentStreamCandidate> {
        val body = executeGet(url) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<StremioStreamResponse>(body) }.getOrNull()
            ?: return emptyList()

        return parsed.streams.mapIndexedNotNull { idx, dto ->
            val hasSource = !dto.url.isNullOrBlank() || !dto.infoHash.isNullOrBlank()
            if (!hasSource) return@mapIndexedNotNull null
            val trackers = dto.sources
                .filter { it.startsWith("tracker:", ignoreCase = true) }
                .map { it.substringAfter(":").trim() }
                .filter { it.isNotBlank() }

            StreamMetadataParser.parse(
                id = "${addon.id}:$idx",
                addonName = addon.name,
                nameField = dto.name,
                titleField = dto.title ?: dto.description,
                directUrl = dto.url,
                infoHash = dto.infoHash,
                fileIdx = dto.fileIdx,
                trackers = trackers,
            )
        }
    }

    suspend fun fetchManifestName(manifestUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = executeGet(manifestUrl.trim())
                ?: error("تعذّر الاتصال برابط الإضافة")
            val manifest = json.decodeFromString<StremioManifestDto>(body)
            manifest.name?.trim().orEmpty().ifBlank { "إضافة Stremio" }
        }
    }

    private fun executeGet(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SahabPlayer/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()
}
