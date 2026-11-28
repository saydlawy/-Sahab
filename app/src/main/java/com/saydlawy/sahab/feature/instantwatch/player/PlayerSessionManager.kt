package com.saydlawy.sahab.feature.instantwatch.player

import android.content.Context
import com.saydlawy.sahab.feature.instantwatch.data.ImdbResolver
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.OnlineSubtitleFetcher
import com.saydlawy.sahab.feature.instantwatch.data.SubtitleRepository
import com.saydlawy.sahab.feature.instantwatch.data.TmdbRepository
import com.saydlawy.sahab.feature.instantwatch.data.VidLinkRepository
import com.saydlawy.sahab.feature.instantwatch.domain.AuthenticationGate
import com.saydlawy.sahab.feature.instantwatch.domain.InputResolver
import com.saydlawy.sahab.feature.instantwatch.domain.PlaybackOrchestrator
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.domain.StreamResolver
import com.saydlawy.sahab.feature.instantwatch.domain.SubtitleManager
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchDatabase
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchPreferences
import com.saydlawy.sahab.feature.instantwatch.security.NavigationGuard
import com.saydlawy.sahab.feature.instantwatch.security.RequestInspector
import com.saydlawy.sahab.feature.instantwatch.security.SecurityMonitor
import com.saydlawy.sahab.feature.instantwatch.security.StreamSecurityPolicy
import com.saydlawy.sahab.feature.instantwatch.security.WebViewSecurityPolicy
import com.saydlawy.sahab.feature.torrent.api.TorrentFeatureBridge
import com.saydlawy.sahab.feature.torrent.data.TorrentFeatureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class ActivePlaybackSession(
    val item: InstantMediaItem,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val source: ResolvedPlaybackSource,
    val resumePositionMs: Long,
    val headers: Map<String, String> = emptyMap(),
    val torrServerHashToCleanup: String? = null,
    val releaseHint: String? = null,
)

class PlayerSessionManager(
    context: Context,
    okHttpClient: OkHttpClient,
    tmdbApiKey: String,
    openSubtitlesApiKey: String = "",
) {
    val preferences = InstantWatchPreferences(context)
    val database = InstantWatchDatabase.getInstance(context)
    val securityPolicy = StreamSecurityPolicy()
    val webViewSecurityPolicy = WebViewSecurityPolicy()
    val requestInspector = RequestInspector(securityPolicy)
    val securityMonitor = SecurityMonitor()
    val navigationGuard = NavigationGuard(securityPolicy, requestInspector, securityMonitor)

    val authGate = AuthenticationGate(preferences)
    val tmdbRepository = TmdbRepository(okHttpClient, tmdbApiKey)
    val imdbResolver = ImdbResolver(tmdbRepository, tmdbApiKey)
    val vidLinkRepository = VidLinkRepository(securityPolicy)
    val subtitleRepository = SubtitleRepository(okHttpClient, securityPolicy, openSubtitlesApiKey)
    val onlineSubtitleFetcher = OnlineSubtitleFetcher(okHttpClient)

    val inputResolver = InputResolver(authGate, tmdbRepository, imdbResolver)
    val subtitleManager = SubtitleManager(preferences, subtitleRepository)
    val streamResolver = StreamResolver(vidLinkRepository, securityPolicy, requestInspector)
    val orchestrator = PlaybackOrchestrator(
        authGate = authGate,
        streamResolver = streamResolver,
        subtitleManager = subtitleManager,
        historyDao = database.instantWatchDao(),
        preferences = preferences,
    )

    val torrentBridge: TorrentFeatureBridge = TorrentFeatureController(context, okHttpClient)
    val torrentController: TorrentFeatureController?
        get() = torrentBridge as? TorrentFeatureController

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeSession = MutableStateFlow<ActivePlaybackSession?>(null)
    val activeSession: StateFlow<ActivePlaybackSession?> = _activeSession.asStateFlow()

    fun startSession(session: ActivePlaybackSession) {
        val prevHash = _activeSession.value?.torrServerHashToCleanup
        if (!prevHash.isNullOrBlank() && prevHash != session.torrServerHashToCleanup) {
            sessionScope.launch { torrentBridge.onPlaybackStopped(prevHash) }
        }
        securityMonitor.clearFailure()
        _activeSession.value = session
    }

    fun endSession(clearSecurityLog: Boolean = true) {
        val prevHash = _activeSession.value?.torrServerHashToCleanup
        _activeSession.value = null
        if (!prevHash.isNullOrBlank()) {
            sessionScope.launch { torrentBridge.onPlaybackStopped(prevHash) }
        }
        if (clearSecurityLog) {
            securityMonitor.reset()
        }
    }

    fun onServiceDisabledOrSignedOut() {
        endSession(clearSecurityLog = true)
    }
}
