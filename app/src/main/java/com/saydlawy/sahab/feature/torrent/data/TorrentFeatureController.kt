package com.saydlawy.sahab.feature.torrent.data

import android.content.Context
import com.saydlawy.sahab.feature.torrent.api.TorrentFeatureBridge
import com.saydlawy.sahab.feature.torrent.domain.AddonCatalogRow
import com.saydlawy.sahab.feature.torrent.domain.TorrentEngineMode
import com.saydlawy.sahab.feature.torrent.domain.TorrentResolutionResult
import com.saydlawy.sahab.feature.torrent.domain.TorrentSettings
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import com.saydlawy.sahab.feature.torrent.local.TorrentPreferences
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

class TorrentFeatureController(
    context: Context,
    okHttpClient: OkHttpClient,
) : TorrentFeatureBridge {

    val preferences = TorrentPreferences(context)
    val addonClient = StremioAddonClient(okHttpClient)
    private val torrServerEngine = TorrServerEngine(okHttpClient)
    private val debridEngine = DebridEngine(okHttpClient)

    @Volatile private var cachedCatalogRows: List<AddonCatalogRow> = emptyList()
    @Volatile private var cachedCatalogSignature: String = ""
    @Volatile private var cachedCatalogAtMs: Long = 0L

    override val isFeatureCompiled: Boolean = true
    override val settings: StateFlow<TorrentSettings> = preferences.settings

    override fun isEnabledAndReady(): Boolean {
        val cur = preferences.current
        return cur.enabled && cur.hasConfiguredAddons
    }

    override suspend fun fetchCatalogRows(forceRefresh: Boolean): List<AddonCatalogRow> {
        val cur = preferences.current
        if (!cur.enabled || !cur.hasConfiguredAddons) {
            cachedCatalogRows = emptyList()
            cachedCatalogSignature = ""
            return emptyList()
        }
        val sig = cur.addons.filter { it.enabled }.joinToString("|") { "${it.id}:${it.manifestUrl}" }
        val now = System.currentTimeMillis()
        if (!forceRefresh && sig == cachedCatalogSignature && (now - cachedCatalogAtMs) < 15 * 60_000L && cachedCatalogRows.isNotEmpty()) {
            return cachedCatalogRows
        }
        val rows = addonClient.fetchCatalogRows(cur.addons)
        cachedCatalogRows = rows
        cachedCatalogSignature = sig
        cachedCatalogAtMs = now
        return rows
    }

    override suspend fun fetchDirectAddonStreams(
        addonId: String,
        type: String,
        itemId: String,
    ): List<TorrentStreamCandidate> {
        val cur = preferences.current
        if (!cur.enabled || !cur.hasConfiguredAddons) return emptyList()
        return addonClient.fetchDirectAddonStreams(cur.addons, addonId, type, itemId)
    }

    override suspend fun fetchStreams(
        imdbId: String,
        isSeries: Boolean,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        searchTitle: String?,
    ): List<TorrentStreamCandidate> {
        val cur = preferences.current
        if (!cur.enabled || !cur.hasConfiguredAddons) return emptyList()
        return addonClient.fetchAllStreams(
            addons = cur.addons,
            imdbId = imdbId,
            isSeries = isSeries,
            season = season,
            episode = episode,
            tmdbId = tmdbId,
            searchTitle = searchTitle,
        )
    }

    override suspend fun resolveForPlayback(candidate: TorrentStreamCandidate): TorrentResolutionResult {
        if (candidate.isDirectHttp) {
            return TorrentResolutionResult.Ready(streamUrl = candidate.directUrl!!)
        }
        val cur = preferences.current
        return when (cur.engineMode) {
            TorrentEngineMode.TORRSERVER -> torrServerEngine.resolve(candidate, cur)
            TorrentEngineMode.REAL_DEBRID,
            TorrentEngineMode.TORBOX -> debridEngine.resolve(candidate, cur)
            TorrentEngineMode.AUTO -> {
                if (cur.debridApiKey.isNotBlank()) {
                    debridEngine.resolve(candidate, cur)
                } else {
                    torrServerEngine.resolve(candidate, cur)
                }
            }
        }
    }

    override suspend fun onPlaybackStopped(torrServerHash: String?) {
        val hash = torrServerHash?.trim().orEmpty()
        if (hash.isNotBlank()) {
            torrServerEngine.cleanup(hash, preferences.current)
        }
    }

    override suspend fun testTorrServerConnection(): Result<String> =
        torrServerEngine.testConnection(preferences.current)
}
