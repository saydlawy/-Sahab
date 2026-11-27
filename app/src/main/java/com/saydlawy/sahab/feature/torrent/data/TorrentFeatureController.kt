package com.saydlawy.sahab.feature.torrent.data

import android.content.Context
import com.saydlawy.sahab.feature.torrent.api.TorrentFeatureBridge
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

    override val isFeatureCompiled: Boolean = true
    override val settings: StateFlow<TorrentSettings> = preferences.settings

    override fun isEnabledAndReady(): Boolean {
        val cur = preferences.current
        return cur.enabled && cur.hasConfiguredAddons
    }

    override suspend fun fetchStreams(
        imdbId: String,
        isSeries: Boolean,
        season: Int?,
        episode: Int?,
    ): List<TorrentStreamCandidate> {
        val cur = preferences.current
        if (!cur.enabled || !cur.hasConfiguredAddons) return emptyList()
        return addonClient.fetchAllStreams(
            addons = cur.addons,
            imdbId = imdbId,
            isSeries = isSeries,
            season = season,
            episode = episode,
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
