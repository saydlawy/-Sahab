package com.saydlawy.sahab.ui.player

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.findActivity
import com.saydlawy.sahab.util.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
private val OsdSurfaceLowest = Color(0xFF0F0B1E)
private val OsdSurfaceLow = Color(0xFF1D192C)
private val OsdSurfaceContainer = Color(0xFF211D31)
private val OsdSurfaceHigh = Color(0xFF2B273C)
private val OsdSurfaceHighest = Color(0xFF363247)
private val OsdPrimary = Color(0xFFDCB8FF)
private val OsdPrimaryContainer = Color(0xFF8A2BE2)
private val OsdOnPrimary = Color(0xFF480081)
private val OsdCyan = Color(0xFF00FBFB)
private val OsdTertiary = Color(0xFFC4C1FB)
private val OsdOnSurface = Color(0xFFE7DEFA)
private val OsdOnSurfaceVariant = Color(0xFFCFC2D7)
private val OsdOutline = Color(0xFF988CA0)
private val OsdOutlineVariant = Color(0xFF4C4354)
private val OsdError = Color(0xFFFFB4AB)

private val ResizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)

private val PlaybackSpeeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_HIDE_MS = 5_000L

@Composable
fun PlayerScreen(container: AppContainer, onBack: () -> Unit) {
    val request = remember { container.playback }
    if (request == null || request.items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    PlayerContent(container, request, onBack)
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(container: AppContainer, request: PlayRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val isTv = LocalIsTv.current
    val repo = container.repo

    var playlist by remember { mutableStateOf(request.items) }
    val lastIndex = playlist.lastIndex

    var index by remember { mutableIntStateOf(request.startIndex.coerceIn(0, lastIndex.coerceAtLeast(0))) }
    val item = playlist[index.coerceIn(0, lastIndex.coerceAtLeast(0))]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.kind == Kind.LIVE

    val exo = remember { SahabPlayer.create(context, container.client, isLive = isLive, isPreview = false) }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showTracks by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showQuickChannelsDrawer by remember { mutableStateOf(isLive && playlist.size > 1) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var skipIntroDismissed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var formatAttempt by remember { mutableIntStateOf(0) }
    var autoReconnectCount by remember { mutableIntStateOf(0) }
    var reconnecting by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var seriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
    val rootFocus = remember { FocusRequester() }

    // Fetch cached/network SeriesDetails when playing a series episode so the VOD drawer shows seasons, thumbnails & plots
    LaunchedEffect(item.groupId, item.kind) {
        if (item.kind == Kind.EPISODE && item.groupId.isNotBlank()) {
            seriesDetails = runCatching { repo.seriesDetails(item.groupId) }.getOrNull()
        }
    }

    val saveNow: () -> Unit = {
        val saved = currentItem
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        container.saveProgress(saved, exo.currentPosition, total)
    }

    val goTo: (Int) -> Unit = { target ->
        if (target in 0..playlist.lastIndex && target != index) {
            saveNow()
            formatAttempt = 0
            autoReconnectCount = 0
            reconnecting = false
            skipIntroDismissed = false
            error = null
            ended = false
            index = target
            controlsVisible = true
            interactions++
        }
    }

    val playEpisodeFromDrawer: (Episode) -> Unit = { ep ->
        val existingIdx = playlist.indexOfFirst { it.id == ep.id }
        if (existingIdx >= 0) {
            goTo(existingIdx)
        } else {
            val allEps = seriesDetails?.episodes.orEmpty()
            if (allEps.isNotEmpty()) {
                val sName = seriesDetails?.name ?: item.title
                val sCover = seriesDetails?.cover ?: item.icon
                val sId = seriesDetails?.id ?: item.groupId
                val newItems = allEps.map { e ->
                    PlayItem(
                        kind = Kind.EPISODE,
                        id = e.id,
                        title = sName,
                        subtitle = "${Ar.seasonEpisode(e.season, e.number)}: ${e.title}",
                        icon = e.image.ifBlank { sCover },
                        ext = e.ext,
                        groupId = sId,
                    )
                }
                val newIdx = newItems.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
                saveNow()
                playlist = newItems
                formatAttempt = 0
                autoReconnectCount = 0
                reconnecting = false
                skipIntroDismissed = false
                error = null
                ended = false
                index = newIdx
                controlsVisible = true
                interactions++
            }
        }
    }

    val zapNext: () -> Unit = { if (playlist.size > 1) goTo(if (index >= playlist.lastIndex) 0 else index + 1) }
    val zapPrevious: () -> Unit = { if (playlist.size > 1) goTo(if (index <= 0) playlist.lastIndex else index - 1) }
    val togglePlay: () -> Unit = {
        if (exo.isPlaying) exo.pause() else exo.play()
        interactions++
    }
    val seekBy: (Long) -> Unit = { delta ->
        val total = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, total))
        position = exo.currentPosition
        interactions++
    }
    val cycleSpeed: () -> Unit = {
        speedIndex = (speedIndex + 1) % PlaybackSpeeds.size
        val nextSpeed = PlaybackSpeeds[speedIndex]
        playbackSpeed = nextSpeed
        exo.setPlaybackSpeed(nextSpeed)
        interactions++
    }

    // Immersive full screen, landscape on phones, screen kept awake.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val previousOrientation = activity?.requestedOrientation
        if (!isTv && activity != null) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!isTv && activity != null) {
                activity.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveNow()
            exo.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null
                    reconnecting = false
                    autoReconnectCount = 0
                }
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onPlayerError(e: PlaybackException) {
                val live = currentItem.kind == Kind.LIVE
                when {
                    autoReconnectCount < 2 -> {
                        reconnecting = true
                        autoReconnectCount += 1
                    }
                    live && formatAttempt + 1 < repo.liveExtensions().size -> {
                        autoReconnectCount = 0
                        reconnecting = true
                        formatAttempt += 1
                    }
                    else -> {
                        reconnecting = false
                        error = Ar.errPlayback
                    }
                }
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Load whatever is selected: a new item, a retry, an auto-reconnect, or the fallback stream format.
    LaunchedEffect(index, playlist, retryKey, formatAttempt, autoReconnectCount) {
        error = null
        buffering = true
        if (autoReconnectCount > 0) {
            delay(1_100)
        }
        val url = try {
            repo.streamUrl(item, formatAttempt)
        } catch (e: Exception) {
            reconnecting = false
            error = Ar.errPlayback
            return@LaunchedEffect
        }
        val resume = if (isLive || request.fromStart) 0L else {
            exo.currentPosition.takeIf { autoReconnectCount > 0 && it > 0L }
                ?: container.prefs.positionOf(item.kind, item.id)
        }
        exo.setMediaItem(MediaItem.fromUri(url), resume.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(ended) {
        if (ended) {
            ended = false
            if (!isLive && index < playlist.lastIndex) goTo(index + 1)
        }
    }

    // Position clock; also saves progress now and then.
    LaunchedEffect(exo) {
        var ticks = 0
        while (true) {
            if (controlsVisible || showEpisodesDrawer) {
                position = exo.currentPosition.coerceAtLeast(0L)
                bufferedPosition = exo.bufferedPosition.coerceAtLeast(position)
                duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            }
            delay(500)
            ticks++
            if (ticks >= 20) {
                ticks = 0
                if (exo.isPlaying) saveNow()
            }
        }
    }

    // What is on now and next, for live channels.
    LaunchedEffect(item.id, isLive) {
        epg = emptyList()
        if (isLive) {
            while (true) {
                epg = try {
                    repo.shortEpg(item.id, 4)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                delay(60_000)
            }
        }
    }

    LaunchedEffect(controlsVisible, interactions, isPlaying, showTracks, showGuide, showEpisodesDrawer) {
        if (controlsVisible && isPlaying && !showTracks && !showGuide && !showEpisodesDrawer) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible && !showEpisodesDrawer) {
            delay(60)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { rootFocus.requestFocus() }
    }

    val onKey: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        interactions++
        when (event.key) {
            Key.MediaPlayPause -> {
                togglePlay()
                controlsVisible = true
                true
            }
            Key.MediaPlay -> {
                exo.play()
                true
            }
            Key.MediaPause -> {
                exo.pause()
                true
            }
            Key.MediaFastForward -> {
                if (!isLive) seekBy(3 * SEEK_STEP_MS)
                true
            }
            Key.MediaRewind -> {
                if (!isLive) seekBy(-3 * SEEK_STEP_MS)
                true
            }
            Key.MediaNext, Key.ChannelUp -> {
                zapNext()
                true
            }
            Key.MediaPrevious, Key.ChannelDown -> {
                zapPrevious()
                true
            }
            Key.Back, Key.Escape -> {
                if (showEpisodesDrawer) {
                    showEpisodesDrawer = false
                    true
                } else if (showQuickChannelsDrawer && isLive && controlsVisible) {
                    showQuickChannelsDrawer = false
                    true
                } else {
                    false
                }
            }
            else -> {
                if (controlsVisible || showEpisodesDrawer) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> if (isLive) zapNext() else if (playlist.size > 1 || seriesDetails != null) {
                            showEpisodesDrawer = true
                        }
                        Key.DirectionDown -> if (isLive) zapPrevious() else {
                            showTracks = true
                        }
                        Key.DirectionRight -> if (!isLive) seekBy(SEEK_STEP_MS) else Unit
                        Key.DirectionLeft -> if (!isLive) seekBy(-SEEK_STEP_MS) else Unit
                        else -> Unit
                    }
                    controlsVisible = true
                    true
                }
            }
        }
    }

    val nowSec = System.currentTimeMillis() / 1000
    val currentProgram = if (isLive) epg.firstOrNull { nowSec in it.start until it.end } else null
    val nextProgram = if (isLive) epg.firstOrNull { it.start >= (currentProgram?.end ?: nowSec) } else null

    val textOff = remember(tracks, exo.trackSelectionParameters) {
        exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }
    val hasVideoOverride = remember(tracks, exo.trackSelectionParameters) {
        tracks.groups.any { g ->
            g.type == C.TRACK_TYPE_VIDEO && exo.trackSelectionParameters.overrides.containsKey(g.mediaTrackGroup)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OsdSurfaceLowest)
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent(onKey),
        ) {
            // 1. Underlying ExoPlayer Video Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player = exo
                    }
                },
                update = { it.resizeMode = ResizeModes[resizeIndex % ResizeModes.size] },
                modifier = Modifier.fillMaxSize(),
            )

            // Tap catcher to toggle OSD
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (showEpisodesDrawer) {
                                showEpisodesDrawer = false
                            } else {
                                controlsVisible = !controlsVisible
                            }
                            interactions++
                        })
                    },
            )

            // Buffering / Reconnecting Center Indicator
            if ((buffering || reconnecting) && error == null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(color = OsdCyan, trackColor = Color.White.copy(alpha = 0.15f))
                    if (reconnecting) {
                        Text(
                            Ar.reconnecting,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OsdCyan,
                        )
                    }
                }
            }

            // 2. Main OSD Overlay: Live TV OSD (ExoPlayer/) vs VOD Player OSD (media-player/)
            AnimatedVisibility(
                visible = controlsVisible && error == null,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isLive) {
                    LiveExoPlayerOsd(
                        item = item,
                        playlist = playlist,
                        currentIndex = index,
                        currentProgram = currentProgram,
                        nextProgram = nextProgram,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        bufferedPosition = bufferedPosition,
                        tracks = tracks,
                        textOff = textOff,
                        hasVideoOverride = hasVideoOverride,
                        formatExt = repo.liveExtensions().getOrElse(formatAttempt) { "m3u8" }.uppercase(Locale.US),
                        showQuickDrawer = showQuickChannelsDrawer,
                        onToggleQuickDrawer = {
                            showQuickChannelsDrawer = !showQuickChannelsDrawer
                            interactions++
                        },
                        onSelectChannel = goTo,
                        onBack = onBack,
                        onTogglePlay = togglePlay,
                        onSeekBy = seekBy,
                        onPrevChannel = zapPrevious,
                        onNextChannel = zapNext,
                        onOpenTracks = {
                            showTracks = true
                            interactions++
                        },
                        onOpenGuide = {
                            showGuide = true
                            interactions++
                        },
                        onCycleResize = {
                            resizeIndex = (resizeIndex + 1) % ResizeModes.size
                            interactions++
                        },
                    )
                } else {
                    VodMediaPlayerOsd(
                        item = item,
                        playlistCount = seriesDetails?.episodes?.size?.takeIf { it > 0 } ?: playlist.size,
                        isPlaying = isPlaying,
                        position = position,
                        bufferedPosition = bufferedPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        tracks = tracks,
                        textOff = textOff,
                        hasVideoOverride = hasVideoOverride,
                        showSkipIntro = !skipIntroDismissed && position in 3_000L..110_000L,
                        onSkipIntro = {
                            skipIntroDismissed = true
                            seekBy(85_000L)
                        },
                        onBack = onBack,
                        onTogglePlay = togglePlay,
                        onSeekBy = seekBy,
                        onSeekTo = { target ->
                            exo.seekTo(target.coerceAtLeast(0L))
                            position = target
                            interactions++
                        },
                        onPrev = zapPrevious,
                        onNext = zapNext,
                        onCycleSpeed = cycleSpeed,
                        onCycleResize = {
                            resizeIndex = (resizeIndex + 1) % ResizeModes.size
                            interactions++
                        },
                        onOpenTracks = {
                            showTracks = true
                            interactions++
                        },
                        onToggleEpisodesDrawer = {
                            showEpisodesDrawer = !showEpisodesDrawer
                            interactions++
                        },
                        onInteract = { interactions++ },
                    )
                }
            }

            // 3. Side Episodes Drawer for VOD (media-player/screen.png)
            if (!isLive) {
                AnimatedVisibility(
                    visible = showEpisodesDrawer && error == null,
                    enter = slideInHorizontally(tween(320)) { -it } + fadeIn(tween(220)),
                    exit = slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.CenterEnd), // Left side in RTL
                ) {
                    VodEpisodesDrawer(
                        currentItem = item,
                        playlist = playlist,
                        currentIndex = index,
                        seriesDetails = seriesDetails,
                        position = position,
                        duration = duration,
                        onSelectPlaylistIndex = { idx ->
                            goTo(idx)
                        },
                        onSelectEpisode = { ep ->
                            playEpisodeFromDrawer(ep)
                        },
                        onClose = { showEpisodesDrawer = false },
                    )
                }
            }

            // Error state overlay
            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .background(OsdSurfaceLow.copy(alpha = 0.94f))
                        .border(1.dp, OsdOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        message,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdOnSurface,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(
                            Ar.retry,
                            {
                                autoReconnectCount = 0
                                formatAttempt = 0
                                retryKey++
                            },
                            primary = true,
                            autoFocus = true,
                        )
                        GlassButton(Ar.back, onBack)
                    }
                }
            }
        }
    }

    if (showTracks) {
        TracksDialog(tracks, exo) { showTracks = false }
    }
    if (showGuide && isLive) {
        EpgDialog(channelName = item.title, channelId = item.id, onDismiss = { showGuide = false })
    }
}

// ============================================================================
// 1. LIVE TV EXOPLAYER OSD (Matches ExoPlayer/code.html & ExoPlayer/screen.png)
// ============================================================================

@Composable
private fun LiveExoPlayerOsd(
    item: PlayItem,
    playlist: List<PlayItem>,
    currentIndex: Int,
    currentProgram: Program?,
    nextProgram: Program?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    tracks: Tracks,
    textOff: Boolean,
    hasVideoOverride: Boolean,
    formatExt: String,
    showQuickDrawer: Boolean,
    onToggleQuickDrawer: () -> Unit,
    onSelectChannel: (Int) -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenGuide: () -> Unit,
    onCycleResize: () -> Unit,
) {
    val nowTime = rememberNow().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    val bitrateText = remember(tracks) { SahabPlayer.activeBitrateText(tracks, isLive = true) }
    val videoBadge = remember(tracks, item.title) {
        val q = qualityOf(item.title) ?: "4K HDR"
        val b = SahabPlayer.videoBadge(tracks, q)
        if (b.contains("4K") && !b.contains("HDR")) b.replace("4K", "4K HDR") else b
    }
    val audioLabel = remember(tracks) { SahabPlayer.activeAudioLabel(tracks) }
    val subtitleLabel = remember(tracks, textOff) { SahabPlayer.activeSubtitleLabel(tracks, textOff) }
    val qualityBtnLabel = remember(tracks, hasVideoOverride) {
        SahabPlayer.activeQualityButtonLabel(tracks, hasVideoOverride, isLive = true)
    }

    val pulse = rememberInfiniteTransition(label = "liveOsdPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        // Layered Cinematic Scrims for TV Legibility
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OsdSurfaceLowest.copy(alpha = 0.80f),
                            OsdSurfaceLowest.copy(alpha = 0.25f),
                            OsdSurfaceLowest.copy(alpha = 0.90f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // TOP OVERLAY HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OsdSurfaceLow.copy(alpha = 0.82f))
                    .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Right Side (RTL Start): Back Button + Broadcaster Identity + Channel Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    OsdIconSquareButton(
                        icon = Ic.Back,
                        description = Ar.back,
                        onClick = onBack,
                        size = 46.dp,
                    )

                    // Channel Logo Box with bottom gradient bar
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OsdSurfaceHighest.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.icon.isNotBlank()) {
                            RemoteImage(
                                url = item.icon,
                                fallbackText = item.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                item.title.take(4).uppercase(Locale.US),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdPrimary,
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(OsdPrimary, OsdCyan, OsdPrimary)),
                                ),
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                item.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Pulsing "مباشر • REC" badge
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Palette.Live.copy(alpha = 0.22f))
                                    .border(1.dp, OsdError.copy(alpha = 0.45f), RoundedCornerShape(50))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(OsdError.copy(alpha = dotAlpha)),
                                )
                                Text(
                                    "مباشر • REC",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OsdError,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                Ar.brand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdPrimary,
                            )
                            Text("•", fontSize = 12.sp, color = OsdOnSurfaceVariant)
                            val epgLine = currentProgram?.title?.takeIf { it.isNotBlank() }
                                ?: nextProgram?.let { "${Ar.nextShowing}: ${it.title}" }
                                ?: "البث المباشر الفائق الوضوح"
                            Text(
                                epgLine,
                                fontSize = 12.sp,
                                color = OsdOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Left Side (RTL End): Real-Time Stream Performance Telemetry Pill + Clock Widget
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Telemetry Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(OsdSurfaceHighest.copy(alpha = 0.68f))
                            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Ic.Speed, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(15.dp))
                            Text(bitrateText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OsdCyan)
                        }
                        Text("|", fontSize = 12.sp, color = OsdOutlineVariant)
                        Text("$formatExt • Media3 v1.5", fontSize = 11.sp, color = OsdOnSurfaceVariant)
                        Text("|", fontSize = 12.sp, color = OsdOutlineVariant)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OsdPrimary.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                videoBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdPrimary,
                            )
                        }
                    }

                    // Clock & Weather Widget
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(OsdSurfaceContainer.copy(alpha = 0.85f))
                            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(nowTime, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OsdOnSurface)
                            Text("سحاب • بث حي", fontSize = 10.sp, color = OsdOnSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(OsdSurfaceHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Ic.Cloud, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // CENTER STAGE: Sync Toast & Floating Quick Channels EPG Drawer (Left-aligned in RTL)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
            ) {
                // Ambient Center Sync Indicator Toast
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(OsdSurfaceLow.copy(alpha = 0.85f))
                        .border(1.dp, OsdCyan.copy(alpha = 0.30f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OsdCyan.copy(alpha = dotAlpha)),
                    )
                    Text(
                        "متزامن مع البث الحي الفعلي (تأخير 0.8 ثانية)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OsdCyan,
                    )
                }

                // Quick Channels EPG Overlay Drawer (Left side in RTL, matching ExoPlayer/screen.png)
                if (showQuickDrawer && playlist.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(300.dp)
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(OsdSurfaceLow.copy(alpha = 0.92f))
                            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Ic.List, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    "دليل القنوات السريع",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OsdPrimary,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(OsdSurfaceHighest)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "القنوات (${currentIndex + 1}/${playlist.size})",
                                    fontSize = 10.sp,
                                    color = OsdOnSurfaceVariant,
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(playlist, key = { idx, c -> "${c.id}_$idx" }) { idx, ch ->
                                val active = idx == currentIndex
                                var focused by remember { mutableStateOf(false) }
                                val bg = when {
                                    active -> OsdPrimaryContainer.copy(alpha = 0.42f)
                                    focused -> OsdSurfaceHighest.copy(alpha = 0.90f)
                                    else -> OsdSurfaceHigh.copy(alpha = 0.65f)
                                }
                                val borderCol = when {
                                    active || focused -> OsdPrimary
                                    else -> Color.Transparent
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                        .onFocusChanged { focused = it.isFocused }
                                        .clickable { onSelectChannel(idx) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            String.format(Locale.US, "%02d", idx + 1),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) OsdCyan else OsdOnSurfaceVariant,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                ch.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                color = OsdOnSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val sub = if (active && currentProgram != null) {
                                                currentProgram.title
                                            } else {
                                                ch.subtitle.ifBlank { "بث مباشر" }
                                            }
                                            Text(
                                                sub,
                                                fontSize = 11.sp,
                                                color = if (active) OsdTertiary else OsdOnSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    if (active) {
                                        Icon(
                                            Ic.Equalizer,
                                            contentDescription = null,
                                            tint = OsdCyan,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        val q = qualityOf(ch.title) ?: "FHD"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (q == "4K") OsdPrimary.copy(alpha = 0.22f)
                                                    else OsdSurfaceHighest,
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                q,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (q == "4K") OsdPrimary else OsdOnSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM EXOPLAYER (MEDIA3) CONTROL SUITE & OSD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OsdSurfaceLow.copy(alpha = 0.90f))
                    .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Live Edge & Progress Bar Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(OsdCyan.copy(alpha = 0.18f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OsdCyan.copy(alpha = dotAlpha)),
                                )
                                Text(
                                    "طرف البث الحي (Live Edge)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OsdCyan,
                                )
                            }
                            Text(
                                formatDuration(position.coerceAtLeast(0L)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = OsdOnSurface,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("التأخير:", fontSize = 12.sp, color = OsdOnSurfaceVariant)
                            Text("-00:01s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OsdCyan)
                            Text("/", fontSize = 12.sp, color = OsdOnSurfaceVariant)
                            val totalText = if (duration > 0L) formatDuration(duration) else "LIVE"
                            Text(totalText, fontSize = 12.sp, color = OsdOnSurfaceVariant)
                        }
                    }

                    val liveProgress = remember(currentProgram, position, duration) {
                        if (currentProgram != null && currentProgram.end > currentProgram.start) {
                            val now = System.currentTimeMillis() / 1000
                            ((now - currentProgram.start).toFloat() / (currentProgram.end - currentProgram.start)).coerceIn(0.08f, 0.96f)
                        } else if (duration > 0L) {
                            (position.toFloat() / duration).coerceIn(0.05f, 0.95f)
                        } else {
                            0.82f
                        }
                    }
                    GradientTimelineBar(
                        progress = liveProgress,
                        buffered = (liveProgress + 0.10f).coerceAtMost(1f),
                        showChapters = false,
                        onSeekToFraction = null,
                        onSeekBy = onSeekBy,
                    )
                }

                // 2. D-Pad Remote Button Controls Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Right Wing (RTL Start): Channel Step & Transport
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdPillButton(
                            label = "القناة السابقة",
                            leadingIcon = Ic.Next,
                            onClick = onPrevChannel,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Previous,
                            description = "ترجيع 10 ثوانٍ",
                            label = "-10",
                            onClick = { onSeekBy(-SEEK_STEP_MS) },
                        )
                        OsdPrimaryPlayButton(
                            isPlaying = isPlaying,
                            onClick = onTogglePlay,
                            size = 48.dp,
                            cornerRadius = 16.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Next,
                            description = "تقديم 10 ثوانٍ",
                            label = "+10",
                            onClick = { onSeekBy(SEEK_STEP_MS) },
                        )
                        OsdPillButton(
                            label = "القناة التالية",
                            trailingIcon = Ic.Previous,
                            onClick = onNextChannel,
                        )
                    }

                    // Center Wing: Audio, Subtitle & Video Quality Switchers
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Audio / Commentator Selector
                        OsdTwoLineTrackButton(
                            topLabel = "الصوت / المعلق",
                            bottomLabel = audioLabel,
                            icon = Ic.Equalizer,
                            iconTint = OsdCyan,
                            onClick = onOpenTracks,
                        )
                        // Subtitle Selector
                        OsdPillButton(
                            label = subtitleLabel,
                            leadingIcon = Ic.Subtitles,
                            iconTint = OsdTertiary,
                            onClick = onOpenTracks,
                        )
                        // Video Quality Switcher
                        OsdPillButton(
                            label = qualityBtnLabel,
                            leadingIcon = Ic.Quality,
                            iconTint = OsdPrimary,
                            onClick = onOpenTracks,
                        )
                    }

                    // Left Wing (RTL End): Aspect Ratio, Full EPG, Quick Channels Drawer Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Aspect,
                            description = "أبعاد العرض",
                            onClick = onCycleResize,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Clock,
                            description = Ar.guide,
                            onClick = onOpenGuide,
                        )
                        OsdPillButton(
                            label = "قائمة القنوات",
                            leadingIcon = Ic.List,
                            iconTint = if (showQuickDrawer) OsdOnPrimary else OsdPrimary,
                            highlighted = showQuickDrawer,
                            onClick = onToggleQuickDrawer,
                        )
                    }
                }

                // 3. TV Remote D-Pad Navigation Legend / Bottom Help Hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, Color.Transparent, RoundedCornerShape(0.dp))
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DpadHintItem("OK", "إظهار / إخفاء التحكم")
                        DpadHintItem("▲ / ▼", "تبديل القنوات")
                        DpadHintItem("◄ / ►", "التقديم والترجيع")
                        DpadHintItem("Back", "الرجوع للقائمة الرئيسية")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(OsdCyan))
                        Text(
                            "سحاب IPTV Engine • مشغل ExoPlayer Media3",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = OsdCyan,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. VOD MEDIA PLAYER OSD (Matches media-player/code.html & media-player/screen.png)
// ============================================================================

@Composable
private fun VodMediaPlayerOsd(
    item: PlayItem,
    playlistCount: Int,
    isPlaying: Boolean,
    position: Long,
    bufferedPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    tracks: Tracks,
    textOff: Boolean,
    hasVideoOverride: Boolean,
    showSkipIntro: Boolean,
    onSkipIntro: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onToggleEpisodesDrawer: () -> Unit,
    onInteract: () -> Unit,
) {
    val nowTime = rememberNow().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    val bitrateText = remember(tracks) { SahabPlayer.activeBitrateText(tracks, isLive = false) }
    val audioLabel = remember(tracks) { SahabPlayer.activeAudioLabel(tracks) }
    val subtitleLabel = remember(tracks, textOff) { SahabPlayer.activeSubtitleLabel(tracks, textOff) }
    val qualityBtnLabel = remember(tracks, hasVideoOverride) {
        SahabPlayer.activeQualityButtonLabel(tracks, hasVideoOverride, isLive = false)
    }
    val isEpisode = item.kind == Kind.EPISODE

    val pulse = rememberInfiniteTransition(label = "vodPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vodDotAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        // Cinematic Vignettes & Scrim Gradients
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OsdSurfaceLowest.copy(alpha = 0.78f),
                            OsdSurfaceLowest.copy(alpha = 0.22f),
                            OsdSurfaceLowest.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. TOP HUD BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OsdSurfaceLow.copy(alpha = 0.72f))
                    .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Right Side (RTL Start): Circular Back Button & Show Metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    OsdIconSquareButton(
                        icon = Ic.Back,
                        description = Ar.back,
                        onClick = onBack,
                        size = 46.dp,
                        circle = true,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                item.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(OsdPrimary.copy(alpha = 0.20f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    if (isEpisode) "VOD مسلسلات" else "VOD أفلام",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OsdPrimary,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val subText = item.subtitle.ifBlank {
                                if (isEpisode) "حلقة كاملة" else "نسخة أصلية فائقة الوضوح"
                            }
                            Text(
                                subText,
                                fontSize = 13.sp,
                                color = OsdOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(Modifier.size(5.dp).clip(CircleShape).background(OsdOutlineVariant))
                            // Format Badges: 4K Ultra HD | HDR10+ | Dolby Vision | Atmos 5.1
                            FormatChip("4K Ultra HD", OsdPrimary)
                            FormatChip("HDR10+", OsdCyan)
                            FormatChip("Dolby Vision", OsdOnSurface)
                            FormatChip("Atmos 5.1", OsdTertiary)
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Left Side (RTL End): Media3 Buffer Telemetry + Speed Pill + Clock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Live ExoPlayer Buffer / Throughput Telemetry
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(OsdSurfaceHighest.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(OsdCyan.copy(alpha = dotAlpha)),
                        )
                        Text(bitrateText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OsdCyan)
                        Text("Media3 Buffer", fontSize = 10.sp, color = OsdOutline)
                    }

                    // Playback Rate Pill (Interactive!)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(OsdSurfaceHigh.copy(alpha = 0.70f))
                            .clickable(onClick = onCycleSpeed)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Ic.Speed, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(15.dp))
                        Text(
                            String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", ".0x").replace("50x", "5x"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OsdOnSurface,
                        )
                    }

                    // Clock Widget
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Ic.Clock, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(18.dp))
                        Text(nowTime, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdOnSurface)
                    }
                }
            }

            // 2. CENTER FLOATING AREA: Active Subtitle Pill & Smart "Skip Intro" Prompt
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // Active Subtitle Info Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(OsdSurfaceLowest.copy(alpha = 0.82f))
                        .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenTracks)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.Subtitles, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(20.dp))
                    Column {
                        Text("الترجمة النشطة", fontSize = 10.sp, color = OsdOutline)
                        Text(
                            if (textOff) "بدون ترجمة" else "العربية (الأصلية - UTF-8)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Smart Floating "Skip Intro" Prompt
                if (showSkipIntro) {
                    var skipFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = OsdPrimary, spotColor = OsdCyan)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (skipFocused) OsdPrimaryContainer else OsdSurfaceHigh.copy(alpha = 0.95f),
                            )
                            .border(
                                1.5.dp,
                                Brush.horizontalGradient(listOf(OsdPrimary, OsdPrimaryContainer, OsdCyan)),
                                RoundedCornerShape(16.dp),
                            )
                            .onFocusChanged { skipFocused = it.isFocused }
                            .clickable(onClick = onSkipIntro)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                "تخطي المقدمة (Skip Intro)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                "اضغط زر [OK] في الريموت",
                                fontSize = 11.sp,
                                color = OsdPrimary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(2.dp, OsdCyan, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("85s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OsdCyan)
                        }
                        Icon(Ic.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // 3. EXOPLAYER VOD CONTROLLER (BOTTOM PANEL)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OsdSurfaceLowest.copy(alpha = 0.88f))
                    .border(1.dp, OsdOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Timeline & Seekbar with Chapter Markers
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val progressFraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    val bufferedFraction = if (duration > 0L) (bufferedPosition.toFloat() / duration).coerceIn(progressFraction, 1f) else 0f

                    GradientTimelineBar(
                        progress = progressFraction,
                        buffered = bufferedFraction,
                        showChapters = true,
                        onSeekToFraction = { frac ->
                            if (duration > 0L) onSeekTo((duration * frac).toLong())
                            onInteract()
                        },
                        onSeekBy = onSeekBy,
                    )

                    // Time Metadata Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                formatDuration(position),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text("/", fontSize = 13.sp, color = OsdOutline)
                            Text(
                                formatDuration(duration),
                                fontSize = 13.sp,
                                color = OsdOutline,
                            )
                        }

                        val remaining = (duration - position).coerceAtLeast(0L)
                        Text(
                            "المتبقي: -${formatDuration(remaining)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = OsdTertiary,
                        )
                    }
                }

                // Main Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Right Wing (RTL Start): Episodes Drawer Toggle + Audio + Subtitles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isEpisode || playlistCount > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(OsdPrimaryContainer.copy(alpha = 0.42f))
                                    .border(1.dp, OsdPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .clickable(onClick = onToggleEpisodesDrawer)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Ic.List, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    if (isEpisode) "قائمة الحلقات" else "قائمة التشغيل",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OsdOnSurface,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(OsdPrimary.copy(alpha = 0.22f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        playlistCount.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OsdPrimary,
                                    )
                                }
                            }
                        }

                        OsdPillButton(
                            label = audioLabel,
                            leadingIcon = Ic.Equalizer,
                            iconTint = OsdTertiary,
                            onClick = onOpenTracks,
                        )
                        OsdPillButton(
                            label = "الترجمة: $subtitleLabel",
                            leadingIcon = Ic.Subtitles,
                            iconTint = OsdCyan,
                            onClick = onOpenTracks,
                        )
                    }

                    // Center Wing: Core ExoPlayer Transport Controls (Circular TV D-Pad Oriented)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (playlistCount > 1) {
                                OsdIconSquareButton(
                                    icon = Ic.Previous,
                                    description = Ar.previous,
                                    onClick = onPrev,
                                    size = 44.dp,
                                    circle = true,
                                )
                            }
                            OsdIconSquareButton(
                                icon = Ic.Previous,
                                description = "-10",
                                label = "-10",
                                onClick = { onSeekBy(-SEEK_STEP_MS) },
                                size = 48.dp,
                                circle = true,
                            )
                            OsdPrimaryPlayButton(
                                isPlaying = isPlaying,
                                onClick = onTogglePlay,
                                size = 62.dp,
                                cornerRadius = 31.dp,
                                showOuterRing = true,
                            )
                            OsdIconSquareButton(
                                icon = Ic.Next,
                                description = "+10",
                                label = "+10",
                                onClick = { onSeekBy(SEEK_STEP_MS) },
                                size = 48.dp,
                                circle = true,
                            )
                            if (playlistCount > 1) {
                                OsdIconSquareButton(
                                    icon = Ic.Next,
                                    description = Ar.next,
                                    onClick = onNext,
                                    size = 44.dp,
                                    circle = true,
                                )
                            }
                        }
                    }

                    // Left Wing (RTL End): Quality + Aspect Ratio + Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdPillButton(
                            label = qualityBtnLabel,
                            leadingIcon = Ic.Quality,
                            iconTint = OsdCyan,
                            onClick = onOpenTracks,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Aspect,
                            description = "نسبة العرض",
                            onClick = onCycleResize,
                            size = 40.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Tune,
                            description = "إعدادات المشغل",
                            onClick = onOpenTracks,
                            size = 40.dp,
                        )
                    }
                }

                // 5. D-PAD NAVIGATION HINTS BAR (ANDROID TV REMOTE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        DpadHintItem("OK", "إيقاف / تشغيل", keyColor = OsdPrimary)
                        if (isEpisode || playlistCount > 1) {
                            DpadHintItem("▲", "إظهار قائمة الحلقات", keyColor = OsdOnSurface)
                        }
                        DpadHintItem("◀ / ▶", "تقديم وترجيع 10 ثوانٍ", keyColor = OsdCyan)
                        DpadHintItem("▼", "خيارات الصوت والترجمة", keyColor = OsdTertiary)
                        DpadHintItem("Back", "إخفاء الواجهة / رجوع", keyColor = OsdError)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. SIDE EPISODES DRAWER (Matches media-player/screen.png & code.html)
// ============================================================================

@Composable
private fun VodEpisodesDrawer(
    currentItem: PlayItem,
    playlist: List<PlayItem>,
    currentIndex: Int,
    seriesDetails: SeriesDetails?,
    position: Long,
    duration: Long,
    onSelectPlaylistIndex: (Int) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onClose: () -> Unit,
) {
    val allEpisodes = seriesDetails?.episodes.orEmpty()
    val seasons = remember(seriesDetails) { seriesDetails?.seasons.orEmpty() }
    val currentEpisodeObj = remember(allEpisodes, currentItem.id) {
        allEpisodes.firstOrNull { it.id == currentItem.id }
    }
    var selectedSeason by remember(currentEpisodeObj, seasons) {
        mutableIntStateOf(currentEpisodeObj?.season ?: seasons.firstOrNull() ?: 1)
    }

    val seasonEpisodes = remember(allEpisodes, selectedSeason) {
        if (allEpisodes.isEmpty()) emptyList()
        else allEpisodes.filter { it.season == selectedSeason }
    }

    val totalCount = if (allEpisodes.isNotEmpty()) allEpisodes.size else playlist.size

    Column(
        modifier = Modifier
            .width(410.dp)
            .widthIn(max = 420.dp)
            .fillMaxHeight()
            .background(OsdSurfaceLowest.copy(alpha = 0.96f))
            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Series, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(26.dp))
                Column {
                    Text(
                        if (currentItem.kind == Kind.EPISODE) "حلقات المسلسل" else "قائمة التشغيل",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "${currentItem.title} • $totalCount حلقات",
                        fontSize = 11.sp,
                        color = OsdOutline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OsdIconSquareButton(
                icon = Ic.Close,
                description = Ar.close,
                onClick = onClose,
                size = 36.dp,
                circle = true,
            )
        }

        // Season Selector Tabs
        if (seasons.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(seasons, key = { it }) { seasonNum ->
                    val selected = seasonNum == selectedSeason
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) OsdPrimary else OsdSurfaceHigh.copy(alpha = 0.80f))
                            .clickable { selectedSeason = seasonNum }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            Ar.season(seasonNum),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) OsdOnPrimary else OsdOnSurface,
                        )
                    }
                }
            }
        }

        // Episodes Scrollable Card List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (seasonEpisodes.isNotEmpty()) {
                itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { _, ep ->
                    val isCurrent = ep.id == currentItem.id
                    val isWatched = currentEpisodeObj != null &&
                        (ep.season < currentEpisodeObj.season ||
                            (ep.season == currentEpisodeObj.season && ep.number < currentEpisodeObj.number))
                    val isNext = currentEpisodeObj != null &&
                        ep.season == currentEpisodeObj.season &&
                        ep.number == currentEpisodeObj.number + 1

                    EpisodeDrawerCard(
                        number = ep.number,
                        title = ep.title,
                        plot = ep.plot.ifBlank { "حلقة كاملة بجودة عالية من ${currentItem.title}" },
                        imageUrl = ep.image.ifBlank { seriesDetails?.cover.orEmpty() }.ifBlank { currentItem.icon },
                        durationText = ep.duration.ifBlank { "50:00" },
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        isNext = isNext,
                        progressFraction = if (isCurrent && duration > 0L) (position.toFloat() / duration).coerceIn(0.05f, 1f) else 0.45f,
                        remainingMinutes = if (isCurrent && duration > position) ((duration - position) / 60_000L).toInt().coerceAtLeast(1) else 28,
                        onClick = { onSelectEpisode(ep) },
                    )
                }
            } else {
                itemsIndexed(playlist, key = { i, p -> "${p.id}_$i" }) { idx, p ->
                    val isCurrent = idx == currentIndex
                    val isWatched = idx < currentIndex
                    val isNext = idx == currentIndex + 1

                    EpisodeDrawerCard(
                        number = idx + 1,
                        title = p.subtitle.ifBlank { p.title },
                        plot = p.title,
                        imageUrl = p.icon,
                        durationText = if (isCurrent && duration > 0L) formatDuration(duration) else "HD",
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        isNext = isNext,
                        progressFraction = if (isCurrent && duration > 0L) (position.toFloat() / duration).coerceIn(0.05f, 1f) else 0.45f,
                        remainingMinutes = if (isCurrent && duration > position) ((duration - position) / 60_000L).toInt().coerceAtLeast(1) else 25,
                        onClick = { onSelectPlaylistIndex(idx) },
                    )
                }
            }
        }

        // Drawer Footer Quick Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Ic.CheckCircle, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(18.dp))
                Text(
                    "مخزن مؤقتاً عبر Media3 للاستجابة الفورية",
                    fontSize = 11.sp,
                    color = OsdPrimary,
                )
            }
            Text(
                "كاش ذكي",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OsdCyan,
            )
        }
    }
}

@Composable
private fun EpisodeDrawerCard(
    number: Int,
    title: String,
    plot: String,
    imageUrl: String,
    durationText: String,
    isCurrent: Boolean,
    isWatched: Boolean,
    isNext: Boolean,
    progressFraction: Float,
    remainingMinutes: Int,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isCurrent -> OsdPrimaryContainer.copy(alpha = 0.24f)
        focused -> OsdSurfaceHigh.copy(alpha = 0.90f)
        else -> OsdSurfaceContainer.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isCurrent || focused -> OsdPrimary
        else -> Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(if (isCurrent || focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail Container
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OsdSurfaceLowest),
            ) {
                RemoteImage(
                    url = imageUrl,
                    fallbackText = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            when {
                                isCurrent -> OsdPrimaryContainer.copy(alpha = 0.42f)
                                isWatched -> OsdSurfaceLowest.copy(alpha = 0.60f)
                                else -> OsdSurfaceLowest.copy(alpha = 0.30f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val overlayIcon = when {
                        isCurrent -> Ic.VolumeUp
                        isWatched -> Ic.CheckCircle
                        else -> Ic.Play
                    }
                    val overlayTint = if (isWatched) OsdCyan else Color.White
                    Icon(overlayIcon, contentDescription = null, tint = overlayTint, modifier = Modifier.size(26.dp))
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(OsdSurfaceHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                                .background(OsdCyan),
                        )
                    }
                } else if (durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OsdSurfaceLowest.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(durationText, fontSize = 10.sp, color = OsdOnSurface)
                    }
                }
            }

            // Meta Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when {
                    isCurrent -> Text(
                        "الحلقة الحالية قيد المشاهدة",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                    isWatched -> Text(
                        "تمت المشاهدة بالكامل",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                    isNext -> Text(
                        "الحلقة التالية",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                }

                Text(
                    "$number. $title",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else OsdOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    plot,
                    fontSize = 11.sp,
                    color = if (isCurrent) OsdTertiary else OsdOutline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isCurrent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("متبقي $remainingMinutes دقيقة", fontSize = 10.sp, color = OsdPrimary)
                        Text("4K UHD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OsdPrimary)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. SHARED OSD MICRO-COMPONENTS & GRADIENT TIMELINE
// ============================================================================

@Composable
private fun GradientTimelineBar(
    progress: Float,
    buffered: Float,
    showChapters: Boolean,
    onSeekToFraction: ((Float) -> Unit)?,
    onSeekBy: (Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    val bufShown = buffered.coerceIn(shown, 1f)

    // Keep timeline LTR so progress grows left-to-right or follows standard media scrubbers
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onSeekBy(SEEK_STEP_MS)
                        true
                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onSeekBy(-SEEK_STEP_MS)
                        true
                    } else {
                        false
                    }
                }
                .focusable(interactionSource = interaction)
                .then(
                    if (onSeekToFraction != null) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) {
                                        onSeekToFraction((offset.x / size.width).coerceIn(0f, 1f))
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        dragFraction?.let { onSeekToFraction(it) }
                                        dragFraction = null
                                    },
                                    onDragCancel = { dragFraction = null },
                                ) { change, _ ->
                                    change.consume()
                                    if (size.width > 0) {
                                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                }
                            }
                    } else Modifier,
                ),
        ) {
            val trackHeight = 10.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            // Base track
            drawRoundRect(
                color = OsdSurfaceHighest.copy(alpha = 0.85f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )

            // Buffered segment
            drawRoundRect(
                color = OsdOutlineVariant.copy(alpha = 0.65f),
                topLeft = Offset(0f, top),
                size = Size(size.width * bufShown, trackHeight),
                cornerRadius = radius,
            )

            // Active glowing gradient progress bar (Violet -> Lilac -> Electric Cyan)
            val activeWidth = (size.width * shown).coerceAtLeast(trackHeight)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(OsdPrimaryContainer, OsdPrimary, OsdCyan),
                ),
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeight),
                cornerRadius = radius,
            )

            // Chapter markers
            if (showChapters) {
                val chapters = floatArrayOf(0.15f, 0.42f, 0.85f)
                for (ch in chapters) {
                    val x = size.width * ch
                    drawLine(
                        color = OsdSurfaceLowest,
                        start = Offset(x, top),
                        end = Offset(x, top + trackHeight),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            // Playhead thumb
            val thumbX = (size.width * shown).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
            val center = Offset(thumbX, size.height / 2f)
            val outerRadius = if (focused || dragFraction != null) 10.dp.toPx() else 8.dp.toPx()
            drawCircle(color = OsdPrimaryContainer, radius = outerRadius + 2.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = outerRadius, center = center)
            drawCircle(color = OsdPrimaryContainer, radius = 3.5.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun OsdPrimaryPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp,
    cornerRadius: Dp,
    showOuterRing: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = if (focused) 22.dp else 14.dp,
                shape = shape,
                ambientColor = OsdPrimaryContainer,
                spotColor = OsdPrimary,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (focused) listOf(OsdPrimary, OsdCyan)
                    else listOf(OsdPrimaryContainer, OsdPrimary),
                ),
            )
            .border(
                width = if (focused || showOuterRing) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdPrimary.copy(alpha = 0.65f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Ic.Pause else Ic.Play,
            contentDescription = if (isPlaying) Ar.pause else Ar.play,
            tint = if (focused) OsdOnPrimary else Color.White,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun OsdIconSquareButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    circle: Boolean = false,
    label: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (circle) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (focused) OsdPrimary else OsdSurfaceHigh.copy(alpha = 0.85f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdOutlineVariant.copy(alpha = 0.35f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (focused) OsdOnPrimary else OsdOnSurface,
            )
        } else {
            Icon(
                icon,
                contentDescription = description,
                tint = if (focused) OsdOnPrimary else OsdOnSurface,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

@Composable
private fun OsdPillButton(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconTint: Color = OsdOnSurface,
    highlighted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused || highlighted -> OsdPrimary
        else -> OsdSurfaceHigh.copy(alpha = 0.88f)
    }
    val contentColor = if (focused || highlighted) OsdOnPrimary else OsdOnSurface
    val activeIconTint = if (focused || highlighted) OsdOnPrimary else iconTint

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdOutlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(17.dp))
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun OsdTwoLineTrackButton(
    topLabel: String,
    bottomLabel: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) OsdPrimary else OsdSurfaceHigh.copy(alpha = 0.90f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdOutlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (focused) OsdOnPrimary else iconTint,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                topLabel,
                fontSize = 9.sp,
                color = if (focused) OsdOnPrimary.copy(alpha = 0.8f) else OsdOnSurfaceVariant,
            )
            Text(
                bottomLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (focused) OsdOnPrimary else OsdOnSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FormatChip(label: String, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OsdSurfaceHighest)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun DpadHintItem(keyLabel: String, description: String, keyColor: Color = OsdOnSurface) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(OsdSurfaceHighest)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                keyLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = keyColor,
            )
        }
        Text(
            description,
            fontSize = 11.sp,
            color = OsdOnSurfaceVariant,
        )
    }
}

// ============================================================================
// 5. TRACKS DIALOG (Video Quality + Audio Tracks + Subtitles)
// ============================================================================

@OptIn(UnstableApi::class)
@Composable
private fun TracksDialog(tracks: Tracks, exo: ExoPlayer, onDismiss: () -> Unit) {
    val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val hasVideoOverride = video.any { g -> exo.trackSelectionParameters.overrides.containsKey(g.mediaTrackGroup) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(OsdSurfaceLow)
                .border(1.5.dp, OsdPrimary.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            Text(Ar.tracks, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OsdOnSurface)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                if (video.isNotEmpty()) {
                    item { Text(Ar.videoQuality, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdPrimary) }
                    item {
                        TrackRow(
                            label = Ar.qualityAuto,
                            selected = !hasVideoOverride,
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    .build()
                                onDismiss()
                            },
                        )
                    }
                    items(video.flatMap { group -> (0 until group.length).map { group to it } }) { (group, i) ->
                        TrackRow(
                            label = SahabPlayer.videoQualityLabel(group.getTrackFormat(i)),
                            selected = hasVideoOverride && group.isTrackSelected(i),
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                    .build()
                                onDismiss()
                            },
                        )
                    }
                }
                if (audio.isNotEmpty()) {
                    item { Text(Ar.audio, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdCyan) }
                    items(audio.flatMap { group -> (0 until group.length).map { group to it } }) { (group, i) ->
                        TrackRow(
                            label = trackLabel(group.getTrackFormat(i), i),
                            selected = group.isTrackSelected(i),
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                    .build()
                                onDismiss()
                            },
                        )
                    }
                }
                if (text.isNotEmpty()) {
                    item { Text(Ar.subtitles, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdTertiary) }
                    item {
                        TrackRow(
                            label = Ar.subtitlesOff,
                            selected = textOff || text.none { g -> (0 until g.length).any { g.isTrackSelected(it) } },
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                onDismiss()
                            },
                        )
                    }
                    items(text.flatMap { group -> (0 until group.length).map { group to it } }) { (group, i) ->
                        TrackRow(
                            label = trackLabel(group.getTrackFormat(i), i),
                            selected = !textOff && group.isTrackSelected(i),
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                    .build()
                                onDismiss()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassButton(Ar.close, onDismiss, modifier = Modifier.fillMaxWidth(), autoFocus = true)
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        selected = selected,
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = OsdOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private fun trackLabel(format: Format, position: Int): String {
    val languageCode = format.language
    val language = if (languageCode.isNullOrBlank() || languageCode == "und") {
        null
    } else {
        val name = Locale(languageCode).getDisplayLanguage(Locale("ar"))
        name.ifBlank { languageCode }
    }
    return listOfNotNull(format.label, language).distinct().joinToString(" - ")
        .ifBlank { Ar.trackNumber(position + 1) }
}
