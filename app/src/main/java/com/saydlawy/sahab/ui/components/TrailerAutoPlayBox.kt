package com.saydlawy.sahab.ui.components

import android.view.Surface
import androidx.annotation.OptIn
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun TrailerAutoPlayBox(
    trailerUrl: String,
    posterUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    autoPlayDelayMs: Long = 350L,
    startMuted: Boolean = true,
) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val isTv = LocalIsTv.current
    val cleanUrl = trailerUrl.trim()
    val youtubeVideoId = remember(cleanUrl) { extractYouTubeVideoId(cleanUrl) }
    val playableUrl = cleanUrl.takeIf { url ->
        youtubeVideoId == null &&
            (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
    }
    val hasMediaSource = playableUrl != null || youtubeVideoId != null

    var shouldPlay by remember(cleanUrl) { mutableStateOf(false) }
    var videoReady by remember(cleanUrl) { mutableStateOf(false) }
    var muted by remember(cleanUrl) { mutableStateOf(startMuted) }
    var player by remember(cleanUrl) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(cleanUrl, isTv) {
        shouldPlay = false
        videoReady = false
        if (!hasMediaSource) return@LaunchedEffect
        val effectiveDelayMs = if (isTv) maxOf(autoPlayDelayMs, 2_000L) else autoPlayDelayMs
        delay(effectiveDelayMs)
        shouldPlay = true
    }

    LaunchedEffect(shouldPlay, playableUrl) {
        if (!shouldPlay || playableUrl == null) {
            player?.let { existing ->
                player = null
                SahabPlayer.releasePlayer(existing)
            }
            return@LaunchedEffect
        }

        val created = runCatching {
            SahabPlayer.create(
                context = context,
                client = container.client,
                isLive = false,
                isPreview = true,
            ).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        videoReady = true
                    }
                })
                setMediaItem(MediaItem.fromUri(playableUrl))
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
        player = created
    }

    LaunchedEffect(muted, player) {
        player?.volume = if (muted) 0f else 1f
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        shouldPlay = false
        videoReady = false
        val existing = player
        player = null
        SahabPlayer.releasePlayer(existing)
    }

    DisposableEffect(cleanUrl) {
        onDispose {
            val existing = player
            player = null
            SahabPlayer.releasePlayer(existing)
        }
    }

    Box(
        modifier = modifier
            .background(Palette.CinematicBg)
            .then(
                if (!isTv && hasMediaSource && !shouldPlay) {
                    Modifier.clickable { shouldPlay = true }
                } else {
                    Modifier
                },
            ),
    ) {
        val activePlayer = player
        if (shouldPlay && activePlayer != null && playableUrl != null) {
            AndroidEmbeddedExternalSurface(
                modifier = Modifier.fillMaxSize(),
            ) {
                onSurface { surface: Surface, _, _ ->
                    activePlayer.setVideoSurface(surface)
                    surface.onDestroyed {
                        if (player === activePlayer) {
                            activePlayer.clearVideoSurface(surface)
                        }
                    }
                }
            }
        } else if (shouldPlay && youtubeVideoId != null) {
            YouTubeEmbedPlayer(
                videoId = youtubeVideoId,
                muted = muted,
                loop = true,
                showControls = false,
                onPlaying = { videoReady = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!videoReady) {
            RemoteImage(
                url = posterUrl,
                fallbackUrl = posterUrl,
                fallbackText = title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (shouldPlay && hasMediaSource) {
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Text(
                    text = if (muted) "🔇" else "🔊",
                    color = Color.White,
                )
            }
        }
    }
}
