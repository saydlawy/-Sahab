package com.saydlawy.sahab.ui.live

import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalAppScale
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatClock
import com.saydlawy.sahab.util.formatNowMillis
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.Immutable

@OptIn(UnstableApi::class)
@Composable
internal fun PreviewPanel(
    channel: Channel?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGuide: (Channel) -> Unit,
    showEpg: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val repo = container.repo
    val context = LocalContext.current

    var previewExo by remember { mutableStateOf<ExoPlayer?>(null) }
    var isScreenActive by remember { mutableStateOf(true) }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var videoReady by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    var formatAttempt by remember { mutableIntStateOf(0) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var activeLiveExt by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            SahabPlayer.releasePlayer(previewExo)
            previewExo = null
        }
    }

    DisposableEffect(previewExo) {
        val exo = previewExo
        if (exo == null) {
            isPlaying = false
            buffering = false
            videoReady = false
            tracks = Tracks.EMPTY
            return@DisposableEffect onDispose { }
        }
        exo.volume = if (muted) 0f else 1f
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onRenderedFirstFrame() {
                videoReady = true
                previewError = null
                activeLiveExt?.let { repo.reportWorkingLiveFormat(it) }
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                if (formatAttempt + 1 < repo.liveExtensions().size) {
                    formatAttempt += 1
                } else {
                    buffering = false
                    videoReady = false
                    previewError = "تعذّر تشغيل معاينة البث المباشر (${error.errorCodeName})"
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        isScreenActive = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        isScreenActive = false
        SahabPlayer.releasePlayer(previewExo)
        previewExo = null
    }

    LaunchedEffect(channel?.id) {
        formatAttempt = 0
        videoReady = false
        previewError = null
        tracks = Tracks.EMPTY
    }

    LaunchedEffect(channel?.id, formatAttempt, isScreenActive, retryNonce) {
        val ch = channel
        if (ch == null || !isScreenActive) {
            previewExo?.stop()
            previewExo?.clearMediaItems()
            if (ch == null) {
                SahabPlayer.releasePlayer(previewExo)
                previewExo = null
            }
            videoReady = false
            previewError = null
            return@LaunchedEffect
        }
        previewError = null
        previewExo?.stop()
        previewExo?.clearMediaItems()
        if (formatAttempt == 0 && retryNonce == 0) {
            delay(280L)
        }
        val exts = repo.liveExtensions()
        activeLiveExt = exts[formatAttempt.coerceIn(0, exts.lastIndex)]
        val url = runCatching {
            repo.streamUrl(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon, url = ch.url), formatAttempt)
        }.getOrNull()
        if (url.isNullOrBlank()) {
            buffering = false
            videoReady = false
            previewError = "تعذّر تجهيز رابط البث للمعاينة"
            return@LaunchedEffect
        }

        val exo = previewExo ?: SahabPlayer.create(
            context = context,
            client = container.client,
            isLive = true,
            isPreview = true,
            accountHash = container.accountHash,
        ).also { created ->
            previewExo = created
            SahabPlayer.bindOnRelease(created) {
                if (previewExo === created) {
                    previewExo = null
                }
            }
        }

        exo.volume = if (muted) 0f else 1f
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    // LIVE-01: recover the preview from silent hardware-decoder stalls as well.
    com.saydlawy.sahab.ui.player.LiveVideoStallWatchdog(
        player = previewExo,
        enabled = isScreenActive && channel != null,
        itemKey = channel?.id,
        onEscalate = {
            if (formatAttempt + 1 < repo.liveExtensions().size) {
                formatAttempt += 1
                true
            } else {
                false
            }
        },
    )

    val openFullScreen: () -> Unit = {
        // Fully release preview player before opening full-screen player (prevents dual MediaCodec/RAM usage)
        SahabPlayer.releasePlayer(previewExo)
        previewExo = null
        onPlay()
    }

    val programs by produceState<List<Program>>(emptyList(), channel?.id) {
        value = if (channel != null && channel.epgId.isNotBlank()) {
            try { repo.shortEpg(channel.id, 6) } catch (_: Exception) { emptyList() }
        } else emptyList()
    }
    val today = formatNowMillis(rememberNow(), "EEEE، d MMMM", Locale.forLanguageTag("ar"))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(appDp(14)),
    ) {
        // Card 1: Live Video Preview & Control Toolbar
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(appDp(24)),
            padding = appDp(12),
        ) {
            var previewFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(appDp(16)))
                    .background(Color.Black)
                    .onFocusChanged { previewFocused = it.isFocused }
                    .border(
                        width = if (previewFocused) appDp(2.5f) else appDp(1),
                        color = if (previewFocused) Palette.FocusCyan else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(appDp(16)),
                    )
                    .clickable(enabled = channel != null, onClick = openFullScreen),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            player = previewExo
                        }
                    },
                    update = { view ->
                        if (view.player !== previewExo) {
                            view.player = previewExo
                        }
                    },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!videoReady) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(PreviewPlaceholderBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (channel != null) {
                            val primaryPreviewLogo = remember(channel.icon, channel.name) {
                                ChannelLogoResolver.resolvePrimaryUrl(channel.icon, channel.name)
                            }
                            val fallbackPreviewLogo = remember(channel.name) {
                                ChannelLogoResolver.resolveInternetLogoUrl(channel.name)
                            }
                            val density = LocalDensity.current
                            val logoSize = appDp(64)
                            val logoPx = remember(density, logoSize) { with(density) { logoSize.roundToPx() } }
                            Box(
                                modifier = Modifier
                                    .size(logoSize)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(appDp(2), Palette.FocusCyan, CircleShape)
                                    .padding(appDp(6)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RemoteImage(
                                    url = primaryPreviewLogo,
                                    fallbackUrl = fallbackPreviewLogo,
                                    fallbackText = channel.name,
                                    targetWidthPx = logoPx,
                                    targetHeightPx = logoPx,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PreviewShadeBrush),
                )

                if (channel == null) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(appDp(14)))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(appDp(1), Palette.Glow.copy(alpha = 0.35f), RoundedCornerShape(appDp(14)))
                            .padding(horizontal = appDp(16), vertical = appDp(10)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(appDp(6)),
                    ) {
                        Icon(Ic.Play, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(appDp(26)))
                        Text(
                            "اختر قناة واضغط موافق أو المسها للمعاينة",
                            fontSize = appSp(12),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else if (previewError != null) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = appDp(14))
                            .clip(RoundedCornerShape(appDp(14)))
                            .background(Color.Black.copy(alpha = 0.80f))
                            .border(appDp(1), Palette.Danger.copy(alpha = 0.55f), RoundedCornerShape(appDp(14)))
                            .padding(horizontal = appDp(16), vertical = appDp(12)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(appDp(8)),
                    ) {
                        Text(
                            previewError ?: "تعذّر تشغيل المعاينة",
                            fontSize = appSp(12),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        GlassButton(
                            text = Ar.retry,
                            onClick = {
                                previewError = null
                                formatAttempt = 0
                                retryNonce += 1
                            },
                            primary = true,
                        )
                    }
                } else if (buffering && !videoReady) {
                    Box(Modifier.align(Alignment.Center)) {
                        CircularProgressIndicator(
                            color = Palette.Glow,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(appDp(32)),
                            strokeWidth = appDp(3),
                        )
                    }
                }

                // Top-right badge: "مباشر - 1080p 60fps"
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(appDp(10))
                        .clip(RoundedCornerShape(appDp(8)))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(appDp(1), Color.White.copy(alpha = 0.12f), RoundedCornerShape(appDp(8)))
                        .padding(horizontal = appDp(10), vertical = appDp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(appDp(6)),
                ) {
                    Box(Modifier.size(appDp(7)).clip(CircleShape).background(Palette.Live))
                    val vBadge = SahabPlayer.videoBadge(tracks, channel?.let { qualityOf(it.name) })
                    Text("${Ar.liveBadge} - $vBadge", fontSize = appSp(11), fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Bottom-left badge: Audio stream info or channel name
                if (channel != null) {
                    val aBadge = SahabPlayer.audioBadge(tracks) ?: channel.name
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(appDp(10))
                            .clip(RoundedCornerShape(appDp(8)))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(appDp(1), Color.White.copy(alpha = 0.12f), RoundedCornerShape(appDp(8)))
                            .padding(horizontal = appDp(10), vertical = appDp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(appDp(6)),
                    ) {
                        Text(
                            aBadge,
                            fontSize = appSp(11),
                            fontWeight = FontWeight.Bold,
                            color = Palette.Glow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (channel != null) {
                Spacer(Modifier.height(appDp(10)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(appDp(12), Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerControlButton(
                        icon = if (isPlaying) Ic.Pause else Ic.Play,
                        description = if (isPlaying) Ar.pause else Ar.play,
                        onClick = {
                            previewExo?.let { exo ->
                                if (exo.isPlaying) exo.pause() else exo.play()
                            }
                        },
                    )
                    PlayerControlButton(
                        icon = if (muted) Ic.VolumeOff else Ic.VolumeUp,
                        description = if (muted) Ar.unmute else Ar.mute,
                        onClick = {
                            muted = !muted
                            previewExo?.volume = if (muted) 0f else 1f
                        },
                    )
                    PlayerControlButton(
                        icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                        description = Ar.favorites,
                        onClick = onToggleFavorite,
                        isRed = true,
                    )
                    PlayerControlButton(icon = Ic.Search, description = Ar.guide, onClick = { onGuide(channel) })
                    PlayerControlButton(icon = Ic.Aspect, description = Ar.fullscreen, onClick = openFullScreen)
                }
            }
        }

        // Card 2: EPG Schedule (shown on Tablet/TV where vertical space allows both video and schedule)
        if (showEpg) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(appDp(24)),
                padding = appDp(16),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotTitle(Ar.epgTitle, Palette.Gold, fontSize = appSp(14))
                    Text(today, fontSize = appSp(11), color = Palette.TextDim)
                }

                Spacer(Modifier.height(appDp(10)))

                if (channel == null) {
                    Text(Ar.pickChannel, fontSize = appSp(13), color = Palette.TextDim)
                } else {
                    val now = System.currentTimeMillis() / 1000
                    val past = programs.lastOrNull { it.end <= now }
                    val current = programs.firstOrNull { now in it.start until it.end }
                    val upcoming = programs.filter { it.start > (current?.start ?: now) }.take(2)

                    if (past == null && current == null && upcoming.isEmpty()) {
                        Text(Ar.noEpgShort, fontSize = appSp(13), color = Palette.TextDim)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(appDp(8))) {
                            past?.let { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.ENDED,
                                    onClick = { onGuide(channel) },
                                )
                            }
                            current?.let { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.NOW,
                                    onClick = { onGuide(channel) },
                                )
                            }
                            upcoming.forEach { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.SOON,
                                    onClick = { onGuide(channel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class EpgRowState { ENDED, NOW, SOON }

@Composable
internal fun EpgScheduleRow(
    time: String,
    title: String,
    state: EpgRowState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(appDp(12))
    val isNow = state == EpgRowState.NOW
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg: Brush = if (focused || isNow) Palette.ActiveFill else Brush.verticalGradient(listOf(Palette.Glass, Palette.Glass))
    val borderColor = when {
        focused -> Palette.FocusCyan
        isNow -> Palette.Glow
        else -> Palette.GlassBorder
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (focused) appDp(2) else appDp(1), borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = appDp(12), vertical = appDp(9)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appDp(10)),
        ) {
            Text(
                time,
                fontSize = appSp(11),
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                color = if (isNow) Palette.Gold else Palette.TextDim,
                maxLines = 1,
            )
            Text(
                title,
                fontSize = appSp(13),
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                color = if (state == EpgRowState.ENDED) Palette.TextDim else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(appDp(8)))
        when (state) {
            EpgRowState.ENDED -> Tag(Ar.ended, Palette.TextDim, fontSize = appSp(10))
            EpgRowState.NOW -> Tag(Ar.onAirNow, Palette.Live, filled = true, fontSize = appSp(10))
            EpgRowState.SOON -> Tag(Ar.soon, Palette.Glow, fontSize = appSp(10))
        }
    }
}

@Composable
internal fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    isRed: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isRed -> Palette.Live.copy(alpha = if (focused) 0.55f else 0.30f)
        focused -> Palette.Royal.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val borderColor = when {
        focused -> Palette.Glow
        isRed -> Palette.Live.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .size(appDp(40))
            .clip(CircleShape)
            .background(bg)
            .border(if (focused) appDp(2) else appDp(1), borderColor, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (isRed) Palette.Live else Color.White,
            modifier = Modifier.size(appDp(18)),
        )
    }
}

