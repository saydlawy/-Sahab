package com.saydlawy.sahab.ui.player

import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.media3.common.Timeline
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
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.findActivity
import com.saydlawy.sahab.util.formatDuration
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import com.saydlawy.sahab.util.ChannelLogoResolver

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
internal val OsdSurfaceLowest get() = Palette.color("OsdSurfaceLowest")
internal val OsdSurfaceLow get() = Palette.color("OsdSurfaceLow")
internal val OsdSurfaceContainer get() = Palette.color("OsdSurfaceContainer")
internal val OsdSurfaceHigh get() = Palette.color("OsdSurfaceHigh")
internal val OsdSurfaceHighest get() = Palette.color("OsdSurfaceHighest")
internal val OsdPrimary get() = Palette.color("OsdPrimary")
internal val OsdPrimaryContainer get() = Palette.color("OsdPrimaryContainer")
internal val OsdOnPrimary get() = Palette.color("OsdOnPrimary")
internal val OsdCyan get() = Palette.color("OsdCyan")
internal val OsdTertiary get() = Palette.color("OsdTertiary")
internal val OsdOnSurface get() = Palette.color("OsdOnSurface")
internal val OsdOnSurfaceVariant get() = Palette.color("OsdOnSurfaceVariant")
internal val OsdOutline get() = Palette.color("OsdOutline")
internal val OsdOutlineVariant get() = Palette.color("OsdOutlineVariant")

@OptIn(UnstableApi::class)
private fun defaultResizeModes(): IntArray = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)

internal val ResizeModes = defaultResizeModes()

internal val PlaybackSpeeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

internal const val SEEK_STEP_MS = 10_000L
internal const val CONTROLS_HIDE_MS = 2_000L
internal val TimelineProgressBrush get() = Brush.horizontalGradient(listOf(OsdPrimaryContainer, OsdPrimary, OsdCyan))

@Composable
fun PlayerScreen(container: AppContainer, onBack: () -> Unit) {
    val themeCustomization = LocalThemeCustomization.current
    val request = remember { container.playback ?: container.restorePendingPlayback() }
    if (request == null || request.items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val exitPlayer: () -> Unit = {
        container.clearPendingPlayback()
        onBack()
    }
    PlayerContent(container, request, exitPlayer)
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerContent(container: AppContainer, request: PlayRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val isTv = LocalIsTv.current
    val displayMode = LocalDisplayMode.current
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it, request) }
    val ui by vm.state.collectAsStateWithLifecycle()

    val playlist = ui.playlist
    val index = ui.index
    val item = ui.currentItem
    val isLive = ui.isLive
    val isPlaying = ui.isPlaying
    val buffering = ui.buffering
    val reconnecting = ui.reconnecting
    val error = ui.error
    val playbackSpeed = ui.playbackSpeed
    val resizeIndex = ui.resizeIndex
    val seriesDetails = ui.seriesDetails
    val requestPlayFocusTick = ui.requestPlayFocusTick

    val exo = remember { SahabPlayer.create(context, container.client, isLive = isLive, isPreview = false, accountHash = container.accountHash) }

    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showQuickChannelsDrawer by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var stoppedByLifecycle by remember { mutableStateOf(false) }
    var mediaLive by remember(item.id) { mutableStateOf(isLive) }
    var mediaSeekable by remember(item.id) { mutableStateOf(!isLive) }
    val canSeek = !isLive && !mediaLive && mediaSeekable
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val drawerFocus = remember { FocusRequester() }

    LaunchedEffect(item.groupId, item.kind) {
        vm.loadSeriesIfNeeded(item)
    }

    val saveNow: () -> Unit = {
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        vm.saveProgress(exo.currentPosition, total)
    }

    val goTo: (Int) -> Unit = { target ->
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (vm.goTo(target, exo.currentPosition, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }

    val playEpisodeFromDrawer: (Episode) -> Unit = { ep ->
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (vm.playEpisodeFromDrawer(ep, exo.currentPosition, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }

    val zapNext: () -> Unit = {
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (vm.zapNext(exo.currentPosition, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }
    val zapPrevious: () -> Unit = {
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (vm.zapPrevious(exo.currentPosition, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }
    val togglePlay: () -> Unit = {
        if (exo.playbackState == Player.STATE_ENDED) {
            // Only restart on explicit manual user play press — never auto-repeat
            exo.seekTo(0L)
            exo.play()
        } else if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
        interactions++
    }
    val seekBy: (Long) -> Unit = { delta ->
        if (canSeek) {
            val total = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
            exo.seekTo((exo.currentPosition + delta).coerceIn(0L, total))
            vm.onSeekPosition(exo.currentPosition)
            interactions++
        }
    }
    val cycleSpeed: () -> Unit = {
        if (canSeek) {
            val nextSpeed = vm.cycleSpeed(PlaybackSpeeds)
            exo.setPlaybackSpeed(nextSpeed)
            interactions++
        }
    }

    val toggleOrientation: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null && !isTv) {
            activity.requestedOrientation = if (displayMode == DisplayMode.PHONE_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // Keep screen awake; hide system bars only in landscape / TV mode so portrait phone has normal status bar spacing.
    DisposableEffect(displayMode) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (displayMode != DisplayMode.PHONE_PORTRAIT) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        onDispose {
            if (!isTv && activity != null) {
                activity.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            saveNow()
            vm.flushRecents()
            exo.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        saveNow()
        vm.flushRecents()
        if (exo.playWhenReady || exo.isPlaying) {
            stoppedByLifecycle = true
            exo.pause()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedByLifecycle) {
            stoppedByLifecycle = false
            if (isLive) {
                exo.seekToDefaultPosition()
                exo.prepare()
            }
            exo.play()
        }
    }

    DisposableEffect(exo) {
        // Lock player software volume at 1.0f (unity gain) & disable looping permanently
        exo.volume = 1.0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                vm.onIsPlayingChanged(playing)
                if (!playing && exo.playbackState != Player.STATE_IDLE) {
                    val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    vm.onPlaybackPausedOrStopped(exo.currentPosition, total)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                vm.onBufferingReady(state == Player.STATE_BUFFERING, state == Player.STATE_READY)
                if (!exo.currentTimeline.isEmpty || state == Player.STATE_READY) {
                    val liveNow = isLive || exo.isCurrentMediaItemLive
                    mediaLive = liveNow
                    mediaSeekable = !liveNow && exo.isCurrentMediaItemSeekable
                }
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!timeline.isEmpty) {
                    val liveNow = isLive || exo.isCurrentMediaItemLive
                    mediaLive = liveNow
                    mediaSeekable = !liveNow && exo.isCurrentMediaItemSeekable
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                vm.onPlayerError(e.errorCodeName, extractHttpResponseCode(e))
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    LaunchedEffect(item.id, ui.retryKey, ui.formatAttempt, ui.autoReconnectCount) {
        val activeItem = item
        vm.onPrepareStarted()
        if (ui.autoReconnectCount > 0) {
            delay(1_100)
        }
        val url = try {
            vm.streamUrl(activeItem, ui.formatAttempt)
        } catch (e: Exception) {
            vm.onUrlError()
            return@LaunchedEffect
        }
        val resume = vm.resolveResumePositionMs(activeItem, exo.currentPosition)
        exo.volume = 1.0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.setMediaItem(MediaItem.fromUri(url), resume)
        exo.prepare()
        exo.playWhenReady = true
    }

    // LIVE-01: recover from silent hardware-decoder stalls (frozen picture while audio continues).
    LiveVideoStallWatchdog(
        player = exo,
        enabled = isLive,
        itemKey = item.id,
        onEscalate = { vm.switchLiveFormatAfterStall() },
    )

    LaunchedEffect(ended) {
        if (ended) {
            ended = false
            val total = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            vm.onPlaybackEnded(total) {
                // Last episode or movie ended: NEVER auto-repeat/loop same item
                exo.playWhenReady = false
                exo.pause()
                controlsVisible = true
                vm.bumpPlayFocusTick()
            }
        }
    }

    LaunchedEffect(exo) {
        var ticks = 0
        while (true) {
            if (controlsVisible || showEpisodesDrawer || displayMode == DisplayMode.PHONE_PORTRAIT) {
                val cur = exo.currentPosition.coerceAtLeast(0L)
                val buf = exo.bufferedPosition.coerceAtLeast(cur)
                val dur = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                vm.onProgressUpdate(cur, buf, dur)
            }
            delay(500)
            ticks++
            if (ticks >= 20) {
                ticks = 0
                if (exo.isPlaying) {
                    val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    vm.saveProgressIfVod(exo.currentPosition, total)
                }
            }
        }
    }

    LaunchedEffect(
        controlsVisible,
        interactions,
        isPlaying,
        showTracks,
        showGuide,
        showEpisodesDrawer,
        showQuickChannelsDrawer,
    ) {
        if (controlsVisible && isPlaying && !showTracks && !showGuide && !showEpisodesDrawer && !showQuickChannelsDrawer) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, requestPlayFocusTick, showEpisodesDrawer, showQuickChannelsDrawer) {
        if (showEpisodesDrawer || (showQuickChannelsDrawer && isLive)) {
            delay(60)
            withFrameNanos { }
            runCatching { drawerFocus.requestFocus() }
        } else if (controlsVisible && displayMode != DisplayMode.PHONE_PORTRAIT) {
            delay(50)
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        } else if (!controlsVisible && !showEpisodesDrawer && !showQuickChannelsDrawer) {
            delay(60)
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        withFrameNanos { }
        runCatching {
            if (controlsVisible && displayMode != DisplayMode.PHONE_PORTRAIT) playFocus.requestFocus()
            else rootFocus.requestFocus()
        }
    }

    BackHandler(
        enabled = showEpisodesDrawer ||
            (showQuickChannelsDrawer && isLive) ||
            (controlsVisible && displayMode != DisplayMode.PHONE_PORTRAIT),
    ) {
        when {
            showEpisodesDrawer -> showEpisodesDrawer = false
            showQuickChannelsDrawer && isLive -> showQuickChannelsDrawer = false
            else -> controlsVisible = false
        }
    }

    val onKey: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        // Preserve hardware/source volume: never intercept or alter volume internally
        if (event.key == Key.VolumeUp || event.key == Key.VolumeDown || event.key == Key.VolumeMute) {
            return@handler false
        }
        interactions++
        when (event.key) {
            Key.MediaPlayPause -> {
                togglePlay()
                controlsVisible = true
                vm.bumpPlayFocusTick()
                true
            }
            Key.MediaPlay -> {
                exo.play()
                true
            }
            Key.MediaPause -> {
                exo.pause()
                controlsVisible = true
                vm.bumpPlayFocusTick()
                true
            }
            Key.MediaFastForward -> {
                if (canSeek) seekBy(3 * SEEK_STEP_MS)
                true
            }
            Key.MediaRewind -> {
                if (canSeek) seekBy(-3 * SEEK_STEP_MS)
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
                } else if (showQuickChannelsDrawer && isLive) {
                    showQuickChannelsDrawer = false
                    true
                } else if (controlsVisible && displayMode != DisplayMode.PHONE_PORTRAIT) {
                    controlsVisible = false
                    true
                } else {
                    false
                }
            }
            else -> {
                if (controlsVisible || showEpisodesDrawer || (showQuickChannelsDrawer && isLive)) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            // First OK pauses playback, shows OSD, and highlights Play/Pause button with cyan glow
                            if (exo.isPlaying) {
                                exo.pause()
                            } else if (exo.playbackState == Player.STATE_ENDED) {
                                exo.seekTo(0L)
                                exo.play()
                            } else {
                                exo.play()
                            }
                            controlsVisible = true
                            vm.bumpPlayFocusTick()
                        }
                        Key.DirectionUp -> {
                            if (isLive) zapNext() else if (playlist.size > 1 || seriesDetails != null) {
                                showEpisodesDrawer = true
                            }
                            controlsVisible = true
                        }
                        Key.DirectionDown -> {
                            if (isLive) zapPrevious() else {
                                showTracks = true
                            }
                            controlsVisible = true
                        }
                        Key.DirectionRight -> {
                            if (canSeek) seekBy(SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        Key.DirectionLeft -> {
                            if (canSeek) seekBy(-SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        else -> controlsVisible = true
                    }
                    true
                }
            }
        }
    }

    val container = LocalContainer.current
    val dao = remember(container) { container.channelDb.channelDao() }
    val livePrograms by produceState<Pair<EpgProgramEntity?, EpgProgramEntity?>>(
        initialValue = null to null,
        item.id,
        isLive,
    ) {
        if (!isLive) {
            value = null to null
            return@produceState
        }
        while (true) {
            val t = System.currentTimeMillis() / 1000
            val cur = runCatching { dao.currentProgramFor(item.id, t) }.getOrNull()
            val nxt = runCatching { dao.nextProgramFor(item.id, t) }.getOrNull()
            value = cur to nxt
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val currentProgram = livePrograms.first?.let { Program(it.title, it.description, it.start, it.end) }
    val nextProgram = livePrograms.second?.let { Program(it.title, it.description, it.start, it.end) }

    val textOff = remember(tracks, exo.trackSelectionParameters) {
        exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }
    val hasVideoOverride = remember(tracks, exo.trackSelectionParameters) {
        tracks.groups.any { g ->
            g.type == C.TRACK_TYPE_VIDEO && exo.trackSelectionParameters.overrides.containsKey(g.mediaTrackGroup)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (displayMode) {
            // =========================================================================
            // MODE A: PHONE PORTRAIT — Vertical stacked layout (16:9 video top + list below)
            // =========================================================================
            DisplayMode.PHONE_PORTRAIT -> {
                PhonePortraitPlayerScreen(
                    exo = exo,
                    item = item,
                    playlist = playlist,
                    currentIndex = index,
                    isLive = isLive,
                    canSeek = canSeek,
                    isPlaying = isPlaying,
                    buffering = buffering,
                    reconnecting = reconnecting,
                    error = error,
                    progressFlow = vm.progress,
                    playbackSpeed = playbackSpeed,
                    tracks = tracks,
                    textOff = textOff,
                    hasVideoOverride = hasVideoOverride,
                    currentProgram = currentProgram,
                    nextProgram = nextProgram,
                    seriesDetails = seriesDetails,
                    controlsVisible = controlsVisible,
                    resizeMode = ResizeModes[resizeIndex % ResizeModes.size],
                    onToggleControls = {
                        controlsVisible = !controlsVisible
                        interactions++
                    },
                    onBack = onBack,
                    onTogglePlay = togglePlay,
                    onSeekBy = seekBy,
                    onSeekTo = { target ->
                        exo.seekTo(target.coerceAtLeast(0L))
                        vm.onSeekPosition(target)
                        interactions++
                    },
                    onPrev = zapPrevious,
                    onNext = zapNext,
                    onSelectPlaylistIndex = goTo,
                    onSelectEpisode = playEpisodeFromDrawer,
                    onCycleSpeed = cycleSpeed,
                    onCycleResize = {
                        vm.cycleResize(ResizeModes.size)
                        interactions++
                    },
                    onOpenTracks = { showTracks = true },
                    onOpenGuide = { showGuide = true },
                    onEnterLandscape = toggleOrientation,
                    onRetry = { vm.retry() },
                )
            }

            // =========================================================================
            // MODE B & C: PHONE LANDSCAPE vs TABLET / TV FULLSCREEN
            // =========================================================================
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OsdSurfaceLowest)
                        .focusRequester(rootFocus)
                        .focusable()
                        .onPreviewKeyEvent(onKey),
                ) {
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

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    if (showEpisodesDrawer) {
                                        showEpisodesDrawer = false
                                    } else if (showQuickChannelsDrawer) {
                                        showQuickChannelsDrawer = false
                                    } else {
                                        controlsVisible = !controlsVisible
                                    }
                                    interactions++
                                })
                            },
                    )

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

                    AnimatedVisibility(
                        visible = controlsVisible && error == null && LocalThemeCustomization.current.isVisible("player_controls"),
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        UnifiedFullscreenPlayerOsd(
                            item = item,
                            isLive = isLive,
                            canSeek = canSeek,
                            playlistCount = if (isLive) playlist.size else (seriesDetails?.episodes?.size?.takeIf { it > 0 } ?: playlist.size),
                            currentProgram = currentProgram,
                            isPlaying = isPlaying,
                            progressFlow = vm.progress,
                            playbackSpeed = playbackSpeed,
                            showDrawer = if (isLive) showQuickChannelsDrawer else showEpisodesDrawer,
                            playFocus = playFocus,
                            onBack = onBack,
                            onTogglePlay = togglePlay,
                            onSeekBy = seekBy,
                            onSeekTo = { target ->
                                exo.seekTo(target.coerceAtLeast(0L))
                                vm.onSeekPosition(target)
                                interactions++
                            },
                            onPrev = zapPrevious,
                            onNext = zapNext,
                            onCycleSpeed = cycleSpeed,
                            onCycleResize = {
                                vm.cycleResize(ResizeModes.size)
                                interactions++
                            },
                            onOpenTracks = {
                                showTracks = true
                                interactions++
                            },
                            onToggleDrawer = {
                                if (isLive) showQuickChannelsDrawer = !showQuickChannelsDrawer
                                else showEpisodesDrawer = !showEpisodesDrawer
                                interactions++
                            },
                        )
                    }

                    // Side Channels Drawer for Live TV (Phone Landscape & TV, aligned to right side in RTL)
                    if (isLive) {
                        AnimatedVisibility(
                            visible = showQuickChannelsDrawer && error == null,
                            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            LiveChannelsDrawer(
                                playlist = playlist,
                                currentIndex = index,
                                currentProgram = currentProgram,
                                compact = displayMode == DisplayMode.PHONE_LANDSCAPE,
                                drawerFocus = drawerFocus,
                                onSelectChannel = { idx ->
                                    goTo(idx)
                                    showQuickChannelsDrawer = false
                                },
                                onClose = { showQuickChannelsDrawer = false },
                            )
                        }
                    }

                    // Side Episodes Drawer for VOD (aligned to right side in RTL)
                    if (!isLive) {
                        AnimatedVisibility(
                            visible = showEpisodesDrawer && error == null,
                            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            VodEpisodesDrawer(
                                currentItem = item,
                                playlist = playlist,
                                currentIndex = index,
                                seriesDetails = seriesDetails,
                                progressFlow = vm.progress,
                                compact = displayMode == DisplayMode.PHONE_LANDSCAPE,
                                drawerFocus = drawerFocus,
                                onSelectPlaylistIndex = { idx ->
                                    goTo(idx)
                                    showEpisodesDrawer = false
                                },
                                onSelectEpisode = { ep ->
                                    playEpisodeFromDrawer(ep)
                                    showEpisodesDrawer = false
                                },
                                onClose = { showEpisodesDrawer = false },
                            )
                        }
                    }

                    error?.let { message ->
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(24.dp))
                                .background(OsdSurfaceLow.copy(alpha = 0.94f))
                                .border(1.dp, OsdOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                message,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdOnSurface,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton(
                                    Ar.retry,
                                    { vm.retry() },
                                    primary = true,
                                    autoFocus = true,
                                )
                                GlassButton(Ar.back, onBack)
                            }
                        }
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

