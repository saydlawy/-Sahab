package com.saydlawy.sahab.feature.instantwatch.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        exoPlayer.setMediaItem(builder.build())
        if (resumePositionMs > 0L) {
            exoPlayer.seekTo(resumePositionMs)
        }
        exoPlayer.prepare()
    }

    // Fast tick for real-time subtitle synchronization
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250L)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError(error.message ?: "حدث خطأ أثناء تشغيل الفيديو في Media3.")
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

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = true
                keepScreenOn = true
                player = exoPlayer
            }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier,
    )
}
