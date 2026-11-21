package com.saydlawy.sahab.ui.player

import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.content.pm.ActivityInfo
import android.os.SystemClock
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
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

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerContent(container: AppContainer, request: PlayRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = LocalIsTv.current
    val displayMode = LocalDisplayMode.current
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it, request) }
    val ui by vm.state.collectAsStateWithLifecycle()

    val playlist = ui.playlist
    val index = ui.index
    val item = ui.currentItem
    val isLive = ui.isLive
    val currentIsLive by rememberUpdatedState(isLive)
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
    var trackSelectionParams by remember { mutableStateOf(exo.trackSelectionParameters) }
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

    var isPlayerReleased by remember(exo) { mutableStateOf(false) }
    val saveNow: () -> Unit = {
        if (!isPlayerReleased) {
            val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            vm.saveProgress(exo.currentPosition, total)
        }
    }
    val releasePlayerNow: () -> Unit = {
        if (!isPlayerReleased) {
            isPlayerReleased = true
            runCatching {
                val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                vm.saveProgress(exo.currentPosition, total)
            }
            runCatching { vm.flushRecents() }
            runCatching { SahabMediaSessionBridge.detach(context, exo) }
            SahabPlayer.releasePlayer(exo)
        }
    }
    val exitPlayerNow: () -> Unit = {
        releasePlayerNow()
        onBack()
    }

    val goTo: (Int) -> Unit = { target ->
        val pos = if (isPlayerReleased) 0L else exo.currentPosition
        val total = if (isPlayerReleased) 0L else (exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        if (vm.goTo(target, pos, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }

    val playEpisodeFromDrawer: (Episode) -> Unit = { ep ->
        val pos = if (isPlayerReleased) 0L else exo.currentPosition
        val total = if (isPlayerReleased) 0L else (exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        if (vm.playEpisodeFromDrawer(ep, pos, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }

    val zapNext: () -> Unit = {
        val pos = if (isPlayerReleased) 0L else exo.currentPosition
        val total = if (isPlayerReleased) 0L else (exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        if (vm.zapNext(pos, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }
    val zapPrevious: () -> Unit = {
        val pos = if (isPlayerReleased) 0L else exo.currentPosition
        val total = if (isPlayerReleased) 0L else (exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        if (vm.zapPrevious(pos, total)) {
            ended = false
            controlsVisible = true
            interactions++
        }
    }
    val togglePlay: () -> Unit = {
        if (!isPlayerReleased) {
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
    }
    val seekBy: (Long) -> Unit = { delta ->
        if (!isPlayerReleased && canSeek) {
            val total = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
            exo.seekTo((exo.currentPosition + delta).coerceIn(0L, total))
            vm.onSeekPosition(exo.currentPosition)
            interactions++
        }
    }
    val cycleSpeed: () -> Unit = {
        if (!isPlayerReleased && canSeek) {
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

    // Keep screen awake; hide system bars only in landscape / mobile mode so Android TV never flashes system chrome.
    DisposableEffect(displayMode, isTv) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isTv || displayMode != DisplayMode.PHONE_PORTRAIT) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = true
        onDispose {
            if (!isTv) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
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
            releasePlayerNow()
        }
    }

    val isInPipMode by SahabMediaSessionBridge.inPipMode.collectAsStateWithLifecycle()

    LaunchedEffect(isTv, isPlaying) {
        SahabMediaSessionBridge.pipAllowed = !isTv && (isPlaying || exo.playWhenReady)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        saveNow()
        vm.flushRecents()
        val inPip = context.findActivity()?.isInPictureInPictureMode == true
        if (!inPip && !isPlayerReleased && (exo.playWhenReady || exo.isPlaying)) {
            stoppedByLifecycle = true
            exo.pause()
            if (currentIsLive) {
                exo.stop()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedByLifecycle && !isPlayerReleased) {
            stoppedByLifecycle = false
            if (currentIsLive) {
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

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                trackSelectionParams = parameters
            }
        }
        exo.addListener(listener)
        SahabPlayer.bindOnRelease(exo) { isPlayerReleased = true }
        SahabMediaSessionBridge.attach(context, exo, allowBackgroundService = false)
        onDispose {
            SahabMediaSessionBridge.detach(context, exo)
            exo.removeListener(listener)
        }
    }

    var hasPreparedFirst by remember(exo) { mutableStateOf(false) }

    LaunchedEffect(item.id, ui.retryKey, ui.formatAttempt, ui.autoReconnectCount) {
        if (isPlayerReleased) return@LaunchedEffect
        val activeItem = item
        val capturedPositionMs = exo.currentPosition
        val resume = vm.resolveResumePositionMs(activeItem, capturedPositionMs)
        vm.onPrepareStarted()
        if (hasPreparedFirst && !isPlayerReleased) {
            exo.stop()
            exo.clearMediaItems()
        }
        if (ui.autoReconnectCount > 0) {
            delay(1_100)
        } else if (currentIsLive && hasPreparedFirst && ui.retryKey == 0) {
            delay(280L)
        }
        if (isPlayerReleased) return@LaunchedEffect
        val url = try {
            vm.streamUrl(activeItem, ui.formatAttempt)
        } catch (e: Exception) {
            vm.onUrlError()
            return@LaunchedEffect
        }
        if (isPlayerReleased) return@LaunchedEffect
        exo.volume = 1.0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
        val metadata = MediaMetadata.Builder()
            .setTitle(activeItem.title)
            .setSubtitle(activeItem.subtitle.takeIf { it.isNotBlank() })
            .setArtworkUri(activeItem.icon.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
        hasPreparedFirst = true
        exo.setMediaItem(mediaItem, resume)
        exo.prepare()
        exo.playWhenReady = true
    }

    // LIVE-01: recover from silent hardware-decoder stalls (frozen picture while audio continues).
    LiveVideoStallWatchdog(
        player = exo,
        enabled = currentIsLive,
        itemKey = item.id,
        isReleased = { isPlayerReleased },
        onEscalate = { vm.switchLiveFormatAfterStall() },
    )

    LaunchedEffect(ended) {
        if (ended) {
            ended = false
            val total = if (isPlayerReleased) 0L else (exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L)
            vm.onPlaybackEnded(total) {
                if (!isPlayerReleased) {
                    // Last episode or movie ended: NEVER auto-repeat/loop same item
                    exo.playWhenReady = false
                    exo.pause()
                }
                controlsVisible = true
                vm.bumpPlayFocusTick()
            }
        }
    }

    LaunchedEffect(exo, isPlayerReleased, controlsVisible, showEpisodesDrawer, displayMode, lifecycleOwner) {
        if (isPlayerReleased) return@LaunchedEffect
        val shouldTickUi = controlsVisible || showEpisodesDrawer || displayMode == DisplayMode.PHONE_PORTRAIT
        if (!shouldTickUi) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!isPlayerReleased) {
                val cur = exo.currentPosition.coerceAtLeast(0L)
                val buf = exo.bufferedPosition.coerceAtLeast(cur)
                val dur = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                vm.onProgressUpdate(cur, buf, dur)
                delay(500)
            }
        }
    }

    LaunchedEffect(exo, isPlayerReleased, isLive, isPlaying, lifecycleOwner) {
        if (isPlayerReleased || isLive || !isPlaying) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!isPlayerReleased && isPlaying) {
                delay(30_000L)
                if (isPlayerReleased || !exo.isPlaying) break
                val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                vm.saveProgressIfVod(exo.currentPosition, total)
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

    LaunchedEffect(controlsVisible, showEpisodesDrawer, showQuickChannelsDrawer) {
        if (!controlsVisible && !showEpisodesDrawer && !showQuickChannelsDrawer) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    val handleBack: () -> Unit = {
        when {
            showTracks -> {
                showTracks = false
                interactions++
                vm.bumpPlayFocusTick()
            }
            showGuide -> {
                showGuide = false
                interactions++
                vm.bumpPlayFocusTick()
            }
            showEpisodesDrawer -> showEpisodesDrawer = false
            showQuickChannelsDrawer && currentIsLive -> showQuickChannelsDrawer = false
            controlsVisible && displayMode != DisplayMode.PHONE_PORTRAIT -> controlsVisible = false
            else -> exitPlayerNow()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    var lastRepeatSeekUptimeMs by remember { mutableLongStateOf(0L) }
    val onKey: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.key == Key.Escape) {
            if (event.type == KeyEventType.KeyUp && event.nativeKeyEvent.repeatCount == 0) {
                handleBack()
            }
            return@handler true
        }
        // Leave Key.Back strictly to BackHandler so a single Back press never closes both OSD and Player
        if (event.key == Key.Back) return@handler false
        if (event.type != KeyEventType.KeyDown) return@handler false
        // Preserve hardware/source volume: never intercept or alter volume internally
        if (event.key == Key.VolumeUp || event.key == Key.VolumeDown || event.key == Key.VolumeMute) {
            return@handler false
        }
        val isRepeat = event.nativeKeyEvent.repeatCount > 0
        fun allowRepeatSeek(): Boolean {
            if (!isRepeat) return true
            val now = SystemClock.uptimeMillis()
            if (now - lastRepeatSeekUptimeMs < 180L) return false
            lastRepeatSeekUptimeMs = now
            return true
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
                if (!isPlayerReleased) exo.play()
                true
            }
            Key.MediaPause -> {
                if (!isPlayerReleased) exo.pause()
                controlsVisible = true
                vm.bumpPlayFocusTick()
                true
            }
            Key.MediaFastForward -> {
                if (canSeek && allowRepeatSeek()) seekBy(3 * SEEK_STEP_MS)
                true
            }
            Key.MediaRewind -> {
                if (canSeek && allowRepeatSeek()) seekBy(-3 * SEEK_STEP_MS)
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
            else -> {
                if (error != null || controlsVisible || showEpisodesDrawer || (showQuickChannelsDrawer && currentIsLive)) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            togglePlay()
                            controlsVisible = true
                            vm.bumpPlayFocusTick()
                        }
                        Key.DirectionUp -> {
                            if (currentIsLive) zapNext() else if (playlist.size > 1 || seriesDetails != null) {
                                showEpisodesDrawer = true
                            }
                            controlsVisible = true
                        }
                        Key.DirectionDown -> {
                            if (currentIsLive) zapPrevious() else {
                                showTracks = true
                            }
                            controlsVisible = true
                        }
                        Key.DirectionRight -> {
                            if (canSeek && allowRepeatSeek()) seekBy(SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        Key.DirectionLeft -> {
                            if (canSeek && allowRepeatSeek()) seekBy(-SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        else -> controlsVisible = true
                    }
                    true
                }
            }
        }
    }

    val dao = remember(container) { container.channelDb.channelDao() }
    val livePrograms by produceState<Pair<EpgProgramEntity?, EpgProgramEntity?>>(
        initialValue = null to null,
        item.id,
        isLive,
        lifecycleOwner,
    ) {
        if (!isLive) {
            value = null to null
            return@produceState
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis() / 1000
                val cur = runCatching { dao.currentProgramFor(item.id, t) }.getOrNull()
                val nxt = runCatching { dao.nextProgramFor(item.id, t) }.getOrNull()
                value = cur to nxt
                kotlinx.coroutines.delay(60_000L)
            }
        }
    }
    val currentProgram = livePrograms.first?.let { Program(it.title, it.description, it.start, it.end) }
    val nextProgram = livePrograms.second?.let { Program(it.title, it.description, it.start, it.end) }

    val textOff = remember(tracks, trackSelectionParams) {
        trackSelectionParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }
    val hasVideoOverride = remember(tracks, trackSelectionParams) {
        tracks.groups.any { g ->
            g.type == C.TRACK_TYPE_VIDEO && trackSelectionParams.overrides.containsKey(g.mediaTrackGroup)
        }
    }
    val activeBitrateText = remember(tracks) {
        val selectedFormats = tracks.groups.asSequence()
            .filter { it.isSelected && (it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO) }
            .flatMap { group ->
                (0 until group.length).asSequence()
                    .filter { group.isTrackSelected(it) }
                    .map { group.getTrackFormat(it) }
            }
            .toList()
        val videoBps = selectedFormats.firstOrNull { it.width > 0 && it.bitrate > 0 }?.bitrate
            ?: selectedFormats.sumOf { it.bitrate.coerceAtLeast(0) }
        when {
            videoBps >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", videoBps / 1_000_000.0)
            videoBps > 0 -> "${videoBps / 1_000} kbps"
            else -> null
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
                    isPlayerReleased = isPlayerReleased,
                    onToggleControls = {
                        controlsVisible = !controlsVisible
                        interactions++
                    },
                    onBack = exitPlayerNow,
                    onTogglePlay = togglePlay,
                    onSeekBy = seekBy,
                    onSeekTo = { target ->
                        if (!isPlayerReleased) {
                            exo.seekTo(target.coerceAtLeast(0L))
                            vm.onSeekPosition(target)
                            interactions++
                        }
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
                        update = {
                            it.resizeMode = ResizeModes[resizeIndex % ResizeModes.size]
                            if (isPlayerReleased && it.player != null) {
                                it.player = null
                            }
                        },
                        onRelease = { it.player = null },
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
                        visible = controlsVisible && !isInPipMode && error == null && LocalThemeCustomization.current.isVisible("player_controls"),
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.displayCutout),
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
                            activeBitrateText = activeBitrateText,
                            focusTick = requestPlayFocusTick,
                            playFocus = playFocus,
                            onBack = exitPlayerNow,
                            onTogglePlay = togglePlay,
                            onSeekBy = seekBy,
                            onSeekTo = { target ->
                                if (!isPlayerReleased) {
                                    exo.seekTo(target.coerceAtLeast(0L))
                                    vm.onSeekPosition(target)
                                    interactions++
                                }
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
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .windowInsetsPadding(WindowInsets.displayCutout),
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
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .windowInsetsPadding(WindowInsets.displayCutout),
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
                        val errorRetryFocus = remember { FocusRequester() }
                        LaunchedEffect(message) {
                            withFrameNanos { }
                            runCatching { errorRetryFocus.requestFocus() }
                        }
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
                                    modifier = Modifier.focusRequester(errorRetryFocus),
                                    primary = true,
                                    autoFocus = true,
                                )
                                GlassButton(Ar.back, exitPlayerNow)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTracks) {
        TracksDialog(tracks, exo, trackSelectionParams) {
            showTracks = false
            interactions++
            vm.bumpPlayFocusTick()
        }
    }
    if (showGuide && isLive) {
        EpgDialog(channelName = item.title, channelId = item.id) {
            showGuide = false
            interactions++
            vm.bumpPlayFocusTick()
        }
    }
}

