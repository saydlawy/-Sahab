package com.saydlawy.sahab.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.findActivity
import com.saydlawy.sahab.util.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.Locale

private val ResizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_HIDE_MS = 4_500L

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
private fun createPlayer(context: Context, client: OkHttpClient): ExoPlayer {
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    val extractors = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )
    val http = OkHttpDataSource.Factory(client).setUserAgent("Sahab/1.0 (Android)")
    val sources = DefaultMediaSourceFactory(context, extractors).setDataSourceFactory(http)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
        .build()
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(sources)
        .setLoadControl(loadControl)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(container: AppContainer, request: PlayRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val isTv = LocalIsTv.current
    val repo = container.repo
    val lastIndex = request.items.lastIndex

    var index by remember { mutableIntStateOf(request.startIndex.coerceIn(0, lastIndex)) }
    val item = request.items[index]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.kind == Kind.LIVE

    val exo = remember { createPlayer(context, container.client) }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var formatAttempt by remember { mutableIntStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    val rootFocus = remember { FocusRequester() }

    val saveNow: () -> Unit = {
        val saved = currentItem
        val total = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        container.saveProgress(saved, exo.currentPosition, total)
    }

    val goTo: (Int) -> Unit = { target ->
        if (target in 0..lastIndex && target != index) {
            saveNow()
            formatAttempt = 0
            error = null
            ended = false
            index = target
            controlsVisible = true
            interactions++
        }
    }
    val zapNext: () -> Unit = { goTo(if (index >= lastIndex) 0 else index + 1) }
    val zapPrevious: () -> Unit = { goTo(if (index <= 0) lastIndex else index - 1) }
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
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onPlayerError(e: PlaybackException) {
                val live = currentItem.kind == Kind.LIVE
                if (live && formatAttempt + 1 < repo.liveExtensions().size) {
                    formatAttempt += 1
                } else {
                    error = Ar.errPlayback
                }
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Load whatever is selected: a new item, a retry, or the fallback stream format.
    LaunchedEffect(index, retryKey, formatAttempt) {
        error = null
        buffering = true
        val url = try {
            repo.streamUrl(item, formatAttempt)
        } catch (e: Exception) {
            error = Ar.errPlayback
            return@LaunchedEffect
        }
        val resume = if (isLive || request.fromStart) 0L else container.prefs.positionOf(item.kind, item.id)
        exo.setMediaItem(MediaItem.fromUri(url), resume.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(ended) {
        if (ended) {
            ended = false
            if (!isLive && index < lastIndex) goTo(index + 1)
        }
    }

    // Position clock; also saves progress now and then.
    LaunchedEffect(exo) {
        var ticks = 0
        while (true) {
            if (controlsVisible) {
                position = exo.currentPosition
                duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
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

    LaunchedEffect(controlsVisible, interactions, isPlaying, showTracks, showGuide) {
        if (controlsVisible && isPlaying && !showTracks && !showGuide) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    // Keys need a focus owner: the root when the controls are hidden.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
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
            Key.Back, Key.Escape -> false
            else -> {
                if (controlsVisible) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> if (isLive) zapNext() else Unit
                        Key.DirectionDown -> if (isLive) zapPrevious() else Unit
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

    val now = System.currentTimeMillis() / 1000
    val subtitle = if (isLive) {
        val current = epg.firstOrNull { now in it.start until it.end }
        val upcoming = epg.firstOrNull { it.start >= (current?.end ?: now) }
        listOfNotNull(
            current?.let { "${Ar.nowShowing}: ${it.title}" },
            upcoming?.let { "${Ar.nextShowing}: ${it.title}" },
        ).joinToString("      ")
    } else {
        item.subtitle
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        controlsVisible = !controlsVisible
                        interactions++
                    })
                },
        )

        if (buffering && error == null) {
            Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = Palette.Pink, trackColor = Color.White.copy(alpha = 0.15f))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                title = item.title,
                subtitle = subtitle,
                isLive = isLive,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                hasPrev = request.items.size > 1,
                hasNext = request.items.size > 1,
                resizeLabel = Ar.resizeLabel(resizeIndex % ResizeModes.size),
                hasTracks = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT },
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
                onResize = {
                    resizeIndex = (resizeIndex + 1) % ResizeModes.size
                    interactions++
                },
                onTracks = {
                    showTracks = true
                    interactions++
                },
                onGuide = {
                    showGuide = true
                    interactions++
                },
                onInteract = { interactions++ },
            )
        }

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(Ar.retry, { retryKey++ }, primary = true, autoFocus = true)
                    GlassButton(Ar.back, onBack)
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

@Composable
private fun PlayerControls(
    title: String,
    subtitle: String,
    isLive: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    hasPrev: Boolean,
    hasNext: Boolean,
    resizeLabel: String,
    hasTracks: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResize: () -> Unit,
    onTracks: () -> Unit,
    onGuide: () -> Unit,
    onInteract: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(Ic.Back, Ar.back, onBack, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Palette.Danger),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(Ar.liveBadge, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Danger)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 15.sp,
                        color = Palette.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isLive) GlassIconButton(Ic.List, Ar.guide, onGuide, size = 52.dp)
            if (hasTracks) GlassIconButton(Ic.Tracks, Ar.tracks, onTracks, size = 52.dp)
            GlassIconButton(Ic.Aspect, resizeLabel, onResize, size = 52.dp)
        }

        // Media controls keep the usual left-to-right order whatever the language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(position), fontSize = 15.sp, color = Palette.Text)
                        SeekBar(
                            position = position,
                            duration = duration,
                            onSeekTo = onSeekTo,
                            onSeekBy = onSeekBy,
                            onInteract = onInteract,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                        )
                        Text(formatDuration(duration), fontSize = 15.sp, color = Palette.Text)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasPrev) GlassIconButton(Ic.Previous, Ar.previous, onPrev, size = 56.dp)
                    if (!isLive) SeekButton("-10") { onSeekBy(-10_000L) }
                    GlassIconButton(
                        icon = if (isPlaying) Ic.Pause else Ic.Play,
                        description = if (isPlaying) Ar.pause else Ar.play,
                        onClick = onTogglePlay,
                        size = 72.dp,
                        autoFocus = true,
                    )
                    if (!isLive) SeekButton("+10") { onSeekBy(10_000L) }
                    if (hasNext) GlassIconButton(Ic.Next, Ar.next, onNext, size = 56.dp)
                }
            }
        }
    }
}

@Composable
private fun SeekButton(label: String, onClick: () -> Unit) {
    GlassCard(onClick = onClick, modifier = Modifier.size(56.dp), shape = CircleShape) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = dragFraction ?: fraction

    Canvas(
        modifier = modifier
            .height(36.dp)
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
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0 && size.width > 0) {
                        onSeekTo((duration * (offset.x / size.width)).toLong())
                    }
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        dragFraction?.let { if (duration > 0) onSeekTo((duration * it).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    if (size.width > 0) dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onInteract()
                }
            },
    ) {
        val trackHeight = 8.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, trackHeight), radius)
        drawRoundRect(Palette.Accent, Offset(0f, top), Size(size.width * shown, trackHeight), radius)
        if (focused || dragFraction != null) {
            drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(size.width * shown, size.height / 2f))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TracksDialog(tracks: Tracks, exo: ExoPlayer, onDismiss: () -> Unit) {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.DialogBg)
                .padding(20.dp),
        ) {
            Text(Ar.tracks, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                if (audio.isNotEmpty()) {
                    item { Text(Ar.audio, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan) }
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
                    item { Text(Ar.subtitles, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Cyan) }
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
            color = Palette.Text,
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
