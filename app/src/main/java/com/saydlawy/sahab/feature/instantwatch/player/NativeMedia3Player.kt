package com.saydlawy.sahab.feature.instantwatch.player

import android.net.Uri
import android.view.ViewGroup
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedSubtitleTrack
import kotlinx.coroutines.delay

@Composable
fun NativeMedia3Player(
    streamUrl: String,
    subtitle: ResolvedSubtitleTrack?,
    resumePositionMs: Long,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onPlaybackError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(streamUrl, subtitle?.url) {
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
                player = exoPlayer
                useController = true
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
