package com.saydlawy.sahab.ui.player

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Shared Media3 (ExoPlayer) factory with:
 * - Singleton 250 MB [SimpleCache] on disk for VOD (Movies & Series episodes) so scrubbing/rewinding/reopening is instant.
 * - Ultra-fast startup buffer thresholds ([DefaultLoadControl]) for sub-second playback start.
 * - Hardware/Software decoder fallback & TS/HLS/DASH/RTSP support.
 */
@OptIn(UnstableApi::class)
object SahabPlayer {

    const val SEEK_STEP_MS = 10_000L
    private const val VIDEO_JOINING_TIME_MS = 3_000L
    private const val MEDIA_CACHE_BYTES_TV = 150L * 1024L * 1024L     // 150 MB on Android TV storage
    private const val MEDIA_CACHE_BYTES_MOBILE = 250L * 1024L * 1024L // 250 MB on mobile/tablet

    private val cacheLock = Any()
    private val cacheWarmupStarted = AtomicBoolean(false)
    private val cacheIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var simpleCache: SimpleCache? = null

    private fun getOrInitMediaCacheOffMain(context: Context): SimpleCache? {
        simpleCache?.let { return it }
        synchronized(cacheLock) {
            simpleCache?.let { return it }
            return runCatching {
                val appContext = context.applicationContext
                val dir = File(appContext.cacheDir, MEDIA_CACHE_DIR).apply { mkdirs() }
                val cacheCap = if (com.saydlawy.sahab.util.isTelevision(appContext)) {
                    MEDIA_CACHE_BYTES_TV
                } else {
                    MEDIA_CACHE_BYTES_MOBILE
                }
                val evictor = LeastRecentlyUsedCacheEvictor(cacheCap)
                val dbProvider = StandaloneDatabaseProvider(appContext)
                SimpleCache(dir, evictor, dbProvider).also { simpleCache = it }
            }.getOrNull()
        }
    }

    private fun ensureMediaCacheWarmingAsync(appContext: Context) {
        if (simpleCache != null) return
        if (cacheWarmupStarted.compareAndSet(false, true)) {
            cacheIoScope.launch {
                runCatching { getOrInitMediaCacheOffMain(appContext) }
            }
        }
    }

    fun clearMediaCache(context: Context) {
        synchronized(cacheLock) {
            runCatching { simpleCache?.release() }
            simpleCache = null
            cacheWarmupStarted.set(false)
            runCatching {
                File(context.applicationContext.cacheDir, MEDIA_CACHE_DIR).deleteRecursively()
            }
        }
    }

    suspend fun clearMediaCacheOffMain(context: Context) {
        withContext(Dispatchers.IO) { clearMediaCache(context) }
    }

    private const val MEDIA_CACHE_DIR = "sahab_media_cache"
    private val CACHE_KEY_CREDENTIAL_REGEX = Regex("/(live|movie|series)/[^/]+/[^/]+/")

    internal val leaseCoordinator = PlayerLeaseCoordinator()
    private val playerHandles = java.util.Collections.synchronizedMap(
        java.util.IdentityHashMap<ExoPlayer, ExoPlayerLeaseHandle>(),
    )

    private class ExoPlayerLeaseHandle(
        private val player: ExoPlayer,
        override val role: PlayerRole,
    ) : LeasedPlayerHandle {
        private val released = AtomicBoolean(false)

        @Volatile
        var onRevoked: (() -> Unit)? = null

        override fun stopAndRelease() {
            if (!released.compareAndSet(false, true)) return
            playerHandles.remove(player)
            onRevoked?.invoke()
            onRevoked = null
            runCatching { player.stop() }
            runCatching { player.clearMediaItems() }
            runCatching { player.release() }
        }
    }

    /**
     * Binds an optional callback invoked when [player] is released or preempted by another player lease.
     */
    fun bindOnRelease(player: ExoPlayer, onReleased: () -> Unit) {
        playerHandles[player]?.onRevoked = onReleased
    }

    /**
     * Synchronously stops, clears, and releases [player] once via [leaseCoordinator].
     */
    fun releasePlayer(player: ExoPlayer?) {
        if (player == null) return
        val handle = playerHandles.remove(player)
        if (handle != null) {
            leaseCoordinator.release(handle)
        } else {
            runCatching { player.stop() }
            runCatching { player.clearMediaItems() }
            runCatching { player.release() }
        }
    }

    /**
     * Synchronously stops and releases any active preview player before fullscreen playback begins.
     */
    fun releasePreviewPlayers() {
        leaseCoordinator.releaseByRole(PlayerRole.PREVIEW)
    }

    fun warmUpMediaCache(context: Context) {
        getOrInitMediaCacheOffMain(context.applicationContext)
    }

    fun create(
        context: Context,
        client: OkHttpClient,
        isLive: Boolean,
        isPreview: Boolean = false,
        accountHash: String = "anon",
    ): ExoPlayer {
        val appContext = context.applicationContext
        // Release any existing active player lease before allocating a new ExoPlayer/MediaCodec instance.
        leaseCoordinator.releaseAll()
        val renderers = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            // LIVE-01: let video join the already-running audio clock without dropping the first frames.
            .setAllowedVideoJoiningTimeMs(VIDEO_JOINING_TIME_MS)

        // LIVE-01: adapt across any quality / fps / codec / audio format the provider sends.
        val trackSelector = DefaultTrackSelector(
            appContext,
            DefaultTrackSelector.Parameters.Builder(appContext)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedSampleRateAdaptiveness(true)
                .setAllowAudioMixedChannelCountAdaptiveness(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build(),
        )

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )

        // LIVE-01: live segments/playlists must never go through the shared 50 MB OkHttp disk cache
        // (stale .m3u8 + disk write latency). newBuilder() keeps the shared connection pool/dispatcher.
        val httpClient = if (isLive) client.newBuilder().cache(null).build() else client
        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent("Sahab/1.0 (Android)")

        val dataSourceFactory: DataSource.Factory = if (!isLive) {
            val cache = simpleCache
            if (cache != null) {
                val sanitizedKeyFactory = CacheKeyFactory { dataSpec ->
                    val rawKey = dataSpec.key ?: dataSpec.uri.toString()
                    // Isolate cache per account: /kind/<accountHash>/<id>.<ext>
                    // (hash derived from server+user; never stores credentials in cache keys)
                    rawKey.replace(CACHE_KEY_CREDENTIAL_REGEX, "/$1/$accountHash/")
                }
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setCacheKeyFactory(sanitizedKeyFactory)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } else {
                ensureMediaCacheWarmingAsync(appContext)
                httpFactory
            }
        } else {
            httpFactory
        }

        val sources = DefaultMediaSourceFactory(appContext, extractors)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(if (isLive) 4 else 3))
            .apply {
                if (isLive) {
                    // LIVE-01: never speed up / slow down live playback to chase the live edge.
                    // Speed changes skew the AudioTrack clock and trigger video drop-frame storms.
                    setLiveMinSpeed(1f)
                    setLiveMaxSpeed(1f)
                }
            }

        val loadControl = when {
            isPreview -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 4_000,
                    /* maxBufferMs = */ 12_000,
                    /* bufferForPlaybackMs = */ 450,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_000,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            isLive -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 30_000,
                    // LIVE-01: fast start with reorder headroom for 50/60fps HEVC/FHD/4K.
                    /* bufferForPlaybackMs = */ 650,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_800,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(0, false)
                .build()
            else -> {
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val isConstrainedRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 256) <= 128
                val vodTargetBytes = if (isConstrainedRam) 24 * 1024 * 1024 else 48 * 1024 * 1024
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 45_000,
                        /* bufferForPlaybackMs = */ 800,
                        /* bufferForPlaybackAfterRebufferMs = */ 1_800,
                    )
                    .setTargetBufferBytes(vodTargetBytes)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build()
            }
        }

        return ExoPlayer.Builder(appContext, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(sources)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, !isPreview)
            .setHandleAudioBecomingNoisy(!isPreview)
            .setWakeMode(if (isPreview) C.WAKE_MODE_NONE else C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .also { created ->
                val role = if (isPreview) PlayerRole.PREVIEW else PlayerRole.FULLSCREEN
                val handle = ExoPlayerLeaseHandle(created, role)
                playerHandles[created] = handle
                leaseCoordinator.acquire(handle)
            }
    }

    /** Formats a live technical video badge from active tracks. */
    fun videoBadge(tracks: Tracks, fallbackQuality: String? = null): String {
        val selectedFormat = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { group ->
                (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                    ?: if (group.length > 0) group.getTrackFormat(0) else null
            }

        if (selectedFormat != null && selectedFormat.height > 0) {
            val h = selectedFormat.height
            val res = when {
                h >= 2160 -> "4K"
                h >= 1440 -> "2K"
                h >= 1080 -> "1080p"
                h >= 720 -> "720p"
                h >= 540 -> "540p"
                else -> "${h}p"
            }
            val fps = if (selectedFormat.frameRate > 0f) {
                " ${selectedFormat.frameRate.roundToInt()}fps"
            } else ""
            return "$res$fps"
        }
        return fallbackQuality?.takeIf { it.isNotBlank() } ?: "HD"
    }

    /** Formats an audio stream badge from active tracks. */
    fun audioBadge(tracks: Tracks): String? {
        val selectedFormat = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group ->
                (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                    ?: if (group.length > 0) group.getTrackFormat(0) else null
            } ?: return null

        val langCode = selectedFormat.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale.forLanguageTag(langCode).getDisplayLanguage(Locale.forLanguageTag("ar")).ifBlank { langCode }
        } else {
            selectedFormat.label?.takeIf { it.isNotBlank() } ?: "الصوت"
        }

        val codec = codecShortName(selectedFormat)
        return if (codec != null) "$langName ($codec)" else langName
    }

    fun videoQualityLabel(format: Format): String {
        val h = format.height
        val res = when {
            h >= 2160 -> "4K (${h}p)"
            h >= 1080 -> "FHD (${h}p)"
            h >= 720 -> "HD (${h}p)"
            h > 0 -> "SD (${h}p)"
            else -> format.label ?: "مسار فيديو"
        }
        val fps = if (format.frameRate > 0f) " · ${format.frameRate.roundToInt()}fps" else ""
        val bitrate = if (format.bitrate > 0) " · ${String.format(Locale.US, "%.1f", format.bitrate / 1_000_000f)} Mbps" else ""
        return "$res$fps$bitrate"
    }

    fun activeBitrateText(tracks: Tracks, isLive: Boolean): String {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        if (format != null && format.bitrate > 0) {
            return String.format(Locale.US, "%.1f Mbps", format.bitrate / 1_000_000f)
        }
        return ""
    }

    fun activeAudioLabel(tracks: Tracks): String {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { g ->
                (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
                    ?: if (g.length > 0) g.getTrackFormat(0) else null
            } ?: return "الصوت الافتراضي"

        val langCode = format.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale.forLanguageTag(langCode).getDisplayLanguage(Locale.forLanguageTag("ar")).ifBlank { langCode }
        } else {
            format.label?.takeIf { it.isNotBlank() } ?: "الصوت"
        }
        val codec = codecShortName(format)
        return if (codec != null) "$langName ($codec)" else langName
    }

    fun activeSubtitleLabel(tracks: Tracks, disabled: Boolean): String {
        if (disabled) return "بدون ترجمة"
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            ?: return "بدون ترجمة"
        val langCode = format.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale.forLanguageTag(langCode).getDisplayLanguage(Locale.forLanguageTag("ar")).ifBlank { langCode }
        } else {
            format.label?.takeIf { it.isNotBlank() } ?: "ترجمة"
        }
        return "$langName (CC)"
    }

    fun activeQualityButtonLabel(tracks: Tracks, hasOverride: Boolean, isLive: Boolean): String {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        val h = format?.height ?: 0
        val res = when {
            h >= 2160 -> "4K"
            h >= 1080 -> "1080p FHD"
            h >= 720 -> "720p HD"
            h > 0 -> "${h}p"
            else -> ""
        }
        return when {
            res.isBlank() -> if (hasOverride) "الجودة" else "تلقائي"
            hasOverride -> res
            else -> "تلقائي $res"
        }
    }

    private fun codecShortName(format: Format): String? {
        val mime = format.sampleMimeType ?: return null
        return when (mime) {
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_AC3 -> "AC3"
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_OPUS -> "OPUS"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            else -> mime.substringAfter('/', "").uppercase(Locale.US).takeIf { it.isNotBlank() }
        }
    }
}
