package com.saydlawy.sahab.feature.torrent.api

import com.saydlawy.sahab.feature.torrent.domain.AddonCatalogRow
import com.saydlawy.sahab.feature.torrent.domain.TorrentResolutionResult
import com.saydlawy.sahab.feature.torrent.domain.TorrentSettings
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface TorrentFeatureBridge {
    val isFeatureCompiled: Boolean
    val settings: StateFlow<TorrentSettings>

    fun isEnabledAndReady(): Boolean

    suspend fun fetchStreams(
        imdbId: String,
        isSeries: Boolean,
        season: Int? = null,
        episode: Int? = null,
        tmdbId: Int? = null,
        searchTitle: String? = null,
    ): List<TorrentStreamCandidate>

    suspend fun resolveForPlayback(candidate: TorrentStreamCandidate): TorrentResolutionResult

    suspend fun onPlaybackStopped(torrServerHash: String?)

    suspend fun fetchCatalogRows(forceRefresh: Boolean = false): List<AddonCatalogRow>

    suspend fun fetchDirectAddonStreams(addonId: String, type: String, itemId: String): List<TorrentStreamCandidate>

    suspend fun testTorrServerConnection(): Result<String>
}

object NoOpTorrentFeatureBridge : TorrentFeatureBridge {
    override val isFeatureCompiled: Boolean = false
    override val settings: StateFlow<TorrentSettings> = MutableStateFlow(TorrentSettings())
    override fun isEnabledAndReady(): Boolean = false

    override suspend fun fetchStreams(
        imdbId: String,
        isSeries: Boolean,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        searchTitle: String?,
    ): List<TorrentStreamCandidate> = emptyList()

    override suspend fun resolveForPlayback(candidate: TorrentStreamCandidate): TorrentResolutionResult =
        TorrentResolutionResult.Error("ميزة مصادر 4K غير مفعّلة في هذه النسخة.")

    override suspend fun onPlaybackStopped(torrServerHash: String?) = Unit

    override suspend fun fetchCatalogRows(forceRefresh: Boolean): List<AddonCatalogRow> = emptyList()

    override suspend fun fetchDirectAddonStreams(addonId: String, type: String, itemId: String): List<TorrentStreamCandidate> = emptyList()

    override suspend fun testTorrServerConnection(): Result<String> =
        Result.failure(IllegalStateException("غير متاح في هذه النسخة"))
}
