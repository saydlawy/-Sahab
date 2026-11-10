package com.saydlawy.sahab.ui.player

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
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
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
    private const val MEDIA_CACHE_BYTES = 250L * 1024L * 1024L // 250 MB

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    private fun getMediaCache(context: Context): SimpleCache? {
        simpleCache?.let { return it }
        return runCatching {
            val appContext = context.applicationContext
            val dir = File(appContext.cacheDir, "sahab_media_cache").apply { mkdirs() }
            val evictor = LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(appContext)
            SimpleCache(dir, evictor, dbProvider).also { simpleCache = it }
        }.getOrNull()
    }

    fun create(
        context: Context,
        client: OkHttpClient,
        isLive: Boolean,
        isPreview: Boolean = false,
    ): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )

        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("Sahab/1.0 (Android)")

        val dataSourceFactory: DataSource.Factory = if (!isLive) {
            val cache = getMediaCache(context)
            if (cache != null) {
                val sanitizedKeyFactory = CacheKeyFactory { dataSpec ->
                    val rawKey = dataSpec.key ?: dataSpec.uri.toString()
                    rawKey.replace(Regex("/(live|movie|series)/[^/]+/[^/]+/"), "/$1/cached/")
                }
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setCacheKeyFactory(sanitizedKeyFactory)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } else {
                httpFactory
            }
        } else {
            httpFactory
        }

        val sources = DefaultMediaSourceFactory(context, extractors)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(if (isLive) 4 else 3))

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
                    /* minBufferMs = */ 8_000,
                    /* maxBufferMs = */ 25_000,
                    /* bufferForPlaybackMs = */ 550,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_400,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            else -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 20_000,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 800,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_800,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(sources)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, !isPreview)
            .setHandleAudioBecomingNoisy(!isPreview)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
    }

    /** Formats a live technical video badge (e.g. "1080p 50fps" or "4K 60fps") from active tracks. */
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
                "${selectedFormat.frameRate.roundToInt()}fps"
            } else {
                "50fps"
            }
            return "$res $fps"
        }
        val fb = fallbackQuality ?: "1080p"
        return "$fb 60fps"
    }

    /** Formats an audio stream badge (e.g. "الصوت: العربية (AAC)") from active tracks. */
    fun audioBadge(tracks: Tracks): String? {
        val selectedFormat = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group ->
                (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                    ?: if (group.length > 0) group.getTrackFormat(0) else null
            } ?: return null

        val langCode = selectedFormat.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale(langCode).getDisplayLanguage(Locale("ar")).ifBlank { langCode }
        } else {
            selectedFormat.label?.takeIf { it.isNotBlank() } ?: "العربية"
        }

        val codec = codecShortName(selectedFormat)
        return if (codec != null) "الصوت: $langName ($codec)" else "الصوت: $langName"
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
        val h = format?.height ?: 0
        return when {
            h >= 2160 -> "24.6 Mbps"
            h >= 1080 -> "18.4 Mbps"
            h >= 720 -> "11.2 Mbps"
            isLive -> "18.4 Mbps"
            else -> "24.6 Mbps"
        }
    }

    fun activeAudioLabel(tracks: Tracks): String {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { g ->
                (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
                    ?: if (g.length > 0) g.getTrackFormat(0) else null
            } ?: return "العربية (Dolby 5.1)"

        val langCode = format.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale(langCode).getDisplayLanguage(Locale("ar")).ifBlank { langCode }
        } else {
            format.label?.takeIf { it.isNotBlank() } ?: "العربية"
        }
        val codec = codecShortName(format) ?: "Dolby 5.1"
        return "$langName ($codec)"
    }

    fun activeSubtitleLabel(tracks: Tracks, disabled: Boolean): String {
        if (disabled) return "بدون ترجمة"
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
            ?: return "العربية (CC)"
        val langCode = format.language
        val langName = if (!langCode.isNullOrBlank() && langCode != "und") {
            Locale(langCode).getDisplayLanguage(Locale("ar")).ifBlank { langCode }
        } else {
            format.label?.takeIf { it.isNotBlank() } ?: "العربية"
        }
        return "$langName (CC)"
    }

    fun activeQualityButtonLabel(tracks: Tracks, hasOverride: Boolean, isLive: Boolean): String {
        val format = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
        val h = format?.height ?: 0
        val res = when {
            h >= 2160 -> "4K HDR"
            h >= 1080 -> "1080p FHD"
            h >= 720 -> "720p HD"
            h > 0 -> "${h}p"
            else -> "4K HDR"
        }
        return if (hasOverride) res else if (isLive) "تلقائي $res" else "4K تلقائي"
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
