package com.saydlawy.sahab.feature.instantwatch.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedSubtitleTrack
import kotlinx.coroutines.delay
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val OSD_HIDE_DELAY_MS = 4_000L

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

/**
 * Native Media3 player with a TV-friendly D-pad contract:
 * - OK / Play-Pause toggles playback
 * - LEFT / RIGHT seek by 10 seconds
 * - UP hands focus back to the player top bar ([onRequestExitFocus])
 * - DOWN just reveals the on-screen progress bar
 * The stock PlayerView controller is disabled because it is not D-pad friendly.
 */
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
) {
    val context = LocalContext.current
    val exoPlayer = remember(context, headers) {
        val userAgent = headers["User-Agent"] ?: headers["user-agent"]
            ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val cleanHeaders = headers.filterKeys {
            !it.equals("User-Agent", ignoreCase = true)
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (cleanHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(cleanHeaders)
                }
            }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                playWhenReady = true
            }
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

    // Fast tick for real-time subtitle synchronization + OSD progress
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250L)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            onPositionTick(pos)
        }
    }

    // Periodic progress persistence
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

    // Take D-pad focus as soon as the native player appears so OK / arrows control playback.
    LaunchedEffect(exoPlayer) {
        delay(300L)
        runCatching { surfaceFocus.requestFocus() }
    }

    // Auto-hide the progress OSD while playing.
    LaunchedEffect(osdPing) {
        osdVisible = true
        delay(OSD_HIDE_DELAY_MS)
        if (exoPlayer.isPlaying) osdVisible = false
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error.message ?: "حدث خطأ أثناء تشغيل الفيديو في Media3.")
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
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
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // The stock controller is not D-pad friendly; a Compose OSD is drawn below instead.
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (osdVisible || !isPlaying) {
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
