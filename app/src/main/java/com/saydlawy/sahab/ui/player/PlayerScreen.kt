package com.saydlawy.sahab.ui.player

import android.content.pm.ActivityInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
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
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
private const val CONTROLS_HIDE_MS = 2_000L

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
    val displayMode = LocalDisplayMode.current
    val repo = container.repo

    var playlist by remember { mutableStateOf(request.items) }
    val lastIndex = playlist.lastIndex

    var index by remember { mutableIntStateOf(request.startIndex.coerceIn(0, lastIndex.coerceAtLeast(0))) }
    val item = playlist[index.coerceIn(0, lastIndex.coerceAtLeast(0))]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.kind == Kind.LIVE

    val exo = remember { SahabPlayer.create(context, container.client, isLive = isLive, isPreview = false, accountHash = container.accountHash) }

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
    var showQuickChannelsDrawer by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var formatAttempt by remember { mutableIntStateOf(0) }
    var autoReconnectCount by remember { mutableIntStateOf(0) }
    var reconnecting by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var forceFromZeroId by remember { mutableStateOf<String?>(null) }
    var usedInitialPos by remember { mutableStateOf(false) }
    var requestPlayFocusTick by remember { mutableIntStateOf(0) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var seriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.groupId, item.kind) {
        if (item.kind == Kind.EPISODE && item.groupId.isNotBlank()) {
            val loaded = runCatching { repo.seriesDetails(item.groupId) }.getOrNull()
            seriesDetails = loaded
            // Expand single-item resume playlist to full series queue without interrupting active playback
            if (loaded != null && loaded.episodes.isNotEmpty() && playlist.size <= 1) {
                val expanded = loaded.episodes.map { e ->
                    PlayItem(
                        kind = Kind.EPISODE,
                        id = e.id,
                        title = loaded.name.ifBlank { item.title },
                        subtitle = "${Ar.seasonEpisode(e.season, e.number)}: ${e.title}",
                        icon = e.image.ifBlank { loaded.cover.ifBlank { item.icon } },
                        ext = e.ext,
                        groupId = loaded.id.ifBlank { item.groupId },
                    )
                }
                val matchIdx = expanded.indexOfFirst { it.id == currentItem.id }
                if (matchIdx >= 0) {
                    playlist = expanded
                    index = matchIdx
                }
            }
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
            exo.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }

    DisposableEffect(exo) {
        // Lock player software volume at 1.0f (unity gain) & disable looping permanently
        exo.volume = 1.0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
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
                        val causeMsg = e.cause?.message ?: e.message ?: ""
                        val errorCodeName = e.errorCodeName
                        val codeMatch = Regex("""\b(401|403|404|458|500|502|503)\b""").find(causeMsg)?.value
                        val hint = when (codeMatch) {
                            "401", "403", "458" -> "تم تجاوز الحد الأقصى للأجهزة المتصلة أو انتهى الاشتراك ($codeMatch)"
                            "404" -> "القناة أو المحتوى غير متوفر حالياً على السيرفر (404)"
                            "500", "502", "503" -> "خلل في خادم البث ($codeMatch)"
                            else -> if (errorCodeName.isNotBlank()) "فشل تشغيل البث ($errorCodeName)" else Ar.errPlayback
                        }
                        error = hint
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

    LaunchedEffect(currentItem.id, retryKey, formatAttempt, autoReconnectCount) {
        val activeItem = currentItem
        error = null
        buffering = true
        if (autoReconnectCount > 0) {
            delay(1_100)
        }
        val url = try {
            repo.streamUrl(activeItem, formatAttempt)
        } catch (e: Exception) {
            reconnecting = false
            error = Ar.errPlayback
            return@LaunchedEffect
        }
        val resume = when {
            activeItem.kind == Kind.LIVE || request.fromStart || forceFromZeroId == activeItem.id -> 0L
            autoReconnectCount > 0 && exo.currentPosition > 0L -> exo.currentPosition
            request.initialPositionMs > 0L && !usedInitialPos &&
                activeItem.id == request.items.getOrNull(request.startIndex)?.id -> {
                usedInitialPos = true
                request.initialPositionMs
            }
            else -> container.prefs.positionOf(activeItem.kind, activeItem.id)
        }
        if (forceFromZeroId == activeItem.id) forceFromZeroId = null
        exo.volume = 1.0f
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.setMediaItem(MediaItem.fromUri(url), resume.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(ended) {
        if (ended) {
            ended = false
            if (!isLive) {
                val total = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                if (total > 0L) container.saveProgress(currentItem, total, total)
                val nextInPlaylist = if (index < playlist.lastIndex) playlist[index + 1] else null
                if (nextInPlaylist != null) {
                    forceFromZeroId = nextInPlaylist.id
                    goTo(index + 1)
                } else {
                    val allEps = seriesDetails?.episodes.orEmpty()
                    val curEpIdx = allEps.indexOfFirst { it.id == currentItem.id }
                    val nextEp = if (curEpIdx >= 0 && curEpIdx + 1 < allEps.size) allEps[curEpIdx + 1] else null
                    if (nextEp != null) {
                        forceFromZeroId = nextEp.id
                        playEpisodeFromDrawer(nextEp)
                    } else {
                        // Last episode or movie ended: NEVER auto-repeat/loop same item
                        exo.playWhenReady = false
                        exo.pause()
                        controlsVisible = true
                        requestPlayFocusTick++
                    }
                }
            }
        }
    }

    LaunchedEffect(exo) {
        var ticks = 0
        while (true) {
            if (controlsVisible || showEpisodesDrawer || displayMode == DisplayMode.PHONE_PORTRAIT) {
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

    LaunchedEffect(controlsVisible, requestPlayFocusTick) {
        if (controlsVisible && (requestPlayFocusTick > 0 || !isPlaying) && displayMode != DisplayMode.PHONE_PORTRAIT) {
            delay(50)
            runCatching { playFocus.requestFocus() }
        } else if (!controlsVisible && !showEpisodesDrawer && !showQuickChannelsDrawer) {
            delay(60)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
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
                requestPlayFocusTick++
                true
            }
            Key.MediaPlay -> {
                exo.play()
                true
            }
            Key.MediaPause -> {
                exo.pause()
                controlsVisible = true
                requestPlayFocusTick++
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
                            requestPlayFocusTick++
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
                            if (!isLive) seekBy(SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        Key.DirectionLeft -> {
                            if (!isLive) seekBy(-SEEK_STEP_MS)
                            controlsVisible = true
                        }
                        else -> controlsVisible = true
                    }
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
                    isPlaying = isPlaying,
                    buffering = buffering,
                    reconnecting = reconnecting,
                    error = error,
                    position = position,
                    bufferedPosition = bufferedPosition,
                    duration = duration,
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
                        position = target
                        interactions++
                    },
                    onPrev = zapPrevious,
                    onNext = zapNext,
                    onSelectPlaylistIndex = goTo,
                    onSelectEpisode = playEpisodeFromDrawer,
                    onCycleSpeed = cycleSpeed,
                    onCycleResize = {
                        resizeIndex = (resizeIndex + 1) % ResizeModes.size
                        interactions++
                    },
                    onOpenTracks = { showTracks = true },
                    onOpenGuide = { showGuide = true },
                    onEnterLandscape = toggleOrientation,
                    onRetry = {
                        autoReconnectCount = 0
                        formatAttempt = 0
                        retryKey++
                    },
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
                        visible = controlsVisible && error == null,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        UnifiedFullscreenPlayerOsd(
                            item = item,
                            isLive = isLive,
                            playlistCount = if (isLive) playlist.size else (seriesDetails?.episodes?.size?.takeIf { it > 0 } ?: playlist.size),
                            currentProgram = currentProgram,
                            isPlaying = isPlaying,
                            position = position,
                            bufferedPosition = bufferedPosition,
                            duration = duration,
                            playbackSpeed = playbackSpeed,
                            showDrawer = if (isLive) showQuickChannelsDrawer else showEpisodesDrawer,
                            playFocus = playFocus,
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
                                position = position,
                                duration = duration,
                                compact = displayMode == DisplayMode.PHONE_LANDSCAPE,
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
// MODE A: PHONE PORTRAIT PLAYER LAYOUT (Vertical: 16:9 Video + Info + List)
// ============================================================================

@OptIn(UnstableApi::class)
@Composable
private fun PhonePortraitPlayerScreen(
    exo: ExoPlayer,
    item: PlayItem,
    playlist: List<PlayItem>,
    currentIndex: Int,
    isLive: Boolean,
    isPlaying: Boolean,
    buffering: Boolean,
    reconnecting: Boolean,
    error: String?,
    position: Long,
    bufferedPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    tracks: Tracks,
    textOff: Boolean,
    hasVideoOverride: Boolean,
    currentProgram: Program?,
    nextProgram: Program?,
    seriesDetails: SeriesDetails?,
    controlsVisible: Boolean,
    resizeMode: Int,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectPlaylistIndex: (Int) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenGuide: () -> Unit,
    onEnterLandscape: () -> Unit,
    onRetry: () -> Unit,
) {
    val videoBadge = remember(tracks, item.title) {
        SahabPlayer.videoBadge(tracks, qualityOf(item.title) ?: "FHD")
    }
    val audioLabel = remember(tracks) { SahabPlayer.activeAudioLabel(tracks) }
    val qualityLabel = remember(tracks, hasVideoOverride) {
        SahabPlayer.activeQualityButtonLabel(tracks, hasVideoOverride, isLive)
    }

    val allEpisodes = seriesDetails?.episodes.orEmpty()
    val seasons = remember(seriesDetails) { seriesDetails?.seasons.orEmpty() }
    val currentEpObj = remember(allEpisodes, item.id) { allEpisodes.firstOrNull { it.id == item.id } }
    var selectedSeason by remember(currentEpObj, seasons) {
        mutableIntStateOf(currentEpObj?.season ?: seasons.firstOrNull() ?: 1)
    }
    val seasonEpisodes = remember(allEpisodes, selectedSeason) {
        if (allEpisodes.isEmpty()) emptyList() else allEpisodes.filter { it.season == selectedSeason }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OsdSurfaceLowest),
    ) {
        // 1. Top 16:9 Video Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
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
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleControls() })
                    },
            )

            if ((buffering || reconnecting) && error == null) {
                CircularProgressIndicator(
                    color = OsdCyan,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Touch Overlay inside 16:9 Video
            if (controlsVisible && error == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    // Top Row inside video: Back + Live/Quality + Fullscreen Landscape Button
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Back,
                            description = Ar.back,
                            onClick = onBack,
                            size = 38.dp,
                            circle = true,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isLive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Palette.Live)
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                ) {
                                    Text(Ar.liveBadge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            FormatChip(videoBadge, OsdPrimary)
                            OsdIconSquareButton(
                                icon = Ic.Aspect,
                                description = Ar.fullscreen,
                                onClick = onEnterLandscape,
                                size = 38.dp,
                                circle = true,
                            )
                        }
                    }

                    // Center Transport Row (LTR for standard media order)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (playlist.size > 1) {
                                OsdIconSquareButton(Ic.Previous, Ar.previous, onPrev, size = 40.dp, circle = true)
                            }
                            OsdIconSquareButton(Ic.Previous, "-10", { onSeekBy(-SEEK_STEP_MS) }, size = 42.dp, circle = true, label = "-10")
                            OsdPrimaryPlayButton(
                                isPlaying = isPlaying,
                                onClick = onTogglePlay,
                                size = 56.dp,
                                cornerRadius = 28.dp,
                            )
                            OsdIconSquareButton(Ic.Next, "+10", { onSeekBy(SEEK_STEP_MS) }, size = 42.dp, circle = true, label = "+10")
                            if (playlist.size > 1) {
                                OsdIconSquareButton(Ic.Next, Ar.next, onNext, size = 40.dp, circle = true)
                            }
                        }
                    }

                    // Bottom Scrubber inside video
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val progressFraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0.85f
                        val bufferedFraction = if (duration > 0L) (bufferedPosition.toFloat() / duration).coerceIn(progressFraction, 1f) else 0.95f
                        GradientTimelineBar(
                            progress = progressFraction,
                            buffered = bufferedFraction,
                            showChapters = !isLive,
                            onSeekToFraction = if (!isLive && duration > 0L) {
                                { frac -> onSeekTo((duration * frac).toLong()) }
                            } else null,
                            onSeekBy = onSeekBy,
                        )
                        if (!isLive) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(formatDuration(position), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(formatDuration(duration), fontSize = 11.sp, color = OsdOnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    GlassButton(Ar.retry, onRetry, primary = true)
                }
            }
        }

        // 2. Channel / Title Metadata & Quick Action Toolbar (Below Video)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OsdSurfaceLow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (item.icon.isNotBlank()) {
                    RemoteImage(
                        url = item.icon,
                        fallbackText = item.title,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OsdSurfaceHighest)
                            .padding(4.dp),
                        contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = if (isLive) {
                        currentProgram?.title?.takeIf { it.isNotBlank() }
                            ?: nextProgram?.let { "${Ar.nextShowing}: ${it.title}" }
                            ?: "بث مباشر فائق الوضوح"
                    } else {
                        item.subtitle.ifBlank { "مشاهدة مباشرة عبر مشغل سحاب Media3" }
                    }
                    Text(
                        sub,
                        fontSize = 13.sp,
                        color = OsdPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Scrollable Action Pills Row (Never crowded!)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                item {
                    OsdPillButton(
                        label = Ar.fullscreen,
                        leadingIcon = Ic.Aspect,
                        iconTint = OsdOnPrimary,
                        highlighted = true,
                        onClick = onEnterLandscape,
                    )
                }
                item {
                    OsdPillButton(
                        label = audioLabel,
                        leadingIcon = Ic.Equalizer,
                        iconTint = OsdCyan,
                        onClick = onOpenTracks,
                    )
                }
                item {
                    OsdPillButton(
                        label = qualityLabel,
                        leadingIcon = Ic.Quality,
                        iconTint = OsdPrimary,
                        onClick = onOpenTracks,
                    )
                }
                if (isLive) {
                    item {
                        OsdPillButton(
                            label = Ar.guide,
                            leadingIcon = Ic.Clock,
                            iconTint = OsdTertiary,
                            onClick = onOpenGuide,
                        )
                    }
                } else {
                    item {
                        OsdPillButton(
                            label = String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", ".0x"),
                            leadingIcon = Ic.Speed,
                            iconTint = OsdCyan,
                            onClick = onCycleSpeed,
                        )
                    }
                }
                item {
                    OsdPillButton(
                        label = "أبعاد الشاشة",
                        leadingIcon = Ic.Aspect,
                        iconTint = OsdOnSurfaceVariant,
                        onClick = onCycleResize,
                    )
                }
            }
        }

        // 3. Vertical Channels or Episodes List (Takes remaining screen height without overlapping video)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "دليل القنوات السريع",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                    Text(
                        "${currentIndex + 1} / ${playlist.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(playlist, key = { idx, c -> "${c.id}_$idx" }) { idx, ch ->
                        val active = idx == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) OsdPrimaryContainer.copy(alpha = 0.38f)
                                    else OsdSurfaceContainer.copy(alpha = 0.85f),
                                )
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) OsdPrimary else OsdOutlineVariant.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable { onSelectPlaylistIndex(idx) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                String.format(Locale.US, "%02d", idx + 1),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) OsdCyan else OsdOnSurfaceVariant,
                            )
                            RemoteImage(
                                url = ch.icon,
                                fallbackText = ch.title,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OsdSurfaceHighest)
                                    .padding(3.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ch.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else OsdOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (active && currentProgram != null) currentProgram.title else "بث مباشر",
                                    fontSize = 12.sp,
                                    color = if (active) OsdCyan else OsdOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (active) {
                                Icon(Ic.Equalizer, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(20.dp))
                            } else {
                                val q = qualityOf(ch.title) ?: "HD"
                                FormatChip(q, if (q == "4K") OsdPrimary else OsdOnSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // Series Episodes or Movie Playlist in Portrait
                if (seasons.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(seasons, key = { it }) { sNum ->
                            val selected = sNum == selectedSeason
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) OsdPrimary else OsdSurfaceHigh)
                                    .clickable { selectedSeason = sNum }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    Ar.season(sNum),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) OsdOnPrimary else OsdOnSurface,
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (seasonEpisodes.isNotEmpty()) {
                        itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { _, ep ->
                            val isCurrent = ep.id == item.id
                            val isWatched = currentEpObj != null &&
                                (ep.season < currentEpObj.season ||
                                    (ep.season == currentEpObj.season && ep.number < currentEpObj.number))
                            val isNext = currentEpObj != null &&
                                ep.season == currentEpObj.season &&
                                ep.number == currentEpObj.number + 1
                            EpisodeDrawerCard(
                                number = ep.number,
                                title = ep.title,
                                plot = ep.plot.ifBlank { "حلقة كاملة من ${item.title}" },
                                imageUrl = ep.image.ifBlank { seriesDetails?.cover.orEmpty() }.ifBlank { item.icon },
                                durationText = ep.duration.ifBlank { "45:00" },
                                isCurrent = isCurrent,
                                isWatched = isWatched,
                                isNext = isNext,
                                progressFraction = if (isCurrent && duration > 0L) (position.toFloat() / duration).coerceIn(0.05f, 1f) else 0.45f,
                                remainingMinutes = if (isCurrent && duration > position) ((duration - position) / 60_000L).toInt().coerceAtLeast(1) else 25,
                                onClick = { onSelectEpisode(ep) },
                            )
                        }
                    } else {
                        itemsIndexed(playlist, key = { i, p -> "${p.id}_$i" }) { idx, p ->
                            EpisodeDrawerCard(
                                number = idx + 1,
                                title = p.subtitle.ifBlank { p.title },
                                plot = p.title,
                                imageUrl = p.icon,
                                durationText = if (idx == currentIndex && duration > 0L) formatDuration(duration) else "FHD",
                                isCurrent = idx == currentIndex,
                                isWatched = idx < currentIndex,
                                isNext = idx == currentIndex + 1,
                                progressFraction = if (idx == currentIndex && duration > 0L) (position.toFloat() / duration).coerceIn(0.05f, 1f) else 0.5f,
                                remainingMinutes = 25,
                                onClick = { onSelectPlaylistIndex(idx) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// MODE B & C: UNIFIED FULLSCREEN OSD (Live TV, Movies, Series — Phone & TV)
// ============================================================================

@Composable
private fun UnifiedFullscreenPlayerOsd(
    item: PlayItem,
    isLive: Boolean,
    playlistCount: Int,
    currentProgram: Program?,
    isPlaying: Boolean,
    position: Long,
    bufferedPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    showDrawer: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onToggleDrawer: () -> Unit,
) {
    val nowTime = formatNowMillis(rememberNow(), "hh:mm a", Locale.US)
    val subtitleText = if (isLive) {
        currentProgram?.title?.takeIf { it.isNotBlank() } ?: item.subtitle
    } else {
        item.subtitle
    }
    val hasDrawer = if (isLive) playlistCount > 1 else (item.kind == Kind.EPISODE || playlistCount > 1)
    val speedDesc = String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", "x").replace("50x", "5x")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        OsdSurfaceLowest.copy(alpha = 0.65f),
                        Color.Transparent,
                        OsdSurfaceLowest.copy(alpha = 0.78f),
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // 1. Unified Compact Top Bar: Back -> Title & Subtitle -> Clock
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OsdSurfaceLow.copy(alpha = 0.78f))
                .border(1.dp, OsdOutlineVariant.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                OsdIconSquareButton(
                    icon = Ic.Back,
                    description = Ar.back,
                    onClick = onBack,
                    size = 36.dp,
                    circle = true,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleText.isNotBlank()) {
                        Text(
                            subtitleText,
                            fontSize = 11.5.sp,
                            color = OsdOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                nowTime,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OsdOnSurface,
                maxLines = 1,
            )
        }

        // 2. Unified Compact Bottom Bar: Timeline + 1 Row of Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OsdSurfaceLow.copy(alpha = 0.85f))
                .border(1.dp, OsdOutlineVariant.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val progressFraction = if (isLive) {
                if (currentProgram != null && currentProgram.end > currentProgram.start) {
                    val now = System.currentTimeMillis() / 1000
                    ((now - currentProgram.start).toFloat() / (currentProgram.end - currentProgram.start)).coerceIn(0.08f, 0.96f)
                } else {
                    0.85f
                }
            } else if (duration > 0L) {
                (position.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            val bufferedFraction = if (duration > 0L) {
                (bufferedPosition.toFloat() / duration).coerceIn(progressFraction, 1f)
            } else {
                (progressFraction + 0.08f).coerceAtMost(1f)
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                GradientTimelineBar(
                    progress = progressFraction,
                    buffered = bufferedFraction,
                    showChapters = !isLive,
                    onSeekToFraction = if (!isLive && duration > 0L) {
                        { frac -> onSeekTo((duration * frac).toLong()) }
                    } else null,
                    onSeekBy = onSeekBy,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            formatDuration(position.coerceAtLeast(0L)),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (!isLive && duration > 0L) {
                            Text("/", fontSize = 11.5.sp, color = OsdOutline)
                            Text(
                                formatDuration(duration),
                                fontSize = 11.5.sp,
                                color = OsdOnSurfaceVariant,
                            )
                        }
                    }

                    if (isLive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(OsdCyan))
                            Text(
                                "مباشر",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OsdCyan,
                            )
                        }
                    } else {
                        val rem = (duration - position).coerceAtLeast(0L)
                        Text(
                            "-${formatDuration(rem)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = OsdTertiary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start (Physical Right in RTL): Channels / Episodes Drawer Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (hasDrawer) {
                        OsdPillButton(
                            label = if (isLive) "قائمة القنوات" else if (item.kind == Kind.EPISODE) "قائمة الحلقات" else "قائمة التشغيل",
                            leadingIcon = Ic.List,
                            iconTint = if (showDrawer) OsdOnPrimary else OsdPrimary,
                            highlighted = showDrawer,
                            onClick = onToggleDrawer,
                        )
                    }
                }

                // Center: Previous -> -10 -> Play/Pause -> +10 -> Next (LTR transport order)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Previous,
                            description = Ar.previous,
                            onClick = onPrev,
                            size = 38.dp,
                            circle = true,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Previous,
                            description = "-10",
                            label = "-10",
                            onClick = { onSeekBy(-SEEK_STEP_MS) },
                            size = 38.dp,
                            circle = true,
                        )
                        OsdPrimaryPlayButton(
                            isPlaying = isPlaying,
                            onClick = onTogglePlay,
                            size = 48.dp,
                            cornerRadius = 24.dp,
                            showOuterRing = true,
                            focusRequester = playFocus,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Next,
                            description = "+10",
                            label = "+10",
                            onClick = { onSeekBy(SEEK_STEP_MS) },
                            size = 38.dp,
                            circle = true,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Next,
                            description = Ar.next,
                            onClick = onNext,
                            size = 38.dp,
                            circle = true,
                        )
                    }
                }

                // End (Physical Left in RTL): Pure icon-only Audio, Subtitles, Speed, Aspect Ratio
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Equalizer,
                            description = Ar.audio,
                            onClick = onOpenTracks,
                            size = 38.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Subtitles,
                            description = Ar.subtitles,
                            onClick = onOpenTracks,
                            size = 38.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Speed,
                            description = "السرعة ($speedDesc)",
                            onClick = onCycleSpeed,
                            size = 38.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Aspect,
                            description = "أبعاد العرض",
                            onClick = onCycleResize,
                            size = 38.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChannelsDrawer(
    playlist: List<PlayItem>,
    currentIndex: Int,
    currentProgram: Program?,
    compact: Boolean = false,
    onSelectChannel: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (compact) 320.dp else 360.dp)
            .fillMaxHeight()
            .background(OsdSurfaceLowest.copy(alpha = 0.90f))
            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Ic.List, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(20.dp))
                Text(
                    "قائمة القنوات (${currentIndex + 1}/${playlist.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            OsdIconSquareButton(Ic.Close, Ar.close, onClose, size = 34.dp, circle = true)
        }

        val channelListState = rememberLazyListState()
        LaunchedEffect(currentIndex) {
            if (currentIndex > 0) {
                channelListState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = channelListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(playlist, key = { idx, c -> "${c.id}_$idx" }) { idx, ch ->
                val active = idx == currentIndex
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                active -> OsdPrimaryContainer.copy(alpha = 0.42f)
                                focused -> OsdSurfaceHighest.copy(alpha = 0.92f)
                                else -> OsdSurfaceHigh.copy(alpha = 0.70f)
                            },
                        )
                        .border(
                            width = if (active || focused) 1.5.dp else 1.dp,
                            color = if (active || focused) OsdCyan else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onSelectChannel(idx) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        String.format(Locale.US, "%02d", idx + 1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) OsdCyan else OsdOnSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OsdOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (active && currentProgram != null) {
                            Text(
                                currentProgram.title,
                                fontSize = 11.sp,
                                color = OsdCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (active) {
                        Icon(Ic.Equalizer, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(18.dp))
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
    compact: Boolean = false,
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

    val episodeListState = rememberLazyListState()
    LaunchedEffect(selectedSeason, currentItem.id, currentIndex) {
        val targetIndex = if (seasonEpisodes.isNotEmpty()) {
            seasonEpisodes.indexOfFirst { it.id == currentItem.id }.takeIf { it >= 0 } ?: 0
        } else {
            currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        }
        if (targetIndex > 0) {
            episodeListState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .width(if (compact) 320.dp else 380.dp)
            .widthIn(max = 400.dp)
            .fillMaxHeight()
            .background(OsdSurfaceLowest.copy(alpha = 0.90f))
            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .padding(if (compact) 14.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Series, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        if (currentItem.kind == Kind.EPISODE) "حلقات المسلسل" else "قائمة التشغيل",
                        fontSize = if (compact) 16.sp else 18.sp,
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
                            .padding(horizontal = 16.dp, vertical = 7.dp),
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

        LazyColumn(
            state = episodeListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            Box(
                modifier = Modifier
                    .width(114.dp)
                    .height(70.dp)
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
                    Icon(overlayIcon, contentDescription = null, tint = overlayTint, modifier = Modifier.size(24.dp))
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when {
                    isCurrent -> Text(
                        "الحلقة الحالية قيد المشاهدة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                    isWatched -> Text(
                        "تمت المشاهدة بالكامل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                    isNext -> Text(
                        "الحلقة التالية",
                        fontSize = 11.sp,
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
                    fontSize = 12.sp,
                    color = if (isCurrent) OsdTertiary else OsdOutline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isCurrent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("متبقي $remainingMinutes دقيقة", fontSize = 11.sp, color = OsdPrimary)
                        Text("4K UHD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OsdPrimary)
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
                            .pointerInput(onSeekToFraction) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) {
                                        onSeekToFraction((offset.x / size.width).coerceIn(0f, 1f))
                                    }
                                }
                            }
                            .pointerInput(onSeekToFraction) {
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
            val trackHeight = 7.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = OsdSurfaceHighest.copy(alpha = 0.85f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )

            drawRoundRect(
                color = OsdOutlineVariant.copy(alpha = 0.65f),
                topLeft = Offset(0f, top),
                size = Size(size.width * bufShown, trackHeight),
                cornerRadius = radius,
            )

            val activeWidth = (size.width * shown).coerceAtLeast(trackHeight)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(OsdPrimaryContainer, OsdPrimary, OsdCyan),
                ),
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeight),
                cornerRadius = radius,
            )

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
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(size)
            .shadow(
                elevation = if (focused) 24.dp else 14.dp,
                shape = shape,
                ambientColor = if (focused) OsdCyan else OsdPrimaryContainer,
                spotColor = if (focused) OsdCyan else OsdPrimary,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (focused) listOf(OsdCyan, OsdPrimary)
                    else listOf(OsdPrimaryContainer, OsdPrimary),
                ),
            )
            .border(
                width = if (focused) 2.5.dp else if (showOuterRing) 2.dp else 1.dp,
                color = if (focused) OsdCyan else OsdPrimary.copy(alpha = 0.65f),
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
            .height(38.dp)
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
            Icon(leadingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(16.dp))
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
            Text(Ar.tracks, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OsdOnSurface)
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
            fontSize = 16.sp,
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
