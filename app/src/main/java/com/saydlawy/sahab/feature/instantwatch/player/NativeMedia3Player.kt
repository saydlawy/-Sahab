package com.saydlawy.sahab.feature.instantwatch.player

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedSubtitleTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val OSD_HIDE_DELAY_MS = 4_000L
private const val TV_SAFE_BUFFER_BYTES = 64 * 1024 * 1024

data class MediaTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

enum class VideoResizeMode(val labelAr: String) {
    FIT("ملائمة"),
    ZOOM("تكبير"),
    FILL("ملء الشاشة"),
}

private fun isRemoteKey(key: Key): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter ||
        key == Key.MediaPlayPause || key == Key.MediaPlay || key == Key.MediaPause ||
        key == Key.DirectionLeft || key == Key.DirectionRight ||
        key == Key.MediaRewind || key == Key.MediaFastForward ||
        key == Key.DirectionUp || key == Key.DirectionDown

private fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatLanguageLabel(lang: String?, fallbackLabel: String?, index: Int): String {
    val cleanLabel = fallbackLabel?.trim().orEmpty()
    val code = lang?.trim()?.lowercase().orEmpty()
    val langName = when (code) {
        "ar", "ara" -> "العربية"
        "en", "eng" -> "English"
        "fr", "fre", "fra" -> "Français"
        "es", "spa" -> "Español"
        "de", "ger", "deu" -> "Deutsch"
        "tr", "tur" -> "Türkçe"
        "hi", "hin" -> "हिन्दी"
        "" -> ""
        else -> code.uppercase()
    }
    return when {
        langName.isNotBlank() && cleanLabel.isNotBlank() && !cleanLabel.equals(langName, true) ->
            "$langName • $cleanLabel"
        langName.isNotBlank() -> langName
        cleanLabel.isNotBlank() -> cleanLabel
        else -> "مسار #${index + 1}"
    }
}

class Media3PlayerController {
    internal var onToggle: (() -> Unit)? = null
    internal var onSeekBy: ((Int) -> Unit)? = null
    internal var onSelectAudio: ((groupIndex: Int, trackIndex: Int) -> Unit)? = null
    internal var onSelectSubtitle: ((groupIndex: Int, trackIndex: Int) -> Unit)? = null
    internal var onDisableEmbeddedSubtitle: (() -> Unit)? = null
    internal var onCycleResizeMode: (() -> Unit)? = null

    private val _audioTracks = MutableStateFlow<List<MediaTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<MediaTrackOption>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<MediaTrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<MediaTrackOption>> = _subtitleTracks.asStateFlow()

    private val _resizeMode = MutableStateFlow(VideoResizeMode.FIT)
    val resizeMode: StateFlow<VideoResizeMode> = _resizeMode.asStateFlow()

    internal fun updateTracks(audio: List<MediaTrackOption>, subtitles: List<MediaTrackOption>) {
        _audioTracks.value = audio
        _subtitleTracks.value = subtitles
    }

    internal fun updateResizeMode(mode: VideoResizeMode) {
        _resizeMode.value = mode
    }

    fun togglePlayback() = onToggle?.invoke() ?: Unit
    fun seekBy(deltaSec: Int) = onSeekBy?.invoke(deltaSec) ?: Unit
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) = onSelectAudio?.invoke(groupIndex, trackIndex) ?: Unit
    fun selectEmbeddedSubtitle(groupIndex: Int, trackIndex: Int) = onSelectSubtitle?.invoke(groupIndex, trackIndex) ?: Unit
    fun disableEmbeddedSubtitle() = onDisableEmbeddedSubtitle?.invoke() ?: Unit
    fun cycleResizeMode() = onCycleResizeMode?.invoke() ?: Unit
}

@OptIn(UnstableApi::class)
@Composable
fun NativeMedia3Player(
    streamUrl: String,
    subtitle: ResolvedSubtitleTrack?,
    resumePositionMs: Long,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onPlaybackError: (String) -> Unit,
    modifier: Modifier = Modifier,
    headers: Map<String, String> = emptyMap(),
    onPositionTick: (Long) -> Unit = {},
    onRequestExitFocus: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    controller: Media3PlayerController? = null,
    showInternalOsd: Boolean = true,
    onToggleOverlay: (() -> Unit)? = null,
    onPlaybackStateChanged: ((isPlaying: Boolean, durationMs: Long) -> Unit)? = null,
    preferArabicEmbeddedSubtitles: Boolean = true,
    disableEmbeddedSubtitles: Boolean = false,
) {
    val context = LocalContext.current
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    val exoPlayer = remember(context, headers) {
        val userAgent = headers["User-Agent"] ?: headers["user-agent"]
            ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val cleanHeaders = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .apply {
                if (cleanHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(cleanHeaders)
                }
            }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 2_500, 5_000)
            .setTargetBufferBytes(TV_SAFE_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguages("ar", "ara")
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferArabicEmbeddedSubtitles && disableEmbeddedSubtitles),
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer, disableEmbeddedSubtitles) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disableEmbeddedSubtitles)
            .build()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var osdVisible by remember { mutableStateOf(true) }
    var osdPing by remember { mutableIntStateOf(0) }
    val surfaceFocus = focusRequester

    LaunchedEffect(streamUrl, subtitle?.url, exoPlayer) {
        val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
        if (subtitle != null && subtitle.url.isNotBlank()) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        val targetPosMs = exoPlayer.currentPosition.takeIf { it > 0L } ?: resumePositionMs
        exoPlayer.setMediaItem(builder.build())
        if (targetPosMs > 0L) {
            exoPlayer.seekTo(targetPosMs)
        }
        exoPlayer.prepare()
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250L)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            onPositionTick(pos)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5_000L)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            if (pos > 0L) {
                onProgressUpdate(pos, dur)
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        delay(300L)
        runCatching { surfaceFocus.requestFocus() }
    }

    LaunchedEffect(osdPing) {
        osdVisible = true
        delay(OSD_HIDE_DELAY_MS)
        if (exoPlayer.isPlaying) osdVisible = false
    }

    DisposableEffect(exoPlayer, controller) {
        fun publishTracks(tracks: Tracks) {
            val audioList = mutableListOf<MediaTrackOption>()
            val subList = mutableListOf<MediaTrackOption>()
            tracks.groups.forEachIndexed { gIdx, group ->
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        for (tIdx in 0 until group.length) {
                            if (!group.isTrackSupported(tIdx)) continue
                            val fmt = group.getTrackFormat(tIdx)
                            val ch = if (fmt.channelCount > 2) " ${fmt.channelCount}ch" else ""
                            val base = formatLanguageLabel(fmt.language, fmt.label, audioList.size)
                            audioList += MediaTrackOption(
                                groupIndex = gIdx,
                                trackIndex = tIdx,
                                label = "$base$ch",
                                language = fmt.language,
                                isSelected = group.isTrackSelected(tIdx),
                            )
                        }
                    }
                    C.TRACK_TYPE_TEXT -> {
                        for (tIdx in 0 until group.length) {
                            val fmt = group.getTrackFormat(tIdx)
                            subList += MediaTrackOption(
                                groupIndex = gIdx,
                                trackIndex = tIdx,
                                label = formatLanguageLabel(fmt.language, fmt.label, subList.size),
                                language = fmt.language,
                                isSelected = group.isTrackSelected(tIdx),
                            )
                        }
                    }
                }
            }
            controller?.updateTracks(audioList, subList)
        }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error.message ?: "حدث خطأ أثناء تشغيل الفيديو في Media3.")
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged?.invoke(playing, exoPlayer.duration.takeIf { it > 0L } ?: 0L)
            }

            override fun onTracksChanged(tracks: Tracks) {
                publishTracks(tracks)
            }
        }
        exoPlayer.addListener(listener)
        publishTracks(exoPlayer.currentTracks)

        onDispose {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            if (pos > 0L) {
                onProgressUpdate(pos, dur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun seekBy(delta: Long) {
        val dur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        exoPlayer.seekTo((exoPlayer.currentPosition + delta).coerceIn(0L, dur))
    }

    DisposableEffect(exoPlayer, controller) {
        controller?.onToggle = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        controller?.onSeekBy = { deltaSec ->
            seekBy(deltaSec * 1_000L)
        }
        controller?.onSelectAudio = { gIdx, tIdx ->
            val group = exoPlayer.currentTracks.groups.getOrNull(gIdx)
            if (group != null && group.type == C.TRACK_TYPE_AUDIO) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                    .build()
            }
        }
        controller?.onSelectSubtitle = { gIdx, tIdx ->
            val group = exoPlayer.currentTracks.groups.getOrNull(gIdx)
            if (group != null && group.type == C.TRACK_TYPE_TEXT) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                    .build()
            }
        }
        controller?.onDisableEmbeddedSubtitle = {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        controller?.onCycleResizeMode = {
            val modes = VideoResizeMode.entries
            val next = modes[(resizeMode.ordinal + 1) % modes.size]
            resizeMode = next
            controller?.updateResizeMode(next)
        }
        onDispose {
            controller?.onToggle = null
            controller?.onSeekBy = null
            controller?.onSelectAudio = null
            controller?.onSelectSubtitle = null
            controller?.onDisableEmbeddedSubtitle = null
            controller?.onCycleResizeMode = null
        }
    }

    val media3ResizeMode = when (resizeMode) {
        VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    Box(
        modifier = modifier
            .focusRequester(surfaceFocus)
            .onPreviewKeyEvent { e ->
                if (!isRemoteKey(e.key)) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            if (e.nativeKeyRepeatCount() == 0) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                        Key.MediaPlay -> exoPlayer.play()
                        Key.MediaPause -> exoPlayer.pause()
                        Key.DirectionLeft, Key.MediaRewind -> seekBy(-SEEK_STEP_MS)
                        Key.DirectionRight, Key.MediaFastForward -> seekBy(SEEK_STEP_MS)
                        Key.DirectionUp -> onRequestExitFocus()
                        else -> Unit
                    }
                    osdPing++
                }
                true
            }
            .pointerInput(onToggleOverlay) {
                detectTapGestures(
                    onTap = {
                        if (onToggleOverlay != null) {
                            onToggleOverlay()
                        } else {
                            osdVisible = !osdVisible
                            if (osdVisible) osdPing++
                        }
                    },
                )
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                    this.resizeMode = media3ResizeMode
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(
                            CaptionStyleCompat(
                                AndroidColor.WHITE,
                                0x88000000.toInt(),
                                AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                AndroidColor.BLACK,
                                null,
                            ),
                        )
                        setFractionalTextSize(0.058f)
                    }
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                if (view.resizeMode != media3ResizeMode) {
                    view.resizeMode = media3ResizeMode
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showInternalOsd && (osdVisible || !isPlaying)) {
            val fraction = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x55FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(Color(0xFF38BDF8)),
                    )
                }
                Text(
                    text = "◀ ▶ تقديم/تأخير 10 ثوانٍ  •  OK تشغيل/إيقاف  •  ▲ شريط الأدوات",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.nativeKeyRepeatCount(): Int =
    this.nativeKeyEvent.repeatCount
