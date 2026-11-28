package com.saydlawy.sahab.feature.torrent.data

import com.saydlawy.sahab.feature.torrent.domain.AddonCatalogItem
import com.saydlawy.sahab.feature.torrent.domain.AddonCatalogRow
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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

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
private data class StremioCatalogDefDto(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val extra: List<StremioCatalogExtraDto> = emptyList(),
)

@Serializable
private data class StremioCatalogExtraDto(
    val name: String? = null,
    val isRequired: Boolean = false,
)

@Serializable
private data class StremioCatalogResponse(
    val metas: List<StremioMetaDto> = emptyList(),
)

@Serializable
private data class StremioMetaDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
)

@Serializable
private data class StremioManifestDto(
    val name: String? = null,
    val catalogs: List<StremioCatalogDefDto> = emptyList(),
)

class StremioAddonClient(
    private val client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val manifestCache = ConcurrentHashMap<String, StremioManifestDto>()

    suspend fun fetchAllStreams(
        addons: List<TorrentAddonConfig>,
        imdbId: String,
        isSeries: Boolean,
        season: Int? = null,
        episode: Int? = null,
        tmdbId: Int? = null,
        searchTitle: String? = null,
    ): List<TorrentStreamCandidate> = coroutineScope {
        val cleanImdb = imdbId.trim()
        val cleanTitle = searchTitle?.trim().orEmpty()
        if (cleanImdb.isBlank() && tmdbId == null && cleanTitle.isBlank()) {
            return@coroutineScope emptyList()
        }

        val activeAddons = addons.filter { it.enabled && it.baseUrl.startsWith("http") }
        if (activeAddons.isEmpty()) return@coroutineScope emptyList()

        val typeStr = if (isSeries) "series" else "movie"
        val epSuffix = if (isSeries && season != null && episode != null) ":$season:$episode" else ""

        val all = activeAddons.map { addon ->
            async(Dispatchers.IO) {
                val results = mutableListOf<TorrentStreamCandidate>()
                if (cleanImdb.isNotBlank()) {
                    results.addAll(fetchFromAddon(addon, "${addon.baseUrl}/stream/$typeStr/$cleanImdb$epSuffix.json"))
                }
                if (results.isEmpty() && tmdbId != null && tmdbId > 0) {
                    results.addAll(fetchFromAddon(addon, "${addon.baseUrl}/stream/$typeStr/tmdb:$tmdbId$epSuffix.json"))
                }
                if (cleanTitle.isNotBlank()) {
                    results.addAll(fetchViaCatalogSearch(addon, cleanTitle, typeStr, epSuffix))
                }
                results
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

    private fun fetchViaCatalogSearch(
        addon: TorrentAddonConfig,
        query: String,
        typeStr: String,
        epSuffix: String,
    ): List<TorrentStreamCandidate> {
        val manifest = manifestCache[addon.baseUrl] ?: run {
            val body = executeGet("${addon.baseUrl}/manifest.json") ?: return emptyList()
            runCatching { json.decodeFromString<StremioManifestDto>(body) }.getOrNull()?.also {
                manifestCache[addon.baseUrl] = it
            } ?: return emptyList()
        }

        val searchableCatalogs = manifest.catalogs.filter { cat ->
            val catId = cat.id.orEmpty()
            val catType = cat.type.orEmpty()
            val supportsSearch = cat.extra.any { it.name.equals("search", ignoreCase = true) }
            val typeMatches = catType.equals(typeStr, ignoreCase = true) || catType.equals("other", ignoreCase = true)
            catId.isNotBlank() && supportsSearch && typeMatches
        }.take(2)

        if (searchableCatalogs.isEmpty()) return emptyList()
        val encodedQuery = runCatching { URLEncoder.encode(query, "UTF-8").replace("+", "%20") }
            .getOrDefault(query)

        val out = mutableListOf<TorrentStreamCandidate>()
        for (cat in searchableCatalogs) {
            val catType = cat.type.orEmpty().ifBlank { typeStr }
            val catId = cat.id.orEmpty()
            val catUrl = "${addon.baseUrl}/catalog/$catType/$catId/search=$encodedQuery.json"
            val body = executeGet(catUrl) ?: continue
            val catalogResp = runCatching { json.decodeFromString<StremioCatalogResponse>(body) }.getOrNull()
                ?: continue
            for (meta in catalogResp.metas.take(3)) {
                val metaId = meta.id?.trim().orEmpty()
                if (metaId.isBlank()) continue
                val metaType = meta.type?.trim().orEmpty().ifBlank { catType }
                val encodedId = runCatching { URLEncoder.encode(metaId, "UTF-8").replace("+", "%20") }
                    .getOrDefault(metaId)
                val suffix = if (metaType.equals("series", ignoreCase = true)) epSuffix else ""
                out.addAll(fetchFromAddon(addon, "${addon.baseUrl}/stream/$metaType/$encodedId$suffix.json"))
            }
        }
        return out
    }

    suspend fun fetchCatalogRows(
        addons: List<TorrentAddonConfig>,
        maxRowsPerAddon: Int = 10,
        maxItemsPerRow: Int = 20,
    ): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        val enabled = addons.filter { it.enabled && it.baseUrl.startsWith("http") }
        if (enabled.isEmpty()) return@withContext emptyList()

        coroutineScope {
            enabled.map { addon ->
                async {
                    runCatching {
                        fetchAddonCatalogRows(addon, maxRowsPerAddon, maxItemsPerRow)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }

    private fun fetchAddonCatalogRows(
        addon: TorrentAddonConfig,
        maxRowsPerAddon: Int,
        maxItemsPerRow: Int,
    ): List<AddonCatalogRow> {
        val manifest = manifestCache[addon.baseUrl] ?: run {
            val body = executeGet("${addon.baseUrl}/manifest.json") ?: return emptyList()
            runCatching { json.decodeFromString<StremioManifestDto>(body) }.getOrNull()?.also {
                manifestCache[addon.baseUrl] = it
            } ?: return emptyList()
        }

        val browsable = manifest.catalogs.filter { cat ->
            val catId = cat.id.orEmpty()
            val catType = cat.type.orEmpty().lowercase()
            val requiresExtra = cat.extra.any { it.isRequired }
            catId.isNotBlank() && catType.isNotBlank() && !requiresExtra
        }.take(maxRowsPerAddon)

        if (browsable.isEmpty()) return emptyList()

        val rows = mutableListOf<AddonCatalogRow>()
        val addonLabel = addon.name.ifBlank { manifest.name.orEmpty().ifBlank { "إضافة" } }
        for (cat in browsable) {
            val catType = cat.type.orEmpty().lowercase()
            val catId = cat.id.orEmpty()
            val encodedId = runCatching { URLEncoder.encode(catId, "UTF-8").replace("+", "%20") }
                .getOrDefault(catId)
            val body = executeGet("${addon.baseUrl}/catalog/$catType/$encodedId.json") ?: continue
            val resp = runCatching { json.decodeFromString<StremioCatalogResponse>(body) }.getOrNull()
                ?: continue
            val items = resp.metas.mapNotNull { meta ->
                val metaId = meta.id?.trim().orEmpty()
                val title = meta.name?.trim().orEmpty()
                if (metaId.isBlank() || title.isBlank()) return@mapNotNull null
                val year = meta.releaseInfo?.trim()?.take(4)?.takeIf { it.all(Char::isDigit) }
                AddonCatalogItem(
                    id = metaId,
                    type = meta.type?.trim()?.lowercase().orEmpty().ifBlank { catType },
                    title = title,
                    posterUrl = meta.poster?.trim()?.takeIf { it.startsWith("http") },
                    releaseYear = year,
                    rating = meta.imdbRating?.toDoubleOrNull(),
                    addonName = addonLabel,
                )
            }.take(maxItemsPerRow)

            if (items.isNotEmpty()) {
                val catTitle = cat.name?.trim().orEmpty().ifBlank {
                    when (catType) {
                        "series" -> "مسلسلات"
                        "movie" -> "أفلام"
                        "anime" -> "أنمي"
                        else -> catId
                    }
                }
                rows.add(
                    AddonCatalogRow(
                        id = "${addon.id}:$catType:$catId",
                        title = "$catTitle • $addonLabel",
                        addonName = addonLabel,
                        type = catType,
                        items = items,
                    )
                )
            }
        }
        return rows
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
